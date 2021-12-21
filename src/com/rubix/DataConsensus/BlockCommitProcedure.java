package com.rubix.DataConsensus;

import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.calculateHash;
import static com.rubix.Resources.Functions.getSignFromShares;
import static com.rubix.Resources.Functions.minQuorum;

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

public class BlockCommitProcedure {
    public static String essential;
    public static String senderSignQ;
    public static JSONObject payload = new JSONObject();
    public static JSONObject alphaReply, betaReply, gammaReply;

    public static Logger BlockCommitProcedureLogger = Logger.getLogger(BlockCommitProcedure.class);

    /**
     * This function sets up the initials before the consensus
     * 
     * @param data Data required for hashing and signing
     * @param ipfs IPFS instance
     * @param PORT port for forwarding to quorum
     */
    public static void consensusSetUp(String data, IPFS ipfs, int PORT, int alphaSize) throws JSONException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONObject dataObject = new JSONObject(data);
        String tid = dataObject.getString("tid");
        String message = dataObject.getString("message");
        String blockHash = dataObject.getString("blockHash");
        String pvt = dataObject.getString("pvt");
        String senderDidIpfs = dataObject.getString("senderDidIpfs");
        // String token = dataObject.getString("token");
        JSONArray alphaList = dataObject.getJSONArray("alphaList");
        JSONArray betaList = dataObject.getJSONArray("betaList");
        JSONArray gammaList = dataObject.getJSONArray("gammaList");
        String authSenderByQuorumHash = "", authQuorumHash = "";
        authSenderByQuorumHash = message;
        authQuorumHash = calculateHash(authSenderByQuorumHash.concat(blockHash), "SHA3-256");

        try {
            payload.put("sender", senderDidIpfs);
            // payload.put("token", token);
            payload.put("blockHash", blockHash);
            payload.put("tid", tid);
        } catch (JSONException e) {
            BlockCommitProcedureLogger.error("JSON Exception occurred", e);
            e.printStackTrace();
        }

        Split.split(payload.toString());

        int[][] shares = Split.get135Shares();
        BlockCommitProcedureLogger.debug("Payload Split Success");

        JSONObject data1 = new JSONObject();
        JSONObject data2 = new JSONObject();

        try {

        essential = SeperateShares.getShare(shares, payload.toString().length(), 0);
        String Q1Share = SeperateShares.getShare(shares, payload.toString().length(), 1);
        String Q2Share = SeperateShares.getShare(shares, payload.toString().length(), 2);
        String Q3Share = SeperateShares.getShare(shares, payload.toString().length(), 3);
        String Q4Share = SeperateShares.getShare(shares, payload.toString().length(), 4);
        
        BlockCommitProcedureLogger.error("Created Shares Q1 to Q4");

            senderSignQ = getSignFromShares(pvt, authSenderByQuorumHash);
            data1.put("sign", senderSignQ);
            data1.put("senderDID", senderDidIpfs);
            data1.put(ConsensusConstants.TRANSACTION_ID, tid);
            data1.put(ConsensusConstants.HASH, authSenderByQuorumHash);
            // data1.put(ConsensusConstants.TYPE, ConsensusConstants.RBD);
            data1.put(ConsensusConstants.BLOCK, blockHash);

            data2.put("Share1", Q1Share);
            data2.put("Share2", Q2Share);
            data2.put("Share3", Q3Share);
            data2.put("Share4", Q4Share);
        } catch (JSONException | IOException e) {
            BlockCommitProcedureLogger.error("JSON Exception occurred at getSignFromShares", e);
            e.printStackTrace();

        }

        JSONArray detailsForQuorum = new JSONArray();
        detailsForQuorum.put(data1);
        detailsForQuorum.put(data2);

        BlockCommitProcedureLogger.debug("Invoking Consensus");

        JSONObject dataSend = new JSONObject();
        dataSend.put("hash", authQuorumHash);
        dataSend.put("details", detailsForQuorum);

        Thread alphaThread = new Thread(() -> {
            try {
                alphaReply = BlockCommitInitiator.start(dataSend.toString(), ipfs, PORT, 0, "alpha", alphaList, alphaSize,
                        alphaSize);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        Thread betaThread = new Thread(() -> {
            try {
                betaReply = BlockCommitInitiator.start(dataSend.toString(), ipfs, PORT + 100, 1, "beta", betaList,
                        alphaSize, 7);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        Thread gammaThread = new Thread(() -> {
            try {
                gammaReply = BlockCommitInitiator.start(dataSend.toString(), ipfs, PORT + 107, 2, "gamma", gammaList,
                        alphaSize, 7);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        BlockCommitInitiator.quorumSignature = new JSONObject();
        alphaThread.start();
        betaThread.start();
        gammaThread.start();
        while (BlockCommitInitiator.quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
        }
        BlockCommitProcedureLogger
                .debug("ABG Consensus completed with length " + BlockCommitInitiator.quorumSignature.length());
    }
}
