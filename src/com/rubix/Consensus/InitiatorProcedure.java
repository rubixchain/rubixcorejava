package com.rubix.Consensus;

import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.calculateHash;
import static com.rubix.Resources.Functions.getSignFromShares;
import static com.rubix.Resources.Functions.initHash;
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
        InitiatorProcedureLogger.debug("Incoming data to initator procedure is "+data);
        JSONObject dataSend = new JSONObject();
        JSONObject dataObject = new JSONObject(data);
        InitiatorProcedureLogger.debug("dataObject is "+dataObject.toString());
        String tid = dataObject.getString("tid");
        String pvt = dataObject.getString("pvt");
        String senderDidIpfs = dataObject.getString("senderDidIpfs");
        JSONArray alphaList = dataObject.getJSONArray("alphaList");
        JSONArray betaList = dataObject.getJSONArray("betaList");
        JSONArray gammaList = dataObject.getJSONArray("gammaList");
        InitiatorProcedureLogger.debug("Trans type is "+dataObject.getString(ConsensusConstants.TRANS_TYPE));
        
        if(dataObject.getString(ConsensusConstants.TRANS_TYPE).equals(ConsensusConstants.PRIMARY)) {
            String receiverDidIpfs = dataObject.getString("receiverDidIpfs");
            String token = dataObject.getString("token");
            String hash = dataObject.getString("hash");
            String message = dataObject.getString("message");
        	String authSenderByQuorumHash = calculateHash(message, "SHA3-256");
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
                senderSignQ = getSignFromShares(pvt, authSenderByQuorumHash);
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

            dataSend.put("hash", authQuorumHash);
            dataSend.put("details", detailsForQuorum);

            if (operation.equals("new-credits-mining")) {
                JSONObject qstDetails = dataObject.getJSONObject("qstDetails");
                dataSend.put("qstDetails", qstDetails);
            }

           
        }
        
        if(dataObject.getString(ConsensusConstants.TRANS_TYPE).equals(ConsensusConstants.DATA)) {
        	String blockHash = dataObject.getString("blockHash");
        	String authSenderByQuorumHash = calculateHash(blockHash, "SHA3-256");
            String authQuorumHash = calculateHash(authSenderByQuorumHash.concat(blockHash), "SHA3-256");

            InitiatorProcedureLogger.debug("Data Sender by Quorum Hash" +
                    authSenderByQuorumHash);
            InitiatorProcedureLogger.debug("Data Quorum Auth Hash" + authQuorumHash);

            try {
                payload.put("sender", senderDidIpfs);
                payload.put("blockHash", blockHash);
                payload.put("tid", tid);
                InitiatorProcedureLogger.debug("payload is "+payload.toString());
            } catch (JSONException e) {
                InitiatorProcedureLogger.error("JSON Exception occurred", e);
                e.printStackTrace();
            }

            Split.split(payload.toString());

            int[][] shares = Split.get135Shares();
            InitiatorProcedureLogger.debug("!================================!");
            InitiatorProcedureLogger.debug("!"+shares+"!");
            InitiatorProcedureLogger.debug("!                                !");
            InitiatorProcedureLogger.debug("!================================!");

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
                data1.put("blockHash", blockHash);
                data1.put(ConsensusConstants.TRANSACTION_ID, tid);
                data1.put(ConsensusConstants.HASH, authSenderByQuorumHash);
                data1.put(ConsensusConstants.INIT_HASH, initHash());
                data1.put(ConsensusConstants.TRANS_TYPE, "data");
                data2.put("Share1", Q1Share);
                data2.put("Share2", Q2Share);
                data2.put("Share3", Q3Share);
                data2.put("Share4", Q4Share);
                InitiatorProcedureLogger.debug("data1 is "+data1.toString());
                InitiatorProcedureLogger.debug("data2 is "+data2.toString());

            } catch (JSONException | IOException e) {
                InitiatorProcedureLogger.error("JSON Exception occurred", e);
                e.printStackTrace();
            }

            JSONArray detailsForQuorum = new JSONArray();
            detailsForQuorum.put(data1);
            detailsForQuorum.put(data2);
            InitiatorProcedureLogger.info(detailsForQuorum.toString());
            InitiatorProcedureLogger.debug("Invoking Consensus");

            dataSend.put("hash", authQuorumHash);
            dataSend.put("details", detailsForQuorum);
            InitiatorProcedureLogger.debug("data sending to init cons is "+ dataSend.toString());

        }

        
        Thread alphaThread = new Thread(() -> {
            try {
            	InitiatorProcedureLogger.debug("sending data to alphaThread dataSend JSON: "+dataSend.toString()+ "alpha is "+ alphaList + "alpha size is "+ alphaSize
                      +" Operation is "+ operation);
                alphaReply = InitiatorConsensus.start(dataSend.toString(), ipfs, PORT, 0, "alpha", alphaList, alphaSize,
                        alphaSize, operation);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        Thread betaThread = new Thread(() -> {
            try {
            	InitiatorProcedureLogger.debug("sending data to betaThread dataSend JSON: "+dataSend.toString()+ "beta is "+ betaList + "beta size is "+ alphaSize
                        +" Operation is "+ operation);
                betaReply = InitiatorConsensus.start(dataSend.toString(), ipfs, PORT + 100, 1, "beta", betaList,
                        alphaSize, 7, operation);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        Thread gammaThread = new Thread(() -> {
            try {
            	InitiatorProcedureLogger.debug("sending data to gammaThread dataSend JSON: "+dataSend.toString()+ "gamma is "+ gammaList + "alpha size is "+ alphaSize
                        +" Operation is "+ operation);
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
                .debug("ABG Consensus completed for " + dataObject.getString(ConsensusConstants.TRANS_TYPE) + " with length " + InitiatorConsensus.quorumSignature.length());
    }
}
