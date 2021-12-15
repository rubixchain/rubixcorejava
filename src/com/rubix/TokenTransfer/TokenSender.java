package com.rubix.TokenTransfer;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Consensus.InitiatorConsensus;

import com.rubix.Consensus.InitiatorProcedure;
import com.rubix.Consensus.QuorumConsensus;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;


public class TokenSender {
    private static final Logger TokenSenderLogger = Logger.getLogger(TokenSender.class);
    private static final String USER_AGENT = "Mozilla/5.0";
    public static BufferedReader serverInput;
    private static PrintStream output;
    private static BufferedReader input;
    private static Socket senderSocket;
    private static boolean senderMutex = false;
//    private static int heartBeatAlpha=0;
//    private static int heartBeatBeta=0;
//    private static int heartBeatGamma=0;
//    private static int alphaSize=0;
//
//    private static ArrayList alphaPeersList;
//    private static ArrayList betaPeersList;
//    private static ArrayList gammaPeersList;

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
        String receiverPeerId;
        JSONObject detailsObject = new JSONObject(data);
        String receiverDidIpfsHash = detailsObject.getString("receiverDidIpfsHash");
        String pvt = detailsObject.getString("pvt");
        int amount = detailsObject.getInt("amount");
        int type = detailsObject.getInt("type");
        String comment = detailsObject.getString("comment");
        JSONArray tokens = detailsObject.getJSONArray("tokens");
        JSONArray tokenHeader = detailsObject.getJSONArray("tokenHeader");
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
        TokenSenderLogger.debug("sender peer id" + senderPeerID);
        String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);
        TokenSenderLogger.debug("sender did ipfs hash" + senderDidIpfsHash);
        TokenSenderLogger.debug("path is" + DATA_PATH + senderDidIpfsHash);
        File folder = new File(DATA_PATH + senderDidIpfsHash);
        File[] listOfFiles = folder.listFiles();
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                System.out.println("File " + listOfFiles[i].getName());
            } else if (listOfFiles[i].isDirectory()) {
                System.out.println("Directory " + listOfFiles[i].getName());
            }
        }


        if (senderMutex) {
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Sender busy. Try again later");
            TokenSenderLogger.warn("Sender busy");
            return APIResponse;
        }

        senderMutex = true;

        String peerAuth;
        ArrayList<String> allTokensChainsPushed = new ArrayList();
        APIResponse = new JSONObject();
        if (tokens.length() == 0) {

            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "No balance");
            senderMutex = false;
            return APIResponse;
        }

        for (int i = 0; i < tokens.length(); i++) {
            File token = new File(TOKENS_PATH + tokens.get(i));
            File tokenchain = new File(TOKENCHAIN_PATH + tokens.get(i) + ".json");
            TokenSenderLogger.debug(token + "and " + tokenchain);
            if (!(token.exists() && tokenchain.exists())) {
                TokenSenderLogger.info("Tokens Not Verified");
                senderMutex = false;
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Invalid token(s)");
                return APIResponse;

            }
            String hash = add(TOKENS_PATH + tokens.get(i), ipfs);
            pin(hash, ipfs);
            String tokenChainHash = add(TOKENCHAIN_PATH + tokens.get(i) + ".json", ipfs);
            allTokensChainsPushed.add(tokenChainHash);
        }

        String authSenderByRecHash = calculateHash(tokens.toString() + allTokensChainsPushed.toString() + receiverDidIpfsHash + comment, "SHA3-256");
        String tid = calculateHash(authSenderByRecHash, "SHA3-256");
        TokenSenderLogger.debug("Sender by Receiver Hash " + authSenderByRecHash);
        TokenSenderLogger.debug("TID on sender " + tid);

        writeToFile(LOGGER_PATH + "tempbeta", tid.concat(senderDidIpfsHash), false);
        String betaHash = IPFSNetwork.add(LOGGER_PATH + "tempbeta", ipfs);
        deleteFile(LOGGER_PATH + "tempbeta");

        writeToFile(LOGGER_PATH + "tempgamma", tid.concat(receiverDidIpfsHash), false);
        String gammaHash = IPFSNetwork.add(LOGGER_PATH + "tempgamma", ipfs);
        deleteFile(LOGGER_PATH + "tempgamma");


        switch (type) {
            case 1: {
                quorumArray = getQuorum(betaHash, gammaHash, senderDidIpfsHash, receiverDidIpfsHash, tokens.length());
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
                TokenSenderLogger.error("Unknown quorum type input, cancelling transaction");
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

        TokenSenderLogger.debug("alphaquorum " + alphaQuorum + " size " + alphaQuorum.length());
        TokenSenderLogger.debug("betaquorum " + betaQuorum + " size " + betaQuorum.length());
        TokenSenderLogger.debug("gammaquorum " + gammaQuorum + " size " + gammaQuorum.length());


        alphaPeersList = QuorumCheck(alphaQuorum, alphaSize);
        betaPeersList = QuorumCheck(betaQuorum, 7);
        gammaPeersList = QuorumCheck(gammaQuorum, 7);

//        for(int i=0;i<alphaPeersList.size();i++) {
//            heartBeatAlpha += checkHeartBeat(alphaPeersList.get(i).toString(), alphaPeersList.get(i).toString() + "alpha");
//        }
//
//        for(int i=0;i<betaPeersList.size();i++) {
//            heartBeatBeta += checkHeartBeat(betaPeersList.get(i).toString(), betaPeersList.get(i).toString() + "beta");
//        }
//        for(int i=0;i<gammaPeersList.size();i++) {
//            heartBeatGamma += checkHeartBeat(gammaPeersList.get(i).toString(), gammaPeersList.get(i).toString() + "gamma");
//        }


        TokenSenderLogger.debug("alphaPeersList size " + alphaPeersList.size());
        TokenSenderLogger.debug("betaPeersList size " + betaPeersList.size());
        TokenSenderLogger.debug("gammaPeersList size " + gammaPeersList.size());
//        TokenSenderLogger.debug("heartBeatAlpha size "+ heartBeatAlpha);
//        TokenSenderLogger.debug("heartBeatBeta size "+ heartBeatBeta);
//        TokenSenderLogger.debug("heartBeatGamma size "+ heartBeatGamma);
        TokenSenderLogger.debug("minQuorumAlpha size " + minQuorum(alphaSize));

        // quorumPeersList = QuorumCheck(quorumArray, ipfs);

        //  if (alphaPeersList.size()<minQuorum(alphaSize)||betaPeersList.size()<5||gammaPeersList.size()<5 || heartBeatAlpha<minQuorum(alphaSize)||heartBeatBeta<5||heartBeatGamma<5) {

        if (alphaPeersList.size() < minQuorum(alphaSize) || betaPeersList.size() < 5 || gammaPeersList.size() < 5) {
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Quorum Members not available");
            TokenSenderLogger.warn("Quorum Members not available");
            senderMutex = false;
            return APIResponse;
        }


        String senderSign = getSignFromShares(pvt, authSenderByRecHash);

        JSONObject senderDetails2Receiver = new JSONObject();
        senderDetails2Receiver.put("sign", senderSign);
        senderDetails2Receiver.put("tid", tid);
        senderDetails2Receiver.put("comment", comment);

        JSONArray tokenBindDetailsArray = new JSONArray();
        JSONObject tokenDetails = new JSONObject();
        tokenDetails.put("token", tokens);
        tokenDetails.put("tokenChain", allTokensChainsPushed);
        tokenDetails.put("tokenHeader", tokenHeader);
        tokenDetails.put("sender", senderDidIpfsHash);

        String senderToken = tokenDetails.toString();

        String consensusID = calculateHash(senderToken, "SHA3-256");
        writeToFile(LOGGER_PATH + "consensusID", consensusID, false);
        String consensusIDIPFSHash = IPFSNetwork.add(LOGGER_PATH + "consensusID", ipfs);
        pin(consensusIDIPFSHash,ipfs);
        deleteFile(LOGGER_PATH + "consensusID");

        JSONObject ipfsObject = new JSONObject();
        ipfsObject.put("ipfsHash", consensusIDIPFSHash);
        tokenBindDetailsArray.put(tokenDetails);
        tokenBindDetailsArray.put(ipfsObject);

        TokenSenderLogger.debug("consensusID hash " + consensusIDIPFSHash + " unique own " + dhtEmpty(consensusIDIPFSHash, ipfs));


//        DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
//        Date date = new Date();
//
//        LocalDate currentTime = LocalDate.parse(formatter.format(date).replace("/", "-"));
        receiverPeerId = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", receiverDidIpfsHash);

        TokenSenderLogger.debug("Swarm connecting to " + receiverPeerId);
        swarmConnectP2P(receiverPeerId, ipfs);
        TokenSenderLogger.debug("Swarm connected");

        String receiverWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash", receiverDidIpfsHash);
        nodeData(receiverDidIpfsHash, receiverWidIpfsHash, ipfs);

        forward(receiverPeerId, port, receiverPeerId);

        TokenSenderLogger.debug("Forwarded to " + receiverPeerId + " on " + port);
        senderSocket = new Socket("127.0.0.1", port);

        input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
        output = new PrintStream(senderSocket.getOutputStream());

        long startTime = System.currentTimeMillis();

        output.println(senderPeerID);
        TokenSenderLogger.debug("Sent PeerID");

        peerAuth = input.readLine();

        if (!peerAuth.equals("200")) {
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            TokenSenderLogger.info("Sender Data Not Available");
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

        output.println(tokenBindDetailsArray);

        String tokenAuth = input.readLine();

        if (!tokenAuth.equals("200")) {
            String errorMessage = null;
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);

            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            switch (tokenAuth) {
                case "420":
                    errorMessage = "Consensus ID not unique: Hashes do not match";
                    break;
                case "421":
                    errorMessage = "Consensus ID not unique: More than one provider for consensus ID hash";
                    break;
                case "422":
                    errorMessage = "Tokens not verified";
                    break;
            }

            TokenSenderLogger.info(errorMessage);
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", errorMessage);

            return APIResponse;

        }

        JSONObject dataObject = new JSONObject();
        dataObject.put("tid", tid);
        dataObject.put("message", consensusIDIPFSHash);
        dataObject.put("receiverDidIpfs", receiverDidIpfsHash);
        dataObject.put("pvt", pvt);
        dataObject.put("senderDidIpfs", senderDidIpfsHash);
        dataObject.put("token", tokens.toString());
        dataObject.put("alphaList", alphaPeersList);
        dataObject.put("betaList", betaPeersList);
        dataObject.put("gammaList", gammaPeersList);

        TokenSenderLogger.debug("dataobject " + dataObject.toString());

        InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 100, alphaSize);
        TokenSenderLogger.debug("length on sender " + InitiatorConsensus.quorumSignature.length() + "response count " + InitiatorConsensus.quorumResponse);
        if (InitiatorConsensus.quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
            //  if (!(InitiatorProcedure.alphaReply.length() >= minQuorum(7))) {
            TokenSenderLogger.debug("Consensus Failed");
            senderDetails2Receiver.put("status", "Consensus Failed");
            senderDetails2Receiver.put("quorumsign", InitiatorConsensus.quorumSignature.toString());
            output.println(senderDetails2Receiver);
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
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

        TokenSenderLogger.debug("Consensus Reached");
        senderDetails2Receiver.put("status", "Consensus Reached");
        senderDetails2Receiver.put("quorumsign", InitiatorConsensus.quorumSignature.toString());

        output.println(senderDetails2Receiver);
        // output.println("Consensus Reached");
        TokenSenderLogger.debug("Quorum Signatures length " + InitiatorConsensus.quorumSignature.length());
        // output.println(InitiatorConsensus.quorumSignature);

        String signatureAuth = input.readLine();

        long endAuth = System.currentTimeMillis();
        long totalTime = endAuth - startTime;
        if (!signatureAuth.equals("200")) {
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            TokenSenderLogger.info("Authentication Failed");
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

        for (int i = 0; i < tokens.length(); i++)
            unpin(String.valueOf(tokens.get(i)), ipfs);

        unpin(consensusIDIPFSHash, ipfs);

        repo(ipfs);

        TokenSenderLogger.debug("Unpinned Tokens");
        output.println("Unpinned");

        String confirmation = input.readLine();
        if (!confirmation.equals("Successfully Pinned")) {
            TokenSenderLogger.warn("Multiple Owners for the token");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);

            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Tokens with multiple pins");
            return APIResponse;

        }
        output.println(InitiatorProcedure.essential);
        String respAuth = input.readLine();

        if (!respAuth.equals("Send Response")) {

            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Receiver process not over");
            TokenSenderLogger.info("Incomplete Transaction");
            return APIResponse;

        }

        Iterator<String> keys = InitiatorConsensus.quorumSignature.keys();
        JSONArray signedQuorumList = new JSONArray();
        while (keys.hasNext())
            signedQuorumList.put(keys.next());
        APIResponse.put("tid", tid);
        APIResponse.put("status", "Success");
        APIResponse.put("did", senderDidIpfsHash);
        APIResponse.put("message", "Tokens transferred successfully!");
        APIResponse.put("quorumlist", signedQuorumList);
        APIResponse.put("receiver", receiverDidIpfsHash);
        APIResponse.put("totaltime", totalTime);

        updateQuorum(quorumArray, signedQuorumList, true, type);

        JSONObject transactionRecord = new JSONObject();
        transactionRecord.put("role", "Sender");
        transactionRecord.put("tokens", tokens);
        transactionRecord.put("txn", tid);
        transactionRecord.put("quorumList", signedQuorumList);
        transactionRecord.put("senderDID", senderDidIpfsHash);
        transactionRecord.put("receiverDID", receiverDidIpfsHash);
        transactionRecord.put("Date", getCurrentUtcTime());
        transactionRecord.put("totalTime", totalTime);
        transactionRecord.put("comment", comment);
        transactionRecord.put("essentialShare", InitiatorProcedure.essential);


        JSONArray transactionHistoryEntry = new JSONArray();
        transactionHistoryEntry.put(transactionRecord);
        updateJSON("add", WALLET_DATA_PATH + "TransactionHistory.json", transactionHistoryEntry.toString());

        for (int i = 0; i < tokens.length(); i++)
            Files.deleteIfExists(Paths.get(TOKENS_PATH + tokens.get(i)));


        //Populating data to explorer
        if (!EXPLORER_IP.contains("127.0.0.1")) {
            List<String> tokenList = new ArrayList<>();
            for (int i = 0; i < tokens.length(); i++)
                tokenList.add(tokens.getString(i));
            String url = EXPLORER_IP + "/CreateOrUpdateRubixTransaction";
            URL obj = new URL(url);
            HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

            // Setting basic post request
            con.setRequestMethod("POST");
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "null");

            // Serialization
            JSONObject dataToSend = new JSONObject();
            dataToSend.put("transaction_id", tid);
            dataToSend.put("sender_did", senderDidIpfsHash);
            dataToSend.put("receiver_did", receiverDidIpfsHash);
            dataToSend.put("token_id", tokenList);
            dataToSend.put("token_time", (int) totalTime);
            dataToSend.put("amount", amount);
            String populate = dataToSend.toString();

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("inputString", populate);
            String postJsonData = jsonObject.toString();

            // Send post request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(postJsonData);
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();
            TokenSenderLogger.debug("Sending 'POST' request to URL : " + url);
            TokenSenderLogger.debug("Post Data : " + postJsonData);
            TokenSenderLogger.debug("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String output;
            StringBuffer response = new StringBuffer();

            while ((output = in.readLine()) != null) {
                response.append(output);
            }
            in.close();

            TokenSenderLogger.debug(response.toString());
        }

//
//        if (type==1) {
//                String urlQuorumUpdate = SYNC_IP+"/updateQuorum";
//                URL objQuorumUpdate = new URL(urlQuorumUpdate);
//                HttpURLConnection conQuorumUpdate = (HttpURLConnection) objQuorumUpdate.openConnection();
//
//                conQuorumUpdate.setRequestMethod("POST");
//                conQuorumUpdate.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
//                conQuorumUpdate.setRequestProperty("Accept", "application/json");
//                conQuorumUpdate.setRequestProperty("Content-Type", "application/json");
//                conQuorumUpdate.setRequestProperty("Authorization", "null");
//
//                JSONObject dataToSendQuorumUpdate = new JSONObject();
//                dataToSendQuorumUpdate.put("completequorum", quorumArray);
//                dataToSendQuorumUpdate.put("signedquorum",signedQuorumList);
//                String populateQuorumUpdate = dataToSendQuorumUpdate.toString();
//
//                conQuorumUpdate.setDoOutput(true);
//                DataOutputStream wrQuorumUpdate = new DataOutputStream(conQuorumUpdate.getOutputStream());
//                wrQuorumUpdate.writeBytes(populateQuorumUpdate);
//                wrQuorumUpdate.flush();
//                wrQuorumUpdate.close();
//
//                int responseCodeQuorumUpdate = conQuorumUpdate.getResponseCode();
//                TokenSenderLogger.debug("Sending 'POST' request to URL : " + urlQuorumUpdate);
//                TokenSenderLogger.debug("Post Data : " + populateQuorumUpdate);
//                TokenSenderLogger.debug("Response Code : " + responseCodeQuorumUpdate);
//
//                BufferedReader inQuorumUpdate = new BufferedReader(
//                        new InputStreamReader(conQuorumUpdate.getInputStream()));
//                String outputQuorumUpdate;
//                StringBuffer responseQuorumUpdate = new StringBuffer();
//                while ((outputQuorumUpdate = inQuorumUpdate.readLine()) != null) {
//                    responseQuorumUpdate.append(outputQuorumUpdate);
//                }
//                inQuorumUpdate.close();
//
//        }

        TokenSenderLogger.info("Transaction Successful");
        System.out.println("Verify Count: " + Authenticate.verifyCount);
        Authenticate.verifyCount = 0;
        executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
        output.close();
        input.close();
        senderSocket.close();
        senderMutex = false;
        return APIResponse;

    }
}
