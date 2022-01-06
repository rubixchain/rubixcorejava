package com.rubix.Mining;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.ArrayList;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;


public class MineInitiator {

    public static Logger MineInitiatorLogger = Logger.getLogger(MineInitiator.class);


    public static volatile JSONObject quorumSignature = new JSONObject();
    private static final Object countLock = new Object();
    private static final Object signLock = new Object();
    public static ArrayList<String> quorumWithShares = new ArrayList<>();
    public static volatile int[] quorumResponse = {0, 0, 0};
    public static volatile JSONArray finalQuorumSignsArray = new JSONArray();

    /**
     * This method increments the quorumResponse variable
     */
    private static synchronized boolean voteNCount(int i, int quorumSize) {
        boolean status;
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        synchronized (countLock) {
            if (quorumResponse[i] < minQuorum(quorumSize)) {
                quorumResponse[i]++;
                MineInitiatorLogger.debug("quorum response added index " + i + "  is " + quorumResponse[i] + " quorumsize " + minQuorum(quorumSize));
                status = true;
            } else {
                status = false;
                MineInitiatorLogger.debug("Consensus Reached for index " + i);
            }
        }
        return status;
    }


    /**
     * This method stores all the quorum signatures until required count for consensus
     *
     * @param quorumDID          DID of the Quorum
     * @param quorumSignResponse Signature of the Quorum
     */
    private static synchronized void quorumSign(String quorumDID, String hash, String quorumSignResponse, int index, int quorumSize, int alphaSize) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        synchronized (signLock) {
            try {
                if (quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7)) && quorumResponse[index] <= minQuorum(quorumSize)) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("did", quorumDID);
                    jsonObject.put("signature", quorumSignResponse);
                    jsonObject.put("hash", hash);
                    finalQuorumSignsArray.put(jsonObject);
                    quorumSignature.put(quorumDID, quorumSignResponse);
                } else {
                    MineInitiatorLogger.debug("quorum already reached consensus " + quorumSignature.length());
                }
            } catch (JSONException e) {
                MineInitiatorLogger.error("JSON Exception Occurred", e);
                e.printStackTrace();
            }
        }
    }


    /**
     * This method runs the consensus
     * 1. Contact quorum with sender signatures and details
     * 2. Verify quorum signatures
     * 3. If consensus reached , sends shares to Quorum
     *
     * @param ipfs IPFS instance
     * @param PORT Port for forwarding to Quorum
     */
    public static JSONObject startMine(String data, IPFS ipfs, int PORT, int index, String role, JSONArray quorumPeersObject, int alphaSize, int quorumSize) throws JSONException {
        String[] qResponse = new String[QUORUM_COUNT];
        String[] qVerification = new String[QUORUM_COUNT];
        Socket[] qSocket = new Socket[QUORUM_COUNT];
        PrintStream[] qOut = new PrintStream[QUORUM_COUNT];
        BufferedReader[] qIn = new BufferedReader[QUORUM_COUNT];
        String[] quorumID = new String[QUORUM_COUNT];
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        JSONObject dataObject = new JSONObject(data);
        String hash = dataObject.getString("hash");
        JSONArray details = dataObject.getJSONArray("details");
        JSONObject qstDetails = dataObject.getJSONObject("qstDetails");
        quorumResponse[index] = 0;
        MineInitiatorLogger.debug("quorum peer role "+role+" length "+quorumPeersObject.length());
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
                        swarmConnectP2P(quorumID[j],ipfs);
                        syncDataTable(null, quorumID[j]);
                        String quorumDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", quorumID[j]);
                        String quorumWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid", quorumID[j]);
                        nodeData(quorumDidIpfsHash, quorumWidIpfsHash, ipfs);
                        String appName = quorumID[j].concat(role).concat("mining");
                        MineInitiatorLogger.debug("quourm ID "+quorumID[j]+ " appname "+appName);
                        forward(appName, PORT+j+1, quorumID[j]);
                        MineInitiatorLogger.debug("Connected to " + quorumID[j] + "on port "+(PORT+j+1)+ "with AppName" + appName);
                        qSocket[j] = new Socket("127.0.0.1", PORT+j+1);
                        qIn[j] = new BufferedReader(new InputStreamReader(qSocket[j].getInputStream()));
                        qOut[j] = new PrintStream(qSocket[j].getOutputStream());

                        //Verify QST Credits
                        qOut[j].println(qstDetails.toString());
                        qResponse[j] = qIn[j].readLine();
                        if(qResponse[j].equals("Verified")) {


                            qOut[j].println(detailsToken);
                            qResponse[j] = qIn[j].readLine();
                            if (qResponse[j].equals("Auth_Failed")) {
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                            } else {
                                MineInitiatorLogger.debug("Signature Received from " + quorumID[j] + " " + qResponse[j]);
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
                                        MineInitiatorLogger.debug(role + " node authenticated at index " + index);
                                        boolean voteStatus = voteNCount(index, quorumSize);
                                        if (quorumResponse[index] <= minQuorum(quorumSize) && voteStatus) {
                                            MineInitiatorLogger.debug("waiting for  " + quorumSize + " +signs " + role);
                                            while (quorumResponse[index] < minQuorum(quorumSize)) {
                                            }
                                            MineInitiatorLogger.debug("between Q1- to Q" + quorumSize + " for index " + index);
                                            quorumSign(didHash, hash, qResponse[j], index, quorumSize, alphaSize);
                                            quorumWithShares.add(quorumPeersObject.getString(j));
                                            while (quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
                                            }
                                            MineInitiatorLogger.debug("sending Qsign  of length " + quorumSignature.length() + "at index " + index);
                                            qOut[j].println(finalQuorumSignsArray.toString());
                                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                        } else {
                                            MineInitiatorLogger.debug("sending null for slow quorum ");
                                            qOut[j].println("null");
                                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                        }
                                        MineInitiatorLogger.debug("Quorum Count : " + quorumResponse + "Signature count : " + quorumSignature.length());
                                    } else {
                                        MineInitiatorLogger.debug("node failed authentication with index " + index + " with role " + role + " with did " + didHash + " and data to verify " + detailsToVerify);
                                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                    }
                                }
                            }
                        }
                        else if(qResponse[j].equals("440")){
                            MineInitiatorLogger.debug("Credit Verification failed: Duplicates found");
                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                        }
                        else if(qResponse[j].equals("441")){
                            MineInitiatorLogger.debug("Credit Verification failed: Signature(s) verification failed");
                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                        }
                        else if(qResponse[j].equals("442")){
                            MineInitiatorLogger.debug("Credit Verification failed: Credits hash mismatch");
                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                        }
                    } catch (IOException | JSONException e) {
                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                        MineInitiatorLogger.error("IOException Occurred");
                        e.printStackTrace();
                    }
                });
                quorumThreads[j].start();
            }

            while(quorumResponse[index] < minQuorum(quorumSize)  || quorumSignature.length() < (minQuorum(alphaSize) + 2* minQuorum(7))){}

        } catch (JSONException e) {
            MineInitiatorLogger.error("JSON Exception Occurred", e);
            e.printStackTrace();
        }
        return quorumSignature;
    }
}
