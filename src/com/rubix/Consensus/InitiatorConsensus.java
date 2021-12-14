package com.rubix.Consensus;

import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.QUORUM_COUNT;
import static com.rubix.Resources.Functions.getValues;
import static com.rubix.Resources.Functions.minQuorum;
import static com.rubix.Resources.Functions.nodeData;
import static com.rubix.Resources.IPFSNetwork.dhtOwnerCheck;
import static com.rubix.Resources.IPFSNetwork.forward;
import static com.rubix.Resources.IPFSNetwork.repo;
import static com.rubix.Resources.IPFSNetwork.swarmConnectP2P;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.ArrayList;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Resources.IPFSNetwork;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.ipfs.api.IPFS;


public class InitiatorConsensus {

    public static Logger InitiatorConsensusLogger = Logger.getLogger(InitiatorConsensus.class);


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
                InitiatorConsensusLogger.debug("quorum response added index " + i + "  is " + quorumResponse[i] + " quorumsize " + minQuorum(quorumSize));
                status = true;
            } else {
                status = false;
                InitiatorConsensusLogger.debug("Consensus Reached for index " + i);
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
                    jsonObject.put("sign", quorumSignResponse);
                    jsonObject.put("hash", hash);
                    finalQuorumSignsArray.put(jsonObject);
                    quorumSignature.put(quorumDID, quorumSignResponse);
                } else {
                    InitiatorConsensusLogger.debug("quorum already reached consensus " + quorumSignature.length());
                }
            } catch (JSONException e) {
                InitiatorConsensusLogger.error("JSON Exception Occurred", e);
                e.printStackTrace();
            }
        }
    }

    public static JSONObject countQuorumSigns(String blockObject) throws JSONException, InterruptedException, IOException {

        // convert blockHash to a JSONObject
        JSONObject response = new JSONObject();
        response.put("blockHash", blockObject);

        // convert blockObject string to json object 
        JSONObject blockObjectJson = new JSONObject(blockObject);
        JSONArray metadataArray = blockObjectJson.getJSONArray("metadata");

        response.put("files", metadataArray.length());

        for (int i = 0; i < metadataArray.length(); i++) {
            JSONObject metadataObject = metadataArray.getJSONObject(i);
            String metadata_hash = metadataObject.getString("metadata_hash");
            ArrayList dhtOwnersList = dhtOwnerCheck(metadata_hash);
            response.put(metadata_hash, dhtOwnersList.size());
        }

        response.put("status", "Success");

        return response;
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
    public static JSONObject start(String data, IPFS ipfs, int PORT, int index, String role, JSONArray quorumPeersObject, int alphaSize, int quorumSize) throws JSONException {
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
        quorumResponse[index] = 0;
        JSONArray tokenDetails;
        try {
            tokenDetails = new JSONArray(details.toString());
            JSONObject detailsToken = tokenDetails.getJSONObject(0);
            JSONObject sharesToken = tokenDetails.getJSONObject(1);

            String[] shares = new String[minQuorum(7) - 1];
            for (int i = 0; i < shares.length; i++) {
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
                        swarmConnectP2P(quorumID[j], ipfs);
                        String quorumDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", quorumID[j]);
                        String quorumWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid", quorumID[j]);
                        nodeData(quorumDidIpfsHash, quorumWidIpfsHash, ipfs);
                        String appName = quorumID[j].concat(role);
                        forward(appName, PORT + j, quorumID[j]);
                        InitiatorConsensusLogger.debug("Connected to " + quorumID[j] + "on port " + (PORT + j) + "with AppName" + appName);
                        qSocket[j] = new Socket("127.0.0.1", PORT + j);
                        qIn[j] = new BufferedReader(new InputStreamReader(qSocket[j].getInputStream()));
                        qOut[j] = new PrintStream(qSocket[j].getOutputStream());
                        qOut[j].println("qstcmrequest");
                        qVerification[j] = qIn[j].readLine();
                        JSONObject quorumDetails = new JSONObject(qVerification[j]);
                        String cmData = IPFSNetwork.get(quorumDetails.getString("CreditMapping"), ipfs);

                        JSONObject qstContent = new JSONObject(quorumDetails.getString("QuorumSignedTransactions"));


//                        if (qstContent.length() == 0 && role == "alpha") {
//                            InitiatorConsensusLogger.warn("Alpha quorum (" + quorumID[j] + ") has no credits");
//                        }
//                        if (cmContent.length() == 0 && role == "alpha") {
//                            InitiatorConsensusLogger.warn("Alpha quorum (" + quorumID[j] + ") has no credits in credit mapping data");
//                        }

                        if (!qstContent.has("minestatus") && qstContent.length() != 0) {
                            if (!qstContent.toString().contains("empty")) {
                                JSONArray cmContent = new JSONArray(cmData);
                                String credits = qstContent.getString("credits");

                                String creditContent = IPFSNetwork.get(credits, ipfs);
                                JSONArray credObject = new JSONArray(creditContent);
                                for (int k = 0; k < credObject.length(); k++) {
                                    JSONObject object = credObject.getJSONObject(k);
                                    String did = object.getString("did");
                                    String sign = object.getString("sign");
                                    String signHash = object.getString("hash");

                                    JSONObject hashedCredObject = new JSONObject();
                                    hashedCredObject.put("did", did);
                                    hashedCredObject.put("hash", signHash);
                                    hashedCredObject.put("signature", sign);


                                    if (!(Authenticate.verifySignature(hashedCredObject.toString())))
                                        InitiatorConsensusLogger.warn("Credit verification failed for Alpha quorum (" + quorumID[j] + ") credits");


                                    if (cmContent != null) {
                                        for (int l = 0; l < cmContent.length(); l++) {
                                            if ((cmContent.getJSONObject(l).getString("hash") == signHash)) {
                                                InitiatorConsensusLogger.warn("Credit verification failed for Alpha quorum (" + quorumID[j] + ") credits - Hash matched in Credits Mapping file");
                                            }
                                        }
                                    }

                                }
                            }
                        }


                        qOut[j].println(detailsToken);
                        qResponse[j] = qIn[j].readLine();
                        if (qResponse[j].equals("Auth_Failed")) {
                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                        } else {
                            InitiatorConsensusLogger.debug("Signature Received from " + quorumID[j]);
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
                                    boolean voteStatus = voteNCount(index, quorumSize);
                                    if (quorumResponse[index] <= minQuorum(quorumSize) && voteStatus) {
                                        while (quorumResponse[index] < minQuorum(quorumSize)) {
                                        }
                                        quorumSign(didHash, hash, qResponse[j], index, quorumSize, alphaSize);
                                        quorumWithShares.add(quorumPeersObject.getString(j));
                                        while (quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
                                        }

                                        qOut[j].println(finalQuorumSignsArray);
                                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                    } else {

                                        qOut[j].println("null");
                                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                    }
                                    InitiatorConsensusLogger.debug("Quorum Count : " + quorumResponse + "Signature count : " + quorumSignature.length());
                                } else {
                                    InitiatorConsensusLogger.debug("node failed authentication with index " + index + " with role " + role + " with did " + didHash + " and data to verify " + detailsToVerify);
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                }
                            }
                        }
                    } catch (IOException | JSONException e) {
                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                        InitiatorConsensusLogger.error("IOException Occurred");
                        e.printStackTrace();
                    }
                });
                quorumThreads[j].start();
            }

            while (quorumResponse[index] < minQuorum(quorumSize) || quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
            }
            repo(ipfs);

        } catch (JSONException e) {
            InitiatorConsensusLogger.error("JSON Exception Occurred", e);
            e.printStackTrace();
        }
        return quorumSignature;
    }
}
