package com.rubix.Consensus;

import com.rubix.Constants.ConsensusConstants;
import com.rubix.SplitandStore.SeperateShares;
import com.rubix.SplitandStore.Split;
import io.ipfs.api.IPFS;
import javafx.util.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import static com.rubix.Consensus.InitiatorConsensus.consensusStatus;
import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.Functions.DATA_PATH;

public class InitiatorProcedure {
    public static String essential, consensus, tid, senderSignQ;
    public static JSONObject payload = new JSONObject();
    public static JSONObject quorumSigns;
    public static void consensusSetUp(String appExt, String message, String receiver, String pvt, String sender, String token, String username, ArrayList<String> quorumlist, IPFS ipfs, int PORT) throws IOException, InterruptedException, NoSuchAlgorithmException, JSONException {
        pathSet(username);
        String senderWalletID = getValues(DATA_PATH + "DataTable.json", "wid", "peer-id", sender);
        String authSenderByQuorumHash = calculateHash(message , "SHA3-256");
        String authQuorumHash = calculateHash(authSenderByQuorumHash+receiver, "SHA3-256");
        tid = calculateHash(authQuorumHash, "SHA3-224");

        payload.put("sender", sender);
        payload.put("token", token);
        payload.put("receiver", receiver);
        payload.put("tid", tid);
        Split.split(payload.toString());

        int[][] shares = Split.get135Shares();
        essential = SeperateShares.getShare(shares, payload.toString().length(), 0);
        String Q1Share = SeperateShares.getShare(shares, payload.toString().length(), 1);
        String Q2Share = SeperateShares.getShare(shares, payload.toString().length(), 2);
        String Q3Share = SeperateShares.getShare(shares, payload.toString().length(), 3);
        String Q4Share = SeperateShares.getShare(shares, payload.toString().length(), 4);


        senderSignQ = getSignFromShares(pvt, authSenderByQuorumHash);

        JSONObject data1 = new JSONObject();
        data1.put("sign", senderSignQ);
        data1.put("senderPID", sender);
        data1.put(ConsensusConstants.TRANSACTION_ID, tid);
        data1.put(ConsensusConstants.HASH, authSenderByQuorumHash);
        data1.put(ConsensusConstants.RECEIVERID, receiver);

        JSONObject data2 = new JSONObject();
        data2.put("Share1", Q1Share);
        data2.put("Share2", Q2Share);
        data2.put("Share3", Q3Share);
        data2.put("Share4", Q4Share);


        JSONArray detailsForQuorum = new JSONArray();
        detailsForQuorum.put(data1);
        detailsForQuorum.put(data2);

        System.out.println("Starting Consensus");
        quorumSigns = InitiatorConsensus.start(appExt, authQuorumHash, detailsForQuorum,username,quorumlist,ipfs, PORT);

        consensus = consensusStatus();
        System.out.println("status:" + consensus);

    }
}
