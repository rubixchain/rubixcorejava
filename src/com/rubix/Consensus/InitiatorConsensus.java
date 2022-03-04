package com.rubix.Consensus;

import static com.rubix.Constants.MiningConstants.MINED_RBT;
import static com.rubix.Constants.MiningConstants.MINED_RBT_SIGN;
import static com.rubix.Constants.MiningConstants.MINE_ID;
import static com.rubix.Constants.MiningConstants.MINE_ID_SIGN;
import static com.rubix.Constants.MiningConstants.MINE_TID;
import static com.rubix.Constants.MiningConstants.MINING_TID_SIGN;
import static com.rubix.Constants.MiningConstants.STAKED_QUORUM_DID;
import static com.rubix.Constants.MiningConstants.STAKED_TOKEN;
import static com.rubix.Constants.MiningConstants.STAKED_TOKEN_SIGN;
import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.QUORUM_COUNT;
import static com.rubix.Resources.Functions.WALLET_DATA_PATH;
import static com.rubix.Resources.Functions.checkTokenOwnershiByDID;
import static com.rubix.Resources.Functions.deleteFile;
import static com.rubix.Resources.Functions.getValues;
import static com.rubix.Resources.Functions.levelHeight;
import static com.rubix.Resources.Functions.minQuorum;
import static com.rubix.Resources.Functions.nodeData;
import static com.rubix.Resources.Functions.syncDataTable;
import static com.rubix.Resources.Functions.writeToFile;
import static com.rubix.Resources.IPFSNetwork.add;
import static com.rubix.Resources.IPFSNetwork.forward;
import static com.rubix.Resources.IPFSNetwork.getMineID;
import static com.rubix.Resources.IPFSNetwork.pin;
import static com.rubix.Resources.IPFSNetwork.repo;
import static com.rubix.Resources.IPFSNetwork.swarmConnectP2P;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketException;
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
    public static volatile JSONObject stakingSignature = new JSONObject();
    private static final Object countLock = new Object();
    private static final Object signLock = new Object();
    public static ArrayList<String> quorumWithShares = new ArrayList<>();
    public static volatile int[] quorumResponse = { 0, 0, 0 };
    public static volatile boolean stakeComplete = false;
    public static volatile JSONArray finalQuorumSignsArray = new JSONArray();
    public static JSONArray ownerSigns = new JSONArray();
    private static int levelHeightValue;

    /**
     * Added by Anuradha K on 04/01/20222.
     * To address SocketConnection reset Issue
     */
    private static int socketTimeOut = 120000;

    /**
     * This method increments the quorumResponse variable
     */
    private static synchronized boolean voteNCount(int i, int quorumSize) {
        boolean status;
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        synchronized (countLock) {
            if (quorumResponse[i] < minQuorum(quorumSize)) {
                quorumResponse[i]++;
                InitiatorConsensusLogger.debug("quorum response added index " + i + "  is " + quorumResponse[i]
                        + " quorumsize " + minQuorum(quorumSize));
                status = true;
            } else {
                status = false;
                InitiatorConsensusLogger.debug("Consensus Reached for index " + i);
            }
        }
        return status;
    }

    /**
     * This method increments the quorumResponse variable
     */
    // private static synchronized boolean stakingQuorumExists(int i) {
    // boolean status;
    // PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
    // synchronized (stakeLock) {
    // if (signedAlphaQuorumID[i] < 1) {
    // quorumResponse[i]++;
    // InitiatorConsensusLogger
    // .debug("quorum response for staking added index " + i + " is " +
    // quorumResponse[i]
    // + " quorumsize ");
    // status = false;
    // } else {
    // status = true;
    // InitiatorConsensusLogger.debug("Staking Reached for index " + i);
    // }
    // }
    // return status;
    // }

    /**
     * This method stores all the quorum signatures until required count for
     * consensus
     *
     * @param quorumDID          DID of the Quorum
     * @param quorumSignResponse Signature of the Quorum
     */
    private static synchronized void quorumSign(String quorumDID, String hash, String quorumSignResponse, int index,
            int quorumSize, int alphaSize) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        synchronized (signLock) {
            try {
                if (quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))
                        && quorumResponse[index] <= minQuorum(quorumSize)) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("did", quorumDID);
                    jsonObject.put("signature", quorumSignResponse);
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

    /**
     * This method runs the consensus
     * 1. Contact quorum with sender signatures and details
     * 2. Verify quorum signatures
     * 3. If consensus reached , sends shares to Quorum
     *
     * @param ipfs IPFS instance
     * @param PORT Port for forwarding to Quorum
     */
    public static JSONObject start(String data, IPFS ipfs, int PORT, int index, String role,
            JSONArray quorumPeersObject, int alphaSize, int quorumSize, String operation) throws JSONException {
        String[] qResponse = new String[QUORUM_COUNT];
        Socket[] qSocket = new Socket[QUORUM_COUNT];
        PrintStream[] qOut = new PrintStream[QUORUM_COUNT];
        BufferedReader[] qIn = new BufferedReader[QUORUM_COUNT];
        String[] quorumID = new String[QUORUM_COUNT];
        String[] signedAlphaQuorumPID = new String[7];
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        JSONObject dataObject = new JSONObject(data);
        String hash = dataObject.getString("hash");
        JSONArray details = dataObject.getJSONArray("details");

        quorumResponse[index] = 0;
        InitiatorConsensusLogger.debug("quorum peer role " + role + " length " + quorumPeersObject.length());
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
                        syncDataTable(null, quorumID[j]);
                        String quorumDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid",
                                quorumID[j]);

                        String quorumWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid",
                                quorumID[j]);
                        nodeData(quorumDidIpfsHash, quorumWidIpfsHash, ipfs);
                        String appName = quorumID[j].concat(role);
                        InitiatorConsensusLogger.debug("quourm ID " + quorumID[j] + " appname " + appName);
                        forward(appName, PORT + j, quorumID[j]);
                        InitiatorConsensusLogger.debug(
                                "Connected to " + quorumID[j] + "on port " + (PORT + j) + "with AppName" + appName);

                        qSocket[j] = new Socket("127.0.0.1", PORT + j);
                        qSocket[j].setSoTimeout(socketTimeOut);
                        qIn[j] = new BufferedReader(new InputStreamReader(qSocket[j].getInputStream()));
                        qOut[j] = new PrintStream(qSocket[j].getOutputStream());

                        qOut[j].println(operation);

                        if (operation.equals("new-credits-mining")) {

                            // ? QST + credit info
                            JSONObject qstDetails = dataObject.getJSONObject("qstDetails");
                            qOut[j].println(qstDetails.toString());
                            try {
                                qResponse[j] = qIn[j].readLine();
                            } catch (SocketException e) {
                                InitiatorConsensusLogger.warn("Quorum " + quorumID[j] + " is unable to Respond!");
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                            }
                            if (qResponse[j] != null) {
                                if (qResponse[j].equals("Verified")) {

                                    qOut[j].println(detailsToken);
                                    try {
                                        qResponse[j] = qIn[j].readLine();
                                    } catch (SocketException e) {
                                        InitiatorConsensusLogger
                                                .warn("Quorum " + quorumID[j] + " is unable to Respond!");
                                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                    }
                                    if (qResponse[j] != null) {
                                        if (qResponse[j].equals("Auth_Failed")) {
                                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                        } else {
                                            InitiatorConsensusLogger.trace(
                                                    "Signature Received from Q" + j + "(" + quorumID[j] + ") : "
                                                            + qResponse[j]);
                                            if (quorumResponse[index] > minQuorum(quorumSize)) {
                                                qOut[j].println("null");
                                                IPFSNetwork
                                                        .executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                            } else {
                                                String didHash = getValues(DATA_PATH + "DataTable.json", "didHash",
                                                        "peerid", quorumID[j]);
                                                JSONObject detailsToVerify = new JSONObject();
                                                detailsToVerify.put("did", didHash);
                                                detailsToVerify.put("hash", hash);
                                                detailsToVerify.put("signature", qResponse[j]);
                                                if (Authenticate.verifySignature(detailsToVerify.toString())) {
                                                    InitiatorConsensusLogger
                                                            .debug(role + " node authenticated at index " + index);
                                                    boolean voteStatus = voteNCount(index, quorumSize);
                                                    if (quorumResponse[index] <= minQuorum(quorumSize) && voteStatus) {
                                                        InitiatorConsensusLogger.debug(
                                                                "waiting for  " + quorumSize + " +signs " + role);
                                                        while (quorumResponse[index] < minQuorum(quorumSize)) {
                                                        }
                                                        InitiatorConsensusLogger.debug("between Q1- to Q" + quorumSize
                                                                + " for index " + index);
                                                        quorumSign(didHash, hash, qResponse[j], index, quorumSize,
                                                                alphaSize);
                                                        quorumWithShares.add(quorumPeersObject.getString(j));
                                                        while (quorumSignature
                                                                .length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
                                                        }
                                                        InitiatorConsensusLogger.debug("sending Qsign  of length "
                                                                + quorumSignature.length() + "at index " + index);
                                                        qOut[j].println(finalQuorumSignsArray.toString());
                                                        IPFSNetwork.executeIPFSCommands(
                                                                "ipfs p2p close -t /p2p/" + quorumID[j]);
                                                    } else {
                                                        InitiatorConsensusLogger.debug("sending null for slow quorum ");
                                                        qOut[j].println("null");
                                                        IPFSNetwork.executeIPFSCommands(
                                                                "ipfs p2p close -t /p2p/" + quorumID[j]);
                                                    }
                                                    InitiatorConsensusLogger.debug("Quorum Count : " + quorumResponse
                                                            + "Signature count : " + quorumSignature.length());
                                                } else {
                                                    InitiatorConsensusLogger
                                                            .debug("node failed authentication with index " + index
                                                                    + " with role " + role + " with did " + didHash
                                                                    + " and data to verify " + detailsToVerify);
                                                    IPFSNetwork.executeIPFSCommands(
                                                            "ipfs p2p close -t /p2p/" + quorumID[j]);
                                                }
                                            }
                                        }
                                    }

                                    try {
                                        qResponse[j] = qIn[j].readLine();
                                    } catch (SocketException e) {
                                        InitiatorConsensusLogger
                                                .warn("Quorum " + quorumID[j] + " is unable to Respond!");
                                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                    }

                                    if (qResponse[j] != null) {
                                        if (qResponse[j].equals("Auth_Failed")) {
                                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                        } else {
                                            JSONObject signObject = new JSONObject();
                                            signObject.put("did", quorumID[j]);
                                            signObject.put("hash", detailsToken.getString("ownerIdentity"));
                                            signObject.put("signature", qResponse[j]);
                                            ownerSigns.put(signObject);

                                        }
                                    }

                                } else if (qResponse[j].equals("440")) {
                                    InitiatorConsensusLogger.debug("Credit Verification failed: Duplicates found");
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                } else if (qResponse[j].equals("441")) {
                                    InitiatorConsensusLogger
                                            .debug("Credit Verification failed: Signature(s) verification failed");
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                } else if (qResponse[j].equals("442")) {
                                    InitiatorConsensusLogger.debug("Credit Verification failed: Credits hash mismatch");
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                } else if (qResponse[j].equals("443")) {
                                    InitiatorConsensusLogger.debug("Failed to Initialize Credit Verification");
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                }
                            }
                        }

                        qOut[j].println(detailsToken);

                        try {
                            qResponse[j] = qIn[j].readLine();
                        } catch (SocketException e) {
                            InitiatorConsensusLogger.warn("Quorum " + quorumID[j] + " is unable to Respond!");
                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                        }

                        if (qResponse[j] != null) {
                            if (qResponse[j].equals("Auth_Failed")) {
                                InitiatorConsensusLogger.debug("Sender Authentication Failure at " + quorumID[j]);
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                            } else {
                                InitiatorConsensusLogger
                                        .debug("Signature Received from " + quorumID[j] + " " + qResponse[j]);
                                if (quorumResponse[index] > minQuorum(quorumSize)) {
                                    qOut[j].println("null");
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                } else {
                                    String didHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid",
                                            quorumID[j]);
                                    JSONObject detailsToVerify = new JSONObject();
                                    detailsToVerify.put("did", didHash);
                                    detailsToVerify.put("hash", hash);
                                    detailsToVerify.put("signatures", qResponse[j]);
                                    if (Authenticate.verifySignature(detailsToVerify.toString())) {
                                        InitiatorConsensusLogger.debug(role + " node authenticated at index " + index);
                                        boolean voteStatus = voteNCount(index, quorumSize);
                                        if (quorumResponse[index] <= minQuorum(quorumSize) && voteStatus) {

                                            if (j <= 7) {
                                                signedAlphaQuorumPID[j] = quorumID[j];
                                            }

                                            InitiatorConsensusLogger
                                                    .debug("waiting for  " + quorumSize + " +signs " + role);
                                            while (quorumResponse[index] < minQuorum(quorumSize)) {
                                            }
                                            InitiatorConsensusLogger
                                                    .debug("between Q1- to Q" + quorumSize + " for index " + index);
                                            quorumSign(didHash, hash, qResponse[j], index, quorumSize, alphaSize);
                                            quorumWithShares.add(quorumPeersObject.getString(j));
                                            while (quorumSignature
                                                    .length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
                                            }
                                            InitiatorConsensusLogger.debug("sending Qsign  of length "
                                                    + quorumSignature.length() + "at index " + index);
                                            qOut[j].println(finalQuorumSignsArray.toString());
                                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                        } else {
                                            InitiatorConsensusLogger.debug("sending null for slow quorum ");
                                            qOut[j].println("null");
                                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                        }
                                        InitiatorConsensusLogger.debug("Quorum Count : " + quorumResponse
                                                + "Signature count : " + quorumSignature.length());
                                    } else {
                                        InitiatorConsensusLogger.debug("node failed authentication with index " + index
                                                + " with role " + role + " with did " + didHash + " and data to verify "
                                                + detailsToVerify);
                                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[j]);
                                    }
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

            if (operation.equals("new-credits-mining")) {

                levelHeightValue = levelHeight();

                Thread[] stakingThreads = new Thread[signedAlphaQuorumPID.length];

                // choosing a quorum member from 1 to 5 who have signed the transaction
                for (int p = 0; p < signedAlphaQuorumPID.length; p++) {
                    int s = p;
                    stakingThreads[p] = new Thread(() -> {
                        try {
                            if (signedAlphaQuorumPID[s] != null) {
                                InitiatorConsensusLogger.debug(
                                        "Contacting Signed Alpha Quorum Peer : " + signedAlphaQuorumPID[s]
                                                + " for staking. Index: " + s);

                                qSocket[s] = new Socket("127.0.0.1", PORT + s);
                                qSocket[s].setSoTimeout(socketTimeOut);
                                qIn[s] = new BufferedReader(new InputStreamReader(qSocket[s].getInputStream()));
                                qOut[s] = new PrintStream(qSocket[s].getOutputStream());

                                qOut[s].println("stake-token");
                                try {

                                    qResponse[s] = qIn[s].readLine();
                                    // convert to JSON array
                                    JSONArray stakingTokenDetals = new JSONArray(qResponse[s]);
                                    // get token chain and token ID from the array
                                    String stakingTokenHash = stakingTokenDetals.getString(0);
                                    JSONArray stakingTokenChain = stakingTokenDetals.getJSONArray(1);

                                    String stakingQuorumDID = getValues(DATA_PATH + "DataTable.json", "didHash",
                                            "peerid", signedAlphaQuorumPID[s]);
                                    if (checkTokenOwnershiByDID(stakingTokenHash, stakingQuorumDID)
                                            && stakingTokenChain.length() > levelHeightValue) {

                                        InitiatorConsensusLogger.debug("Staking Token Received and Verified from "
                                                + signedAlphaQuorumPID[s] + " " + qResponse[s]);

                                        String mineID = getMineID(stakingTokenHash, ipfs);
                                        InitiatorConsensusLogger
                                                .debug("Sending mine ID to miner :" + mineID);
                                        JSONObject detToSign = new JSONObject();
                                        detToSign.put(MINE_ID, mineID);
                                        detToSign.put(MINE_TID, detailsToken.getString("Tid"));
                                        detToSign.put(MINED_RBT, detailsToken.getString("token"));
                                        qOut[s].println(detToSign.toString());

                                        JSONObject stakingQuorumSigned = new JSONObject();

                                        try {
                                            qResponse[s] = qIn[s].readLine();
                                            // convert to JSON array
                                            stakingQuorumSigned = new JSONObject(qResponse[s]);
                                            InitiatorConsensusLogger.debug("Signature for Mining ID Received from "
                                                    + signedAlphaQuorumPID[s] + " " + qResponse[s]);
                                        } catch (SocketException e) {
                                            InitiatorConsensusLogger
                                                    .warn("Quorum " + quorumID[s] + " is unable to Respond!");
                                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[s]);
                                        }

                                        JSONObject detailsToVerify = new JSONObject();
                                        detailsToVerify.put("did", stakingQuorumDID);
                                        detailsToVerify.put("hash", mineID);
                                        detailsToVerify.put("signature", stakingQuorumSigned.getString(MINE_ID_SIGN));

                                        if (Authenticate.verifySignature(detailsToVerify.toString())) {

                                            stakingSignature.put(MINE_TID, detailsToken.getString("Tid"));
                                            stakingSignature.put(MINED_RBT, detailsToken.getString("token"));
                                            stakingSignature.put(MINE_ID, mineID);
                                            stakingSignature.put(STAKED_QUORUM_DID, stakingQuorumDID);
                                            stakingSignature.put(STAKED_TOKEN, stakingTokenHash);

                                            stakingSignature.put(STAKED_TOKEN_SIGN, stakingQuorumSigned.getString(
                                                    STAKED_TOKEN_SIGN));
                                            stakingSignature.put(MINING_TID_SIGN, stakingQuorumSigned.getString(
                                                    MINING_TID_SIGN));
                                            stakingSignature.put(MINED_RBT_SIGN, stakingQuorumSigned.getString(
                                                    MINED_RBT_SIGN));
                                            stakingSignature.put(MINE_ID_SIGN,
                                                    stakingQuorumSigned.getString(MINE_ID_SIGN));

                                            if (!stakeComplete) {
                                                FileWriter shareWriter = new FileWriter(
                                                        new File(LOGGER_PATH + "mineID.txt"),
                                                        true);
                                                shareWriter.write(detailsToVerify.toString(0));
                                                shareWriter.close();
                                                File readMineID = new File(LOGGER_PATH + "mineID.txt");
                                                String mineData = add(readMineID.toString(), ipfs);
                                                pin(mineData, ipfs);

                                                File mineIDFile = new File(
                                                        WALLET_DATA_PATH.concat("/Stakes/").concat(mineData)
                                                                .concat(".json"));
                                                if (!mineIDFile.exists())
                                                    mineIDFile.createNewFile();
                                                writeToFile(mineIDFile.toString(), qResponse[s], false);

                                                InitiatorConsensusLogger.debug("Mine object: " + mineData);
                                                deleteFile(LOGGER_PATH + "mineID.txt");

                                                InitiatorConsensusLogger.debug("Staking Successful at index " + s);

                                                IPFSNetwork
                                                        .executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID[s]);

                                                JSONObject stakingInfo = new JSONObject();
                                                stakingInfo.put("StakerDID", stakingQuorumDID);
                                                stakingInfo.put("StakedokenID", stakingTokenHash);
                                                stakingInfo.put("minedToken", detailsToken);

                                                JSONArray creditArray = new JSONArray();

                                                InitiatorConsensusLogger.debug("Staking Successful at index " + s);
                                                InitiatorConsensusLogger
                                                        .debug("Sending Credits to DID: " + stakingQuorumDID);

                                                for (int i = 0; i < levelHeightValue; i++) {

                                                    creditArray.put(finalQuorumSignsArray);
                                                }

                                                qOut[s].println(creditArray);

                                                stakeComplete = true;
                                            } else {

                                                InitiatorConsensusLogger.debug("sending null for slow quorum");
                                                qOut[s].println("null");
                                                IPFSNetwork.executeIPFSCommands(
                                                        "ipfs p2p close -t /p2p/" + signedAlphaQuorumPID[s]);
                                            }

                                        } else {

                                            InitiatorConsensusLogger.debug("sending null for slow quorum");
                                            qOut[s].println("null");
                                            IPFSNetwork.executeIPFSCommands(
                                                    "ipfs p2p close -t /p2p/" + signedAlphaQuorumPID[s]);

                                        }

                                    } else {

                                        InitiatorConsensusLogger.debug("sending null for slow quorum ");
                                        qOut[s].println("null");
                                        IPFSNetwork.executeIPFSCommands(
                                                "ipfs p2p close -t /p2p/" + signedAlphaQuorumPID[s]);

                                    }
                                } catch (SocketException e) {
                                    InitiatorConsensusLogger
                                            .warn("Alpha Quorum (for Staking)" + signedAlphaQuorumPID[s]
                                                    + " is unable to Respond!");
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" +
                                            signedAlphaQuorumPID[s]);
                                }
                            }

                        } catch (Exception e) {
                            InitiatorConsensusLogger.error("Exception Occurred");
                            e.printStackTrace();
                        }
                    });
                    stakingThreads[s].start();
                }
                // check the balance
                // if balance is sufficient then stake the token
                // else send a null to Qa(j)
                // if Qa(j) sends a null then close the connection
                // else send the stake ID to Qa(j)
                // pin the stake ID (initiator) and keep it in a file for future use
                File stakesFolder = new File(WALLET_DATA_PATH.concat("/Stakes"));
                if (!stakesFolder.exists())
                    stakesFolder.mkdirs();
                // send mining fee to Qa(j) - 0.02 RBT
                // close the connection
            }

            while (quorumResponse[index] < minQuorum(quorumSize)
                    || quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
            }
            repo(ipfs);
        } catch (JSONException e) {
            InitiatorConsensusLogger.error("JSON Exception Occurred", e);
            e.printStackTrace();
        }
        return quorumSignature;
    }
}
