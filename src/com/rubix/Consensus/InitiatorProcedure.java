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
import com.rubix.TokenTransfer.TokenSender;

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
    public static JSONArray alphaReply;

    public static Logger InitiatorProcedureLogger = Logger.getLogger(InitiatorProcedure.class);

    /**
     * This function sets up the initials before the consensus
     * 
     * @param data Data required for hashing and signing
     * @param ipfs IPFS instance
     * @param PORT port for forwarding to quorum
     * @throws IOException
     */
	public static void consensusSetUp(
			String data, IPFS ipfs, int PORT, int alphaSize, String operation)
            throws JSONException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONObject dataObject = new JSONObject(data);
        String tid = dataObject.optString("tid");
        String senderPayloadHash = dataObject.optString("senderPayloadHash");
        String receiverDidIpfs = dataObject.optString("receiverDidIpfs");
        String pvt = dataObject.optString("pvt");
        String senderDidIpfs = dataObject.optString("senderDidIpfs");
        String token = dataObject.optString("token");
        JSONArray alphaList = dataObject.optJSONArray("alphaList");
        JSONArray betaList = dataObject.optJSONArray("betaList");
        JSONArray gammaList = dataObject.optJSONArray("gammaList");
        String senderPayloadSign = dataObject.optString("senderPayloadSign");
        senderSignQ = dataObject.optString("sign");
        String authQuorumHash = "";
        
 
        if(operation.equals("new-credits-mining")) {
        	
            String message = dataObject.getString("message");
        		String authSenderByQuorumHash = calculateHash(message, "SHA3-256");
             authQuorumHash = calculateHash(authSenderByQuorumHash.concat(receiverDidIpfs), "SHA3-256");
        	
        }else if (operation.equals("")){
			
            authQuorumHash = calculateHash(TokenSender.authSenderByRecHash.concat(receiverDidIpfs), "SHA3-256");

		}

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
            data1.put("sign", senderSignQ);
            data1.put("senderDID", senderDidIpfs);
            data1.put(ConsensusConstants.TRANSACTION_ID, tid);
            data1.put(ConsensusConstants.HASH, TokenSender.authSenderByRecHash);
            data1.put(ConsensusConstants.RECEIVERID, receiverDidIpfs);
            data1.put(ConsensusConstants.INIT_HASH, initHash());

            data2.put("Share1", Q1Share);
            data2.put("Share2", Q2Share);
            data2.put("Share3", Q3Share);
            data2.put("Share4", Q4Share);
        } catch (JSONException e) {
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
        
    //    InitiatorProcedureLogger.debug("hash"+authQuorumHash);
    //    InitiatorProcedureLogger.debug("details"+detailsForQuorum.toString());

        if (operation.equals("new-credits-mining")) {
            JSONObject qstDetails = dataObject.getJSONObject("qstDetails");
            dataSend.put("qstDetails", qstDetails);
        }

        if (operation.equals("NFT")) {
            JSONObject nftdetails = new JSONObject();
            nftdetails.put("tid", dataObject.getString("tid"));
            nftdetails.put("sellerPubKeyIpfsHash", dataObject.getString("sellerPubKeyIpfsHash"));
            nftdetails.put("saleContractIpfsHash", dataObject.getString("saleContractIpfsHash"));
            nftdetails.put("nftTokenDetails", dataObject.getJSONObject("nftTokenDetails"));
            nftdetails.put("tokenAmount", dataObject.getDouble("tokenAmount"));
            InitiatorProcedureLogger.debug("NFT Token Detials "+dataObject.getJSONObject("nftTokenDetails").toString());
            String authNftSenderByQuorumHash = calculateHash(dataObject.getJSONObject("nftTokenDetails").toString(),
                    "SHA3-256");

            //creating authNftQuorumHash
            String authNftQuorumHash = calculateHash(authNftSenderByQuorumHash.concat(receiverDidIpfs), "SHA3-256");
            String nftSenderQsign=null;
            try {
                nftSenderQsign=getSignFromShares(pvt, authNftSenderByQuorumHash);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            InitiatorProcedureLogger.debug("authNftSenderByQuorumHash "+authNftSenderByQuorumHash);
            nftdetails.put("nftHash", authNftSenderByQuorumHash);
            
            nftdetails.put("nftQhash", authNftQuorumHash);
            
            nftdetails.put("nftBuyerDid", receiverDidIpfs);
            //pvt share signture quorum going to veify 
            nftdetails.put("nftSign", nftSenderQsign);

            dataSend.put("nftDetails", nftdetails);

        }

        Thread alphaThread = new Thread(() -> {
            try {
                alphaReply = InitiatorConsensus.start(dataSend.toString(), ipfs, PORT, 0, "alpha", alphaList, alphaSize,
                        alphaSize, operation);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });



        InitiatorConsensus.quorumSignature = new JSONArray();
        InitiatorConsensus.finalQuorumSignsArray = new JSONArray();
        alphaThread.start();

        if (operation.equals("NFT")) {
            while ((InitiatorConsensus.nftQuorumSignature.length() < ((minQuorum(alphaSize))))) {
            }
            InitiatorProcedureLogger.debug(
                    "ABG NFT Consensus completed with length for NFT :" + InitiatorConsensus.nftQuorumSignature.length()
                            + " RBT " + InitiatorConsensus.quorumSignature.length());
        } else {
            while (InitiatorConsensus.quorumSignature.length() < (minQuorum(alphaSize))) {
            }
            InitiatorProcedureLogger
                    .debug("ABG Consensus completed with length " + InitiatorConsensus.quorumSignature.length());
        }
        InitiatorProcedureLogger
                .debug("ABG Consensus completed with length " + InitiatorConsensus.quorumSignature.length());
    }
}
