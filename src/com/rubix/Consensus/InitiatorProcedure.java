package com.rubix.Consensus;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.calculateHash;
import static com.rubix.Resources.Functions.getSignFromShares;
import static com.rubix.Resources.Functions.initHash;
import static com.rubix.Resources.Functions.minQuorum;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

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
    public static JSONArray alphaReply, betaReply, gammaReply;

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
        String pvt = dataObject.getString("pvt");
        String senderDidIpfs = dataObject.getString("senderDidIpfs");
        String token = dataObject.getString("token");
        JSONArray alphaList = dataObject.getJSONArray("alphaList");
        JSONArray betaList = dataObject.getJSONArray("betaList");
        JSONArray gammaList = dataObject.getJSONArray("gammaList");

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

            if (WALLET_TYPE == 1) {
                senderSignQ = getSignFromShares(pvt, authSenderByQuorumHash);
            } else if (WALLET_TYPE == 2) {
                // API call to fexr
            } else {
                // create json file to write data
                InitiatorProcedureLogger.debug("Creating file to write signing data");
                String signFile = DATA_PATH + "/SignFile.json";
                File f = new File(signFile);
                if (f.exists()) {
                    f.delete();
                }
                writeToFile(signFile, "[]", false);
                // write sign details
                InitiatorProcedureLogger.debug("writing hash authSenderByRecHash " + authSenderByQuorumHash
                        + " to be signed with pvt share in to " + signFile);
                JSONObject signDetailsObject = new JSONObject();
                JSONArray signDetailsArray = new JSONArray();
                signDetailsObject.put("DID", senderDidIpfs);
                signDetailsObject.put("content", authSenderByQuorumHash);
                signDetailsArray.put(signDetailsObject);

                InitiatorProcedureLogger.debug("write signing data");
                writeToFile(signFile, signDetailsArray.toString(), false);
                InitiatorProcedureLogger.debug("################################");
                InitiatorProcedureLogger.debug(
                        "Please move file " + signFile
                                + " to cold Wallet for Signature and return back to same location");

                TimeUnit.MINUTES.sleep(1);

                InitiatorProcedureLogger.debug("Waiting for File with Signature from cold wallet");

                boolean fileModify = checkFile("SignFile.json", DATA_PATH);
                InitiatorProcedureLogger.debug("read sign file");

                String signFiledata ="";
                if(fileModify){
                    signFiledata = readFile(signFile);
                }
                

                signDetailsArray = new JSONArray(signFiledata);
                signDetailsObject = new JSONObject(signDetailsArray.getJSONObject(0));

                senderSignQ = signDetailsObject.getString("signature");
                InitiatorProcedureLogger.debug("SenderSignQ : " + senderSignQ);
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
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        JSONArray detailsForQuorum = new JSONArray();
        detailsForQuorum.put(data1);
        detailsForQuorum.put(data2);

        InitiatorProcedureLogger.debug("Invoking Consensus");

        JSONObject dataSend = new JSONObject();
        dataSend.put("hash", authQuorumHash);
        dataSend.put("details", detailsForQuorum);
        
        InitiatorProcedureLogger.debug("hash"+authQuorumHash);
        InitiatorProcedureLogger.debug("data1"+data1.toString());

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
            //nftdetails.put("rbtTokenDetails", dataObject.getJSONObject("rbtTokenDetails"));
            // nftdetails.put("sellerPvtKeySign", dataObject.getString("sellerPvtKeySign"));
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

        InitiatorConsensus.quorumSignature = new JSONArray();
        InitiatorConsensus.finalQuorumSignsArray = new JSONArray();
        alphaThread.start();
        betaThread.start();
        gammaThread.start();

        if (operation.equals("NFT")) {
            while ((InitiatorConsensus.nftQuorumSignature.length() < ((minQuorum(alphaSize) + 2 * minQuorum(7))))) {
            }
            InitiatorProcedureLogger.debug(
                    "ABG NFT Consensus completed with length for NFT :" + InitiatorConsensus.nftQuorumSignature.length()
                            + " RBT " + InitiatorConsensus.quorumSignature.length());
        } else {
            while (InitiatorConsensus.quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
            }
            InitiatorProcedureLogger
                    .debug("ABG Consensus completed with length " + InitiatorConsensus.quorumSignature.length());
        }
        InitiatorProcedureLogger
                .debug("ABG Consensus completed with length " + InitiatorConsensus.quorumSignature.length());
    }
}
