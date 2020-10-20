package com.rubix.Consensus;


import com.rubix.Constants.ConsensusConstants;
import com.rubix.SplitandStore.SeperateShares;
import com.rubix.SplitandStore.Split;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import static com.rubix.Resources.Functions.*;

public class InitiatorProcedure {
    public static String essential;
    public static String consensus;
    public static String senderSignQ;
    public static JSONObject payload = new JSONObject();

    public static Logger InitiatorProcedureLogger = Logger.getLogger(InitiatorProcedure.class);

    /**
     * This function sets up the initials before the consensus
     * @param tid Transaction Id
     * @param message Data to be signed on
     * @param receiverDidIpfs DID of the receiver
     * @param pvt Private Shares path
     * @param senderDidIpfs DID of the sender
     * @param token Token with attached data for signing
     * @param quorumlist List of quorum peers
     * @param ipfs IPFS instance
     * @param PORT port for forwarding to quorum
     */
    public static void consensusSetUp(String tid, String message, String receiverDidIpfs, String pvt, String senderDidIpfs, String token, ArrayList<String> quorumlist, IPFS ipfs, int PORT) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        String authSenderByQuorumHash="", authQuorumHash="";
        authSenderByQuorumHash = calculateHash(message , "SHA3-256");
        authQuorumHash = calculateHash(authSenderByQuorumHash.concat(receiverDidIpfs), "SHA3-256");
        InitiatorProcedureLogger.debug("Sender by Quorum Hash" + authSenderByQuorumHash);
        InitiatorProcedureLogger.debug("Quorum Auth Hash" + authQuorumHash);

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
            senderSignQ = getSignFromShares(pvt, authSenderByQuorumHash);
            data1.put("sign", senderSignQ);
            data1.put("senderDID", senderDidIpfs);
            data1.put(ConsensusConstants.TRANSACTION_ID, tid);
            data1.put(ConsensusConstants.HASH, authSenderByQuorumHash);
            data1.put(ConsensusConstants.RECEIVERID, receiverDidIpfs);

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


        InitiatorConsensus.start(authQuorumHash, detailsForQuorum,quorumlist,ipfs, PORT);
//        consensus = consensusStatus();
//        InitiatorProcedureLogger.debug("Consensus Status: " + consensus);
    }
}
