package com.rubix.TokenTransfer;

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

    private static ArrayList alphaPeersList;
    private static ArrayList betaPeersList;
    private static ArrayList gammaPeersList;

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
        JSONArray betaQuorum=new JSONArray();
        JSONArray gammaQuorum=new JSONArray();



        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);

        BufferedImage senderWidImage = ImageIO.read(new File(DATA_PATH + senderDidIpfsHash + "/PublicShare.png"));
        String senderWidBin = PropImage.img2bin(senderWidImage);


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
            File tokenchain = new File(TOKENCHAIN_PATH+tokens.get(i)+".json");
            TokenSenderLogger.debug(token +"and " +tokenchain);
            if (!(token.exists()&&tokenchain.exists())) {
                TokenSenderLogger.info("Tokens Not Verified");
                senderMutex = false;
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Invalid token(s)");
                return APIResponse;

            }
            add(TOKENS_PATH + tokens.get(i), ipfs);
            String tokenChainHash = add(TOKENCHAIN_PATH + tokens.get(i) + ".json", ipfs);
            allTokensChainsPushed.add(tokenChainHash);
        }

        String authSenderByRecHash = calculateHash(tokens.toString() + allTokensChainsPushed.toString() + receiverDidIpfsHash + comment, "SHA3-256");
        String tid = calculateHash(authSenderByRecHash, "SHA3-256");
        TokenSenderLogger.debug("Sender by Receiver Hash " + authSenderByRecHash);
        TokenSenderLogger.debug("TID on sender " + tid);

        writeToFile(LOGGER_PATH+"tempbeta", tid.concat(senderDidIpfsHash), false);
        String betaHash = IPFSNetwork.add(LOGGER_PATH+"tempbeta", ipfs);
        deleteFile(LOGGER_PATH+"tempbeta");

        writeToFile(LOGGER_PATH+"tempgamma", tid.concat(receiverDidIpfsHash), false);
        String gammaHash = IPFSNetwork.add(LOGGER_PATH+"tempgamma", ipfs);
        deleteFile(LOGGER_PATH+"tempgamma");


            switch (type) {
                case 1: {
                    quorumArray = getQuorum(betaHash,gammaHash,senderDidIpfsHash,receiverDidIpfsHash,tokens.length());
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

            QuorumSwarmConnect(quorumArray,ipfs);

        for(int i=0;i<7;i++)
        {
            alphaQuorum.put(quorumArray.getString(i));
            betaQuorum.put(quorumArray.getString(7+i));
            gammaQuorum.put(quorumArray.getString(14+i));
        }


        TokenSenderLogger.debug("alphaquorum " + alphaQuorum + " size " +alphaQuorum.length());
        TokenSenderLogger.debug("betaquorum "+betaQuorum + " size "+betaQuorum.length());
        TokenSenderLogger.debug("gammaquorum "+gammaQuorum + " size "+gammaQuorum.length());

            alphaPeersList=QuorumCheck(alphaQuorum,ipfs);
            betaPeersList= QuorumCheck(betaQuorum,ipfs);
            gammaPeersList=QuorumCheck(gammaQuorum,ipfs);

           // quorumPeersList = QuorumCheck(quorumArray, ipfs);

            if (alphaPeersList.size()<5||betaPeersList.size()<5||gammaPeersList.size()<5) {
                updateQuorum(quorumArray,null,false,type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Quorum Members not available");
                TokenSenderLogger.warn("Quorum Members not available");
                return APIResponse;
            }

            String senderSign = getSignFromShares(pvt, authSenderByRecHash);

            JSONObject senderDetails2Receiver = new JSONObject();
            senderDetails2Receiver.put("sign", senderSign);
            senderDetails2Receiver.put("tid", tid);
            senderDetails2Receiver.put("comment", comment);

        JSONObject tokenDetails = new JSONObject();
        tokenDetails.put("token", tokens);
        tokenDetails.put("tokenChain", allTokensChainsPushed);
        tokenDetails.put("tokenHeader", tokenHeader);

        String message = tokenDetails.toString();

        String consensusID = calculateHash(message , "SHA3-256");
        writeToFile(LOGGER_PATH+"consensusID", consensusID, false);
        String consensusIDIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH+"consensusID", ipfs);
        deleteFile(LOGGER_PATH+"consensusID");

        TokenSenderLogger.debug("consensusID hash " + consensusIDIPFSHash + " unique own " + dhtEmpty(consensusIDIPFSHash,ipfs));


            DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
            Date date = new Date();

            LocalDate currentTime = LocalDate.parse(formatter.format(date).replace("/", "-"));
            receiverPeerId = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", receiverDidIpfsHash);

            TokenSenderLogger.debug("Swarm connecting to " + receiverPeerId);
            swarmConnect(receiverPeerId, ipfs);
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

            peerAuth= input.readLine();


//            while ((peerAuth = input.readLine()) == null) {
////                forward(receiverPeerId, port, receiverPeerId);
////                senderSocket = new Socket("127.0.0.1", port);
////                input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
////                output = new PrintStream(senderSocket.getOutputStream());
////                output.println(senderPeerID);
//            }
            if (!peerAuth.equals("200")) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                TokenSenderLogger.info("Sender Data Not Available");
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                updateQuorum(quorumArray,null,false,type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender Data Not Available");
                return APIResponse;

            }

        output.println(tokenDetails);

            String tokenAuth = input.readLine();

            if (!tokenAuth.equals("200")) {

                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);

                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                if (tokenAuth.equals("421")) {
                    TokenSenderLogger.info("Tokens Not Verified");
                    APIResponse.put("message", "Tokens Not Verified");
                }
                else {
                    TokenSenderLogger.info("Consensus ID not unique");
                    APIResponse.put("message", "Consensus ID not unique");
                }
                updateQuorum(quorumArray,null,false,type);
                    APIResponse.put("did", senderDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Failed");

                return APIResponse;

            }

               // output.println(senderDetails2Receiver);


                JSONObject dataObject = new JSONObject();
                dataObject.put("tid", tid);
                dataObject.put("message", message);
                dataObject.put("receiverDidIpfs", receiverDidIpfsHash);
                dataObject.put("pvt", pvt);
                dataObject.put("senderDidIpfs", senderDidIpfsHash);
                dataObject.put("token", tokens.toString());
                dataObject.put("alphaList", alphaPeersList);
                dataObject.put("betaList", betaPeersList);
                dataObject.put("gammaList", gammaPeersList);

                TokenSenderLogger.debug("dataobject " + dataObject.toString());

                InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 3);
                TokenSenderLogger.debug("length on sender " + InitiatorConsensus.quorumSignature.length() + "response count " + InitiatorConsensus.quorumResponse);
                if (!(InitiatorConsensus.quorumSignature.length() >= 3 * minQuorum(7))) {

                    //  if (!(InitiatorProcedure.alphaReply.length() >= minQuorum(7))) {
                    TokenSenderLogger.debug("Consensus Failed");
                    output.println("Consensus failed");
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                    output.close();
                    input.close();
                    senderSocket.close();
                    senderMutex = false;
                    updateQuorum(quorumArray,null,false,type);
                    APIResponse.put("did", senderDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Transaction declined by Quorum");
                    return APIResponse;

                }

                    TokenSenderLogger.debug("Consensus Reached");
                senderDetails2Receiver.put("status","Consensus Reached");
                senderDetails2Receiver.put("quorumsign",InitiatorConsensus.quorumSignature.toString());

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
                updateQuorum(quorumArray,null,false,type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender not authenticated");
                return APIResponse;

            }

                for (int i = 0; i < tokens.length(); i++)
                    unpin(String.valueOf(tokens.get(i)), ipfs);

                repo(ipfs);

            TokenSenderLogger.debug("Unpinned Tokens");
            output.println("Unpinned");

            String confirmation = input.readLine();
            if (!confirmation.equals("Successfully Pinned")) {
                TokenSenderLogger.warn("Multiple Owners for the token");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                TokenSenderLogger.info("Tokens with multiple pins");
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                updateQuorum(quorumArray,null,false,type);
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
                    updateQuorum(quorumArray,null,false,type);
                    APIResponse.put("did", senderDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Receiver process not over");
                    TokenSenderLogger.info("Incomplete Transaction");
                    return APIResponse;

                }

                    Iterator<String> keys = InitiatorConsensus.quorumSignature.keys();
                    JSONArray signedQuorumList = new JSONArray();
                    while(keys.hasNext())
                        signedQuorumList.put(keys.next());
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Success");
                    APIResponse.put("did", senderDidIpfsHash);
                    APIResponse.put("message", "Tokens transferred successfully!");
                    APIResponse.put("quorumlist",signedQuorumList);
                    APIResponse.put("receiver",receiverDidIpfsHash);
                    APIResponse.put("totaltime",totalTime);

                    updateQuorum(quorumArray,signedQuorumList,true,type);

                    JSONObject transactionRecord = new JSONObject();
                    transactionRecord.put("role", "Sender");
                    transactionRecord.put("tokens", tokens);
                    transactionRecord.put("txn", tid);
                    transactionRecord.put("quorumList",signedQuorumList);
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
                        String url = EXPLORER_IP+"/CreateOrUpdateRubixTransaction";
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
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                    output.close();
                    input.close();
                    senderSocket.close();
                    senderMutex = false;
                    return APIResponse;

    }
}
