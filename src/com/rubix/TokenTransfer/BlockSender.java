package com.rubix.TokenTransfer;

import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.QuorumCheck;
import static com.rubix.Resources.Functions.QuorumSwarmConnect;
import static com.rubix.Resources.Functions.SEND_PORT;
import static com.rubix.Resources.Functions.WALLET_DATA_PATH;
import static com.rubix.Resources.Functions.calculateHash;
import static com.rubix.Resources.Functions.deleteFile;
import static com.rubix.Resources.Functions.getCurrentUtcTime;
import static com.rubix.Resources.Functions.getPeerID;
import static com.rubix.Resources.Functions.getQuorum;
import static com.rubix.Resources.Functions.getSignFromShares;
import static com.rubix.Resources.Functions.getValues;
import static com.rubix.Resources.Functions.minQuorum;
import static com.rubix.Resources.Functions.readFile;
import static com.rubix.Resources.Functions.updateJSON;
import static com.rubix.Resources.Functions.updateQuorum;
import static com.rubix.Resources.Functions.writeToFile;
import static com.rubix.Resources.IPFSNetwork.repo;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;

import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Consensus.InitiatorConsensus;
import com.rubix.Consensus.InitiatorProcedure;
import com.rubix.Resources.IPFSNetwork;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class BlockSender {
    private static final Logger BlockSenderLogger = Logger.getLogger(BlockSender.class);
    private static final String USER_AGENT = "Mozilla/5.0";
    public static BufferedReader serverInput;
    private static PrintStream output;
    private static BufferedReader input;
    private static Socket senderSocket;
    private static boolean senderMutex = false;
    // private static int heartBeatAlpha=0;
    // private static int heartBeatBeta=0;
    // private static int heartBeatGamma=0;
    // private static int alphaSize=0;
    //
    // private static ArrayList alphaPeersList;
    // private static ArrayList betaPeersList;
    // private static ArrayList gammaPeersList;

    /**
     * A sender node to transfer tokens
     *
     * @param data Details required for tokenTransfer
     * @param ipfs IPFS instance
     * @param port Sender port for communication
     * @return Transaction Details (JSONObject)
     * @throws IOException              handles IO Exceptions
     * @throws JSONException            handles JSON Exceptions
     * @throws NoSuchAlgorithmException handles No Such Algorithm Exceptions
     */
    public static JSONObject Send(String data, IPFS ipfs, int port) throws Exception {

        JSONObject APIResponse = new JSONObject();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        // String receiverPeerId;
        JSONObject detailsObject = new JSONObject(data);
        // String receiverDidIpfsHash = detailsObject.getString("receiverDidIpfsHash");
        String pvt = detailsObject.getString("pvt");
        // int amount = detailsObject.getInt("amount");
        int type = detailsObject.getInt("type");
        String blockHash = detailsObject.getString("blockHash");
        String comment = detailsObject.getString("comment");
        // JSONArray tokens = detailsObject.getJSONArray("tokens");
        // JSONArray tokenHeader = detailsObject.getJSONArray("tokenHeader");
        JSONArray quorumArray;
        JSONArray alphaQuorum = new JSONArray();
        JSONArray betaQuorum = new JSONArray();
        JSONArray gammaQuorum = new JSONArray();
        int heartBeatAlpha = 0;
        int heartBeatBeta = 0;
        int heartBeatGamma = 0;
        int alphaSize;

        ArrayList alphaPeersList;
        ArrayList betaPeersList;
        ArrayList gammaPeersList;

        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        BlockSenderLogger.debug("sender peer id" + senderPeerID);
        String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);
        BlockSenderLogger.debug("sender did ipfs hash" + senderDidIpfsHash);
        BlockSenderLogger.debug("path is" + DATA_PATH + senderDidIpfsHash);
        File folder = new File(DATA_PATH + senderDidIpfsHash);
        File[] listOfFiles = folder.listFiles();

        //? Need more details!
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                System.out.println("File " + listOfFiles[i].getName());
            } else if (listOfFiles[i].isDirectory()) {
                System.out.println("Directory " + listOfFiles[i].getName());
            }
        }

        BufferedImage senderWidImage = ImageIO.read(new File(DATA_PATH + senderDidIpfsHash + "/PublicShare.png"));
        String senderWidBin = PropImage.img2bin(senderWidImage);

        if (senderMutex) {
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Sender busy. Try again later");
            BlockSenderLogger.warn("Sender busy");
            return APIResponse;
        }

        senderMutex = true;

        String peerAuth;
        // ArrayList<String> allTokensChainsPushed = new ArrayList();
        APIResponse = new JSONObject();

        // if (tokens.length() == 0) {

        //     APIResponse.put("did", senderDidIpfsHash);
        //     APIResponse.put("tid", "null");
        //     APIResponse.put("status", "Failed");
        //     APIResponse.put("message", "No balance");
        //     senderMutex = false;
        //     return APIResponse;
        // }

        // for (int i = 0; i < tokens.length(); i++) {
        //     File token = new File(TOKENS_PATH + tokens.get(i));
        //     File tokenchain = new File(TOKENCHAIN_PATH + tokens.get(i) + ".json");
        //     BlockSenderLogger.debug(token + "and " + tokenchain);
        //     if (!(token.exists() && tokenchain.exists())) {
        //         BlockSenderLogger.info("Tokens Not Verified");
        //         senderMutex = false;
        //         APIResponse.put("did", senderDidIpfsHash);
        //         APIResponse.put("tid", "null");
        //         APIResponse.put("status", "Failed");
        //         APIResponse.put("message", "Invalid token(s)");
        //         return APIResponse;

        //     }
        //     add(TOKENS_PATH + tokens.get(i), ipfs);
        //     String tokenChainHash = add(TOKENCHAIN_PATH + tokens.get(i) + ".json", ipfs);
        //     allTokensChainsPushed.add(tokenChainHash);
        // }

        String authSenderByRecHash = calculateHash(
                blockHash + comment, "SHA3-256");
        String tid = calculateHash(authSenderByRecHash + comment, "SHA3-256");
        BlockSenderLogger.debug("Sender by Receiver Hash " + authSenderByRecHash);
        BlockSenderLogger.debug("TID on sender " + tid);

        writeToFile(LOGGER_PATH + "tempbeta", tid.concat(senderDidIpfsHash), false);
        String betaHash = IPFSNetwork.add(LOGGER_PATH + "tempbeta", ipfs);
        deleteFile(LOGGER_PATH + "tempbeta");

        writeToFile(LOGGER_PATH + "tempgamma", tid.concat(senderDidIpfsHash), false);
        String gammaHash = IPFSNetwork.add(LOGGER_PATH + "tempgamma", ipfs);
        deleteFile(LOGGER_PATH + "tempgamma");

        switch (type) {
            case 1: {
                quorumArray = getQuorum(betaHash, gammaHash, senderDidIpfsHash, senderDidIpfsHash, 1);
                break;
            }

            case 2: {
                quorumArray = new JSONArray(readFile(DATA_PATH + "quorumlist.json"));
                break;
            }
            case 3: {
                quorumArray = detailsObject.getJSONArray("quorum");
                break;
            }
            default: {
                BlockSenderLogger.error("Unknown quorum type input, cancelling transaction");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Unknown quorum type input, cancelling transaction");
                return APIResponse;

            }
        }

        QuorumSwarmConnect(quorumArray, ipfs);

        alphaSize = quorumArray.length() - 14;

        for (int i = 0; i < alphaSize; i++)
            alphaQuorum.put(quorumArray.getString(i));

        for (int i = 0; i < 7; i++) {
            betaQuorum.put(quorumArray.getString(alphaSize + i));
            gammaQuorum.put(quorumArray.getString(alphaSize + 7 + i));
        }

        BlockSenderLogger.debug("alphaquorum " + alphaQuorum + " size " + alphaQuorum.length());
        BlockSenderLogger.debug("betaquorum " + betaQuorum + " size " + betaQuorum.length());
        BlockSenderLogger.debug("gammaquorum " + gammaQuorum + " size " + gammaQuorum.length());

        alphaPeersList = QuorumCheck(alphaQuorum, alphaSize);
        betaPeersList = QuorumCheck(betaQuorum, 7);
        gammaPeersList = QuorumCheck(gammaQuorum, 7);

        // for(int i=0;i<alphaPeersList.size();i++) {
        // heartBeatAlpha += checkHeartBeat(alphaPeersList.get(i).toString(),
        // alphaPeersList.get(i).toString() + "alpha");
        // }
        //
        // for(int i=0;i<betaPeersList.size();i++) {
        // heartBeatBeta += checkHeartBeat(betaPeersList.get(i).toString(),
        // betaPeersList.get(i).toString() + "beta");
        // }
        // for(int i=0;i<gammaPeersList.size();i++) {
        // heartBeatGamma += checkHeartBeat(gammaPeersList.get(i).toString(),
        // gammaPeersList.get(i).toString() + "gamma");
        // }

        BlockSenderLogger.debug("alphaPeersList size " + alphaPeersList.size());
        BlockSenderLogger.debug("betaPeersList size " + betaPeersList.size());
        BlockSenderLogger.debug("gammaPeersList size " + gammaPeersList.size());
        // BlockSenderLogger.debug("heartBeatAlpha size "+ heartBeatAlpha);
        // BlockSenderLogger.debug("heartBeatBeta size "+ heartBeatBeta);
        // BlockSenderLogger.debug("heartBeatGamma size "+ heartBeatGamma);
        BlockSenderLogger.debug("minQuorumAlpha size " + minQuorum(alphaSize));

        // quorumPeersList = QuorumCheck(quorumArray, ipfs);

        // if
        // (alphaPeersList.size()<minQuorum(alphaSize)||betaPeersList.size()<5||gammaPeersList.size()<5
        // || heartBeatAlpha<minQuorum(alphaSize)||heartBeatBeta<5||heartBeatGamma<5) {

        if (alphaPeersList.size() < minQuorum(alphaSize) || betaPeersList.size() < 5 || gammaPeersList.size() < 5) {
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Quorum Members not available");
            BlockSenderLogger.warn("Quorum Members not available");
            senderMutex = false;
            return APIResponse;
        }

        String senderSign = getSignFromShares(pvt, authSenderByRecHash);

        // ? above code is verified for block sender logic

        JSONObject senderDetails2Receiver = new JSONObject();
        senderDetails2Receiver.put("sign", senderSign);
        senderDetails2Receiver.put("tid", tid);
        senderDetails2Receiver.put("comment", comment);

        // JSONArray tokenBindDetailsArray = new JSONArray();
        // JSONObject tokenDetails = new JSONObject();
        // // tokenDetails.put("token", tokens);
        // // tokenDetails.put("tokenChain", allTokensChainsPushed);
        // // tokenDetails.put("tokenHeader", tokenHeader);
        // // tokenDetails.put("sender", senderDidIpfsHash);

        // // String senderToken = tokenDetails.toString();

        // String consensusID = calculateHash(blockHash, "SHA3-256");
        // writeToFile(LOGGER_PATH + "consensusID", consensusID, false);
        // String consensusIDIPFSHash = IPFSNetwork.add(LOGGER_PATH + "consensusID", ipfs);
        // deleteFile(LOGGER_PATH + "consensusID");

        // JSONObject ipfsObject = new JSONObject();
        // ipfsObject.put("ipfsHash", consensusIDIPFSHash);
        // tokenBindDetailsArray.put(tokenDetails);
        // tokenBindDetailsArray.put(ipfsObject);

        // BlockSenderLogger.debug(
        //         "consensusID hash " + consensusIDIPFSHash + " unique own " + dhtEmpty(consensusIDIPFSHash, ipfs));

        // DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
        // Date date = new Date();
        //
        // LocalDate currentTime = LocalDate.parse(formatter.format(date).replace("/",
        // "-"));
        // receiverPeerId = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", receiverDidIpfsHash);

        // BlockSenderLogger.debug("Swarm connecting to " + receiverPeerId);
        // swarmConnectP2P(receiverPeerId, ipfs);
        BlockSenderLogger.debug("Swarm connected");

        // String receiverWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash",
        //         receiverDidIpfsHash);
        // nodeData(receiverDidIpfsHash, receiverWidIpfsHash, ipfs);

        // forward(receiverPeerId, port, receiverPeerId);

        // BlockSenderLogger.debug("Forwarded to " + receiverPeerId + " on " + port);
        senderSocket = new Socket("127.0.0.1", port);

        input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
        output = new PrintStream(senderSocket.getOutputStream());

        long startTime = System.currentTimeMillis();

        output.println(senderPeerID);
        BlockSenderLogger.debug("Sent PeerID");

        peerAuth = input.readLine();

        // while ((peerAuth = input.readLine()) == null) {
        //// forward(receiverPeerId, port, receiverPeerId);
        //// senderSocket = new Socket("127.0.0.1", port);
        //// input = new BufferedReader(new
        // InputStreamReader(senderSocket.getInputStream()));
        //// output = new PrintStream(senderSocket.getOutputStream());
        //// output.println(senderPeerID);
        // }
        if (!peerAuth.equals("200")) {
            // executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            BlockSenderLogger.info("Sender Data Not Available");
            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Sender Data Not Available");
            return APIResponse;

        }

        // output.println(tokenBindDetailsArray);

        String tokenAuth = input.readLine();

        // if (!tokenAuth.equals("200")) {
        //     String errorMessage = null;
        //     executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);

        //     output.close();
        //     input.close();
        //     senderSocket.close();
        //     senderMutex = false;
        //     switch (tokenAuth) {
        //         case "420":
        //             errorMessage = "Consensus ID not unique: Hashes do not match";
        //             break;
        //         case "421":
        //             errorMessage = "Consensus ID not unique: More than one provider for consensus ID hash";
        //             break;
        //         case "422":
        //             errorMessage = "Tokens not verified";
        //             break;
        //     }

        //     BlockSenderLogger.info(errorMessage);
        //     updateQuorum(quorumArray, null, false, type);
        //     APIResponse.put("did", senderDidIpfsHash);
        //     APIResponse.put("tid", tid);
        //     APIResponse.put("status", "Failed");
        //     APIResponse.put("message", errorMessage);

        //     return APIResponse;

        // }

        // output.println(senderDetails2Receiver);

        JSONObject dataObject = new JSONObject();
        dataObject.put("tid", tid);
        // dataObject.put("message", consensusIDIPFSHash);
        dataObject.put("blockHash", blockHash);
        dataObject.put("pvt", pvt);
        dataObject.put("senderDidIpfs", senderDidIpfsHash);
        // dataObject.put("token", tokens.toString());
        dataObject.put("alphaList", alphaPeersList);
        dataObject.put("betaList", betaPeersList);
        dataObject.put("gammaList", gammaPeersList);

        BlockSenderLogger.debug("dataobject " + dataObject.toString());

        InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 100, alphaSize);
        BlockSenderLogger.debug("length on sender " + InitiatorConsensus.quorumSignature.length() + "response count "
                + InitiatorConsensus.quorumResponse);
        if (InitiatorConsensus.quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
            // if (!(InitiatorProcedure.alphaReply.length() >= minQuorum(7))) {
            BlockSenderLogger.debug("Consensus Failed");
            senderDetails2Receiver.put("status", "Consensus Failed");
            senderDetails2Receiver.put("quorumsign", InitiatorConsensus.quorumSignature.toString());
            // output.println(senderDetails2Receiver);
            // executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Transaction declined by Quorum");
            return APIResponse;

        }

        BlockSenderLogger.debug("Consensus Reached");
        senderDetails2Receiver.put("status", "Consensus Reached");
        senderDetails2Receiver.put("quorumsign", InitiatorConsensus.quorumSignature.toString());

        // output.println(senderDetails2Receiver);
        // output.println("Consensus Reached");
        BlockSenderLogger.debug("Quorum Signatures length " + InitiatorConsensus.quorumSignature.length());
        // output.println(InitiatorConsensus.quorumSignature);

        String signatureAuth = input.readLine();

        long endAuth = System.currentTimeMillis();
        long totalTime = endAuth - startTime;
        if (!signatureAuth.equals("200")) {
            // executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            BlockSenderLogger.info("Authentication Failed");
            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Sender not authenticated");
            return APIResponse;

        }

        // for (int i = 0; i < tokens.length(); i++)
        //     unpin(String.valueOf(tokens.get(i)), ipfs);

        // unpin(consensusIDIPFSHash, ipfs);

        repo(ipfs);

        // BlockSenderLogger.debug("Unpinned Tokens");
        // output.println("Unpinned");

        // String confirmation = input.readLine();
        // if (!confirmation.equals("Successfully Pinned")) {
        //     BlockSenderLogger.warn("Multiple Owners for the token");
        //     // executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
        //     BlockSenderLogger.info("Tokens with multiple pins");
        //     output.close();
        //     input.close();
        //     senderSocket.close();
        //     senderMutex = false;
        //     updateQuorum(quorumArray, null, false, type);
        //     APIResponse.put("did", senderDidIpfsHash);
        //     APIResponse.put("tid", tid);
        //     APIResponse.put("status", "Failed");
        //     APIResponse.put("message", "Tokens with multiple pins");
        //     return APIResponse;

        // }
        // output.println(InitiatorProcedure.essential);
        // String respAuth = input.readLine();

        // if (!respAuth.equals("Send Response")) {

        //     // executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
        //     output.close();
        //     input.close();
        //     senderSocket.close();
        //     senderMutex = false;
        //     updateQuorum(quorumArray, null, false, type);
        //     APIResponse.put("did", senderDidIpfsHash);
        //     APIResponse.put("tid", tid);
        //     APIResponse.put("status", "Failed");
        //     APIResponse.put("message", "Receiver process not over");
        //     BlockSenderLogger.info("Incomplete Transaction");
        //     return APIResponse;

        // }

        //? below code is verified for block sender logic

        Iterator<String> keys = InitiatorConsensus.quorumSignature.keys();
        JSONArray signedQuorumList = new JSONArray();
        while (keys.hasNext())
            signedQuorumList.put(keys.next());
        APIResponse.put("tid", tid);
        APIResponse.put("status", "Success");
        APIResponse.put("did", senderDidIpfsHash);
        APIResponse.put("message", "Block Committed Successfully");
        APIResponse.put("quorumlist", signedQuorumList);
        APIResponse.put("blockHash", blockHash);
        APIResponse.put("totaltime", totalTime);

        updateQuorum(quorumArray, signedQuorumList, true, type);

        JSONObject transactionRecord = new JSONObject();
        transactionRecord.put("role", "Sender");
        // transactionRecord.put("tokens", tokens);
        transactionRecord.put("txn", tid);
        transactionRecord.put("quorumList", signedQuorumList);
        transactionRecord.put("senderDID", senderDidIpfsHash);
        transactionRecord.put("blockHash", blockHash);
        transactionRecord.put("Date", getCurrentUtcTime());
        transactionRecord.put("totalTime", totalTime);
        transactionRecord.put("comment", comment);
        transactionRecord.put("essentialShare", InitiatorProcedure.essential);

        JSONArray transactionHistoryEntry = new JSONArray();
        transactionHistoryEntry.put(transactionRecord);
        updateJSON("add", WALLET_DATA_PATH + "TransactionHistory.json", transactionHistoryEntry.toString());

        // for (int i = 0; i < tokens.length(); i++)
        //     Files.deleteIfExists(Paths.get(TOKENS_PATH + tokens.get(i)));

        // Populating data to explorer
        // if (!EXPLORER_IP.contains("127.0.0.1")) {
        //     // List<String> tokenList = new ArrayList<>();
        //     // for (int i = 0; i < tokens.length(); i++)
        //     //     tokenList.add(tokens.getString(i));
        //     String url = EXPLORER_IP + "/CreateOrUpdateRubixTransaction";
        //     URL obj = new URL(url);
        //     HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

        //     // Setting basic post request
        //     con.setRequestMethod("POST");
        //     con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        //     con.setRequestProperty("Accept", "application/json");
        //     con.setRequestProperty("Content-Type", "application/json");
        //     con.setRequestProperty("Authorization", "null");

        //     // Serialization
        //     JSONObject dataToSend = new JSONObject();
        //     dataToSend.put("transaction_id", tid);
        //     dataToSend.put("sender_did", senderDidIpfsHash);
        //     dataToSend.put("receiver_did", receiverDidIpfsHash);
        //     dataToSend.put("blockHash", blockHash);
        //     dataToSend.put("token_time", (int) totalTime);
        //     // dataToSend.put("amount", amount);
        //     String populate = dataToSend.toString();

        //     JSONObject jsonObject = new JSONObject();
        //     jsonObject.put("inputString", populate);
        //     String postJsonData = jsonObject.toString();

        //     // Send post request
        //     con.setDoOutput(true);
        //     DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        //     wr.writeBytes(postJsonData);
        //     wr.flush();
        //     wr.close();

        //     int responseCode = con.getResponseCode();
        //     BlockSenderLogger.debug("Sending 'POST' request to URL : " + url);
        //     BlockSenderLogger.debug("Post Data : " + postJsonData);
        //     BlockSenderLogger.debug("Response Code : " + responseCode);

        //     BufferedReader in = new BufferedReader(
        //             new InputStreamReader(con.getInputStream()));
        //     String output;
        //     StringBuffer response = new StringBuffer();

        //     while ((output = in.readLine()) != null) {
        //         response.append(output);
        //     }
        //     in.close();

        //     BlockSenderLogger.debug(response.toString());
        // }

        //
        // if (type==1) {
        // String urlQuorumUpdate = SYNC_IP+"/updateQuorum";
        // URL objQuorumUpdate = new URL(urlQuorumUpdate);
        // HttpURLConnection conQuorumUpdate = (HttpURLConnection)
        // objQuorumUpdate.openConnection();
        //
        // conQuorumUpdate.setRequestMethod("POST");
        // conQuorumUpdate.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        // conQuorumUpdate.setRequestProperty("Accept", "application/json");
        // conQuorumUpdate.setRequestProperty("Content-Type", "application/json");
        // conQuorumUpdate.setRequestProperty("Authorization", "null");
        //
        // JSONObject dataToSendQuorumUpdate = new JSONObject();
        // dataToSendQuorumUpdate.put("completequorum", quorumArray);
        // dataToSendQuorumUpdate.put("signedquorum",signedQuorumList);
        // String populateQuorumUpdate = dataToSendQuorumUpdate.toString();
        //
        // conQuorumUpdate.setDoOutput(true);
        // DataOutputStream wrQuorumUpdate = new
        // DataOutputStream(conQuorumUpdate.getOutputStream());
        // wrQuorumUpdate.writeBytes(populateQuorumUpdate);
        // wrQuorumUpdate.flush();
        // wrQuorumUpdate.close();
        //
        // int responseCodeQuorumUpdate = conQuorumUpdate.getResponseCode();
        // BlockSenderLogger.debug("Sending 'POST' request to URL : " +
        // urlQuorumUpdate);
        // BlockSenderLogger.debug("Post Data : " + populateQuorumUpdate);
        // BlockSenderLogger.debug("Response Code : " + responseCodeQuorumUpdate);
        //
        // BufferedReader inQuorumUpdate = new BufferedReader(
        // new InputStreamReader(conQuorumUpdate.getInputStream()));
        // String outputQuorumUpdate;
        // StringBuffer responseQuorumUpdate = new StringBuffer();
        // while ((outputQuorumUpdate = inQuorumUpdate.readLine()) != null) {
        // responseQuorumUpdate.append(outputQuorumUpdate);
        // }
        // inQuorumUpdate.close();
        //
        // }

        BlockSenderLogger.info("Transaction Successful");
        // executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
        output.close();
        input.close();
        senderSocket.close();
        senderMutex = false;
        return APIResponse;

    }
}
