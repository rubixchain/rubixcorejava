package com.rubix.Consensus;

import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.calculateHash;
import static com.rubix.Resources.Functions.getSignFromShares;
import static com.rubix.Resources.Functions.initHash;
import static com.rubix.Resources.Functions.minQuorum;
import static com.rubix.Resources.Functions.*;

import java.io.IOException;

import com.rubix.Constants.ConsensusConstants;
import com.rubix.SplitandStore.SeperateShares;
import com.rubix.SplitandStore.Split;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class InitiatorProcedure {
    public static String essential;
    public static String senderSignQ;
    public static JSONObject payload = new JSONObject();
    public static JSONObject alphaReply, betaReply, gammaReply;

    public static Logger InitiatorProcedureLogger = Logger.getLogger(InitiatorProcedure.class);

    /**
     * This function sets up the initials before the consensus
     * 
     * @param data Data required for hashing and signing
     * @param ipfs IPFS instance
     * @param PORT port for forwarding to quorum
     * @throws IOException
     */
    public static void consensusSetUp(String data, IPFS ipfs, int PORT, int alphaSize, String operation)
            throws JSONException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONObject dataObject = new JSONObject(data);
        String tid = dataObject.getString("tid");
        String message = dataObject.getString("message");
        String receiverDidIpfs = dataObject.getString("receiverDidIpfs");
        String pvt = "";
        if (WALLET_TYPE == 2) {
            pvt = dataObject.getString("pvt");
        }
        String senderDidIpfs = dataObject.getString("senderDidIpfs");
        String token = dataObject.getString("token");
        JSONArray alphaList = dataObject.getJSONArray("alphaList");
        JSONArray betaList = dataObject.getJSONArray("betaList");
        JSONArray gammaList = dataObject.getJSONArray("gammaList");

        String authSenderByQuorumHash = "";
        if (WALLET_TYPE == 2) {
            authSenderByQuorumHash = dataObject.getString("authSenderByQuorumHash");
        } else {
            authSenderByQuorumHash = calculateHash(message, "SHA3-256");
        }
        String authQuorumHash = calculateHash(authSenderByQuorumHash.concat(receiverDidIpfs), "SHA3-256");

        try {
            payload.put("sender", senderDidIpfs);
            payload.put("token", token);
            payload.put("receiver", receiverDidIpfs);
            payload.put("tid", tid);
        } catch (JSONException e) {
            InitiatorProcedureLogger.error("JSON Exception occurred", e);
            e.printStackTrace();
        }

        Split.split(payload.toString());

        int[][] shares = Split.get135Shares();
        InitiatorProcedureLogger.debug("Payload Split Success");
        essential = SeperateShares.getShare(shares, payload.toString().length(), 0);
        String Q1Share = SeperateShares.getShare(shares, payload.toString().length(), 1);
        String Q2Share = SeperateShares.getShare(shares, payload.toString().length(), 2);
        String Q3Share = SeperateShares.getShare(shares, payload.toString().length(), 3);
        String Q4Share = SeperateShares.getShare(shares, payload.toString().length(), 4);
        JSONObject data1 = new JSONObject();
        JSONObject data2 = new JSONObject();
        try {

            if (WALLET_TYPE == 2) {
                senderSignQ = dataObject.getString("senderSignQ");
            } else {
                senderSignQ = getSignFromShares(pvt, authSenderByQuorumHash);
            }

            data1.put("sign", senderSignQ);
            data1.put("senderDID", senderDidIpfs);
            data1.put(ConsensusConstants.TRANSACTION_ID, tid);
            data1.put(ConsensusConstants.HASH, authSenderByQuorumHash);
            data1.put(ConsensusConstants.RECEIVERID, receiverDidIpfs);
            data1.put(ConsensusConstants.INIT_HASH, initHash());

            data2.put("Share1", Q1Share);
            data2.put("Share2", Q2Share);
            data2.put("Share3", Q3Share);
            data2.put("Share4", Q4Share);
        } catch (JSONException | IOException e) {
            InitiatorProcedureLogger.error("JSON Exception occurred", e);
            e.printStackTrace();
        }

        JSONArray detailsForQuorum = new JSONArray();
        detailsForQuorum.put(data1);
        detailsForQuorum.put(data2);

        InitiatorProcedureLogger.debug("Invoking Consensus");

        JSONObject dataSend = new JSONObject();
        dataSend.put("hash", authQuorumHash);
        dataSend.put("details", detailsForQuorum);

        InitiatorProcedureLogger.debug("hash" + authQuorumHash);
        InitiatorProcedureLogger.debug("data1" + data1.toString());

        if (operation.equals("new-credits-mining")) {
            JSONObject qstDetails = dataObject.getJSONObject("qstDetails");
            dataSend.put("qstDetails", qstDetails);
        }

        Thread alphaThread = new Thread(() -> {
            try {
                alphaReply = InitiatorConsensus.start(dataSend.toString(), ipfs, PORT, 0, "alpha", alphaList, alphaSize,
                        alphaSize, operation);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        Thread betaThread = new Thread(() -> {
            try {
                betaReply = InitiatorConsensus.start(dataSend.toString(), ipfs, PORT + 100, 1, "beta", betaList,
                        alphaSize, 7, operation);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        Thread gammaThread = new Thread(() -> {
            try {
                gammaReply = InitiatorConsensus.start(dataSend.toString(), ipfs, PORT + 107, 2, "gamma", gammaList,
                        alphaSize, 7, operation);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        InitiatorConsensus.quorumSignature = new JSONObject();
        InitiatorConsensus.finalQuorumSignsArray = new JSONArray();
        alphaThread.start();
        betaThread.start();
        gammaThread.start();
        while (InitiatorConsensus.quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
        }
        InitiatorProcedureLogger
                .debug("ABG Consensus completed with length " + InitiatorConsensus.quorumSignature.length());
    }
}
