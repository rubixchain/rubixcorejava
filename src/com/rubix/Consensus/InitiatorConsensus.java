package com.rubix.Consensus;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.forward;
import static com.rubix.Resources.IPFSNetwork.swarmConnect;


public class InitiatorConsensus {

    public static Logger InitiatorConsensusLogger = Logger.getLogger(InitiatorConsensus.class);


    public static volatile JSONObject quorumSignature = new JSONObject();
    private static final Object countLock = new Object();
    private static final Object signLock = new Object();
    public static ArrayList<String> quorumWithShares = new ArrayList<>();
    public static volatile int[] quorumResponse = {0,0,0};

    /**
     * This method increments the quorumResponse variable
     */
    private static synchronized boolean voteNCount(int i,int quorumSize) {
        boolean status;
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        synchronized (countLock) {
            if(quorumResponse[i]<minQuorum(quorumSize)) {
                quorumResponse[i]++;
                InitiatorConsensusLogger.debug("quorum response added index "+i+ "  is "+quorumResponse[i]+" quorumsize " + minQuorum(quorumSize));
                status=true;
            }
            else
            {
                status=false;
                InitiatorConsensusLogger.debug("Consensus Reached for index "+ i);
            }
        }
        return status;
    }


    /**
     * This method stores all the quorum signatures until required count for consensus
     * @param quorumDID DID of the Quorum
     * @param quorumSignResponse Signature of the Quorum
     */
    private static synchronized void quorumSign(String quorumDID, String quorumSignResponse,int index,int quorumSize,int alphaSize) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        synchronized (signLock) {
            try {
                if(quorumSignature.length()<(minQuorum(alphaSize)+2*minQuorum(7))&& quorumResponse[index]<=minQuorum(quorumSize)) {
                    quorumSignature.put(quorumDID, quorumSignResponse);
                    InitiatorConsensusLogger.debug("quorum sign added current length " + quorumSignature.length());
                }
                else
                {
                    InitiatorConsensusLogger.debug("quorum already reached consensus "+quorumSignature.length());
                }
            } catch (JSONException e) {
                InitiatorConsensusLogger.error("JSON Exception Occurred", e);
                e.printStackTrace();
            }
        }
    }


    /**
     * This method runs the consensus
     * 1. Contact quorum with sender signatures and details
     * 2. Verify quorum signatures
     * 3. If consensus reached , sends shares to Quorum
     * @param ipfs IPFS instance
     * @param PORT Port for forwarding to Quorum
     */
    public static JSONObject start(String data,  IPFS ipfs, int PORT, int index,String role,JSONArray quorumPeersObject,int alphaSize,int quorumSize) throws JSONException {
        String[] qResponse = new String[QUORUM_COUNT];
//        int quorumSize = quorumPeersObject.length();
        Socket[] qSocket = new Socket[QUORUM_COUNT];
        PrintStream[] qOut = new PrintStream[QUORUM_COUNT];
        BufferedReader[] qIn = new BufferedReader[QUORUM_COUNT];
        String[] quorumID = new String[QUORUM_COUNT];
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        JSONObject dataObject = new JSONObject(data);
        String hash = dataObject.getString("hash");
        JSONArray details = dataObject.getJSONArray("details");
        quorumResponse[index] = 0;
        InitiatorConsensusLogger.debug("quorum peer role "+role+" length "+quorumPeersObject.length());
        JSONArray tokenDetails;
        try {
            tokenDetails = new JSONArray(details.toString());
            JSONObject detailsToken = tokenDetails.getJSONObject(0);
            JSONObject sharesToken = tokenDetails.getJSONObject(1);

            String[] shares = new String[minQuorum(7) - 1];
            for(int i = 0; i < shares.length; i++){
                int p = i + 1;
                shares[i] = sharesToken.getString("Share" + p);
            }

            for (int j = 0; j < quorumPeersObject.length(); j++)
                quorumID[j] = quorumPeersObject.getString(j);

            Thread[] quorumThreads = new Thread[quorumPeersObject.length()];
            for (int i = 0; i < quorumPeersObject.length(); i++) {
                int j = i;
                quorumThreads[i] = new Thread(() -> {

                    try {
                        swarmConnect(quorumID[j],ipfs);
                        String quorumDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", quorumID[j]);
                        String quorumWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid", quorumID[j]);
                        nodeData(quorumDidIpfsHash, quorumWidIpfsHash, ipfs);
                        String appName = quorumID[j].concat(role);
                        InitiatorConsensusLogger.debug("quourm ID "+quorumID[j]+ " appname "+appName);
                        forward(appName, PORT+j, quorumID[j]);
                        InitiatorConsensusLogger.debug("Connected to " + quorumID[j] + "on port "+(PORT+j)+ "with AppName" + appName);
                        qSocket[j] = new Socket("127.0.0.1", PORT+j);
                        qIn[j] = new BufferedReader(new InputStreamReader(qSocket[j].getInputStream()));
                        qOut[j] = new PrintStream(qSocket[j].getOutputStream());
                        qOut[j].println(detailsToken);
                        qResponse[j] = qIn[j].readLine();
                        if (qResponse[j].equals("Auth_Failed")) {
                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                        }
                        else {
                            InitiatorConsensusLogger.debug("Signature Received from " + quorumID[j] + " " + qResponse[j]);
                            if (quorumResponse[index] > minQuorum(quorumSize)) {
                                qOut[j].println("null");
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                            } else {
                                String didHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", quorumID[j]);
                                JSONObject detailsToVerify = new JSONObject();
                                detailsToVerify.put("did", didHash);
                                detailsToVerify.put("hash", hash);
                                detailsToVerify.put("signature", qResponse[j]);
                                if (Authenticate.verifySignature(detailsToVerify.toString())) {
                                    InitiatorConsensusLogger.debug(role + " node authenticated at index " + index);
                                    boolean voteStatus = voteNCount(index,quorumSize);
                                    if (quorumResponse[index] <= minQuorum(quorumSize) && voteStatus) {
                                        InitiatorConsensusLogger.debug("waiting for  " +quorumSize +" +signs " + role);
                                        while (quorumResponse[index] < minQuorum(quorumSize)) {}
                                        InitiatorConsensusLogger.debug("between Q1- to Q"+quorumSize+" for index " + index);
                                        quorumSign(didHash, qResponse[j], index,quorumSize,alphaSize);
                                        quorumWithShares.add(quorumPeersObject.getString(j));
                                        while (quorumSignature.length() < (minQuorum(alphaSize) + 2* minQuorum(7))) {}
                                        InitiatorConsensusLogger.debug("sending Qsign  of length " + quorumSignature.length() + "at index " + index);
                                        qOut[j].println(quorumSignature);
                                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                    }
                                    else {
                                        InitiatorConsensusLogger.debug("sending null for slow quorum ");
                                        qOut[j].println("null");
                                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                    }
                                    InitiatorConsensusLogger.debug("Quorum Count : " + quorumResponse + "Signature count : " + quorumSignature.length());
                                } else {
                                    InitiatorConsensusLogger.debug("node failed authentication with index " + index + " with role " + role + " with did " + didHash + " and data to verify " + detailsToVerify);
                                    InitiatorConsensusLogger.debug("recheck result is " + Authenticate.verifySignature(detailsToVerify.toString()));
                                }
                            }
                        }
                    } catch (IOException | JSONException e) {
                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/"+ quorumID[j]);
                        InitiatorConsensusLogger.error("IOException Occurred", e);
                        e.printStackTrace();
                    }
                });
                quorumThreads[j].start();
            }

            while(quorumResponse[index] < minQuorum(quorumSize)  || quorumSignature.length() < (minQuorum(alphaSize) + 2* minQuorum(7))){}

        } catch (JSONException e) {
            InitiatorConsensusLogger.error("JSON Exception Occurred", e);
            e.printStackTrace();
        }
        return quorumSignature;
    }
}
