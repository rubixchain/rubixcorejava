package com.rubix.TokenTransfer;

import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Consensus.InitiatorConsensus;

import com.rubix.Consensus.InitiatorProcedure;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
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
import java.util.ArrayList;
import java.util.Date;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;


public class TokenSender {
    private static final Logger TokenSenderLogger = Logger.getLogger(TokenSender.class);
    private static PrintStream output;
    private static BufferedReader input;
    private static Socket senderSocket;
    private static boolean senderMutex = false, consensusStatus = false;
    private static ArrayList quorumPeersList;

    /**
     *  A sender node to transfer tokens
     * @param data Details required for tokenTransfer
     * @param ipfs IPFS instance
     * @param port Sender port for communication
     * @return Transaction Details (JSONObject)
     * @throws IOException handles IO Exceptions
     * @throws JSONException handles JSON Exceptions
     * @throws NoSuchAlgorithmException handles No Such Algorithm Exceptions
     */
    public static JSONObject Send(String data, IPFS ipfs, int port) throws IOException, JSONException {

        JSONObject APIResponse = new JSONObject();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONObject detailsObject = new JSONObject(data);
        String receiverDidIpfsHash = detailsObject.getString("receiverDidIpfsHash");
        String pvt = detailsObject.getString("pvt");
        String comment = detailsObject.getString("comment");
        JSONArray tokens = detailsObject.getJSONArray("tokens");
        JSONArray tokenHeader = detailsObject.getJSONArray("tokenHeader");

        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);

        BufferedImage senderWidImage = ImageIO.read(new File(DATA_PATH + senderDidIpfsHash + "/PublicShare.png"));
        String senderWidBin = PropImage.img2bin(senderWidImage);

        String receiverPeerId = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", receiverDidIpfsHash);
        String receiverWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash", receiverDidIpfsHash);
        nodeData(receiverDidIpfsHash, receiverWidIpfsHash, ipfs);
        BufferedImage receiverWidImage = ImageIO.read(new File(DATA_PATH + receiverDidIpfsHash + "/PublicShare.png"));
        String receiverWidBin = PropImage.img2bin(receiverWidImage);


        JSONObject quorumDidObject = null;
        if (CONSENSUS_STATUS) {
            quorumDidObject = QUORUM_MEMBERS;
            quorumPeersList = QuorumCheck(quorumDidObject, ipfs);
            if (quorumPeersList == null) {
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Quorum Members not available");
                TokenSenderLogger.warn("Quorum Members not available");
                return APIResponse;
            } else
                consensusStatus = true;
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
            executeIPFSCommands("ipfs p2p close -t /ipfs/" + receiverPeerId);
            senderMutex = false;
            return APIResponse;
        } else {
            for (int i = 0; i < tokens.length(); i++) {
                add(TOKENS_PATH + tokens.get(i), ipfs);
                String tokenChainHash = add(TOKENCHAIN_PATH + tokens.get(i) + ".json", ipfs);
                allTokensChainsPushed.add(tokenChainHash);
            }

            String authSenderByRecHash = calculateHash(tokens.toString() + allTokensChainsPushed.toString() + receiverWidBin + comment, "SHA3-256");
            String tid = calculateHash(authSenderByRecHash, "SHA3-256");
            TokenSenderLogger.debug("Sender by Receiver Hash " + authSenderByRecHash);
            TokenSenderLogger.debug("TID on sender " + tid);
            String senderSign = getSignFromShares(pvt, authSenderByRecHash);

            JSONObject senderDetails2Receiver = new JSONObject();
            senderDetails2Receiver.put("sign", senderSign);
            senderDetails2Receiver.put("tid", tid);
            senderDetails2Receiver.put("comment", comment);

            JSONObject tokenDetails = new JSONObject();
            tokenDetails.put("token", tokens);
            tokenDetails.put("tokenChain", allTokensChainsPushed);
            tokenDetails.put("tokenHeader", tokenHeader);

            DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
            Date date = new Date();
            LocalDate currentTime = LocalDate.parse(formatter.format(date).replace("/", "-"));

            swarmConnect(receiverPeerId, ipfs);
            forward(receiverPeerId, port, receiverPeerId);

            TokenSenderLogger.debug("Forwarded to " + receiverPeerId + " on " + port);
            senderSocket = new Socket("127.0.0.1", port);

            input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
            output = new PrintStream(senderSocket.getOutputStream());

            long startTime = System.currentTimeMillis();

            output.println(senderPeerID);
            while ((peerAuth = input.readLine()) == null) {
                forward(receiverPeerId, port, receiverPeerId);
                senderSocket = new Socket("127.0.0.1", port);
                input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
                output = new PrintStream(senderSocket.getOutputStream());
                output.println(senderPeerID);
            }

            if (!peerAuth.equals("200")) {
                executeIPFSCommands(" ipfs p2p close -t /ipfs/" + receiverPeerId);
                TokenSenderLogger.info("Sender Data Not Available");
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender Data Not Available");
                return APIResponse;

            } else
                output.println(tokenDetails);

            String tokenAuth = input.readLine();
            if (!tokenAuth.equals("200")) {
                executeIPFSCommands(" ipfs p2p close -t /ipfs/" + receiverPeerId);
                TokenSenderLogger.info("Tokens Not Verified");
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Tokens Not Verified");
                return APIResponse;

            } else
                output.println(senderDetails2Receiver);

            TokenSenderLogger.debug("status of consensus : " + consensusStatus);
            if (consensusStatus) {
                String message = senderWidBin + tokens;

                JSONObject dataObject = new JSONObject();
                dataObject.put("tid", tid);
                dataObject.put("message", message);
                dataObject.put("receiverDidIpfs", receiverDidIpfsHash);
                dataObject.put("pvt", pvt);
                dataObject.put("senderDidIpfs", senderDidIpfsHash);
                dataObject.put("token", tokens.toString());
                dataObject.put("quorumlist", quorumPeersList);

                InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 3);
                TokenSenderLogger.debug("length on sender " + InitiatorConsensus.quorumSignature.length() + "response count " + InitiatorConsensus.quorumResponse);
                if (!(InitiatorConsensus.quorumResponse > minQuorum())) {
                    TokenSenderLogger.debug("Consensus Failed");
                    output.println("Consensus failed");
                    executeIPFSCommands(" ipfs p2p close -t /ipfs/" + receiverPeerId);
                    output.close();
                    input.close();
                    senderSocket.close();
                    senderMutex = false;
                    APIResponse.put("did", senderDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Transaction declined by Quorum");
                    return APIResponse;

                } else {
                    TokenSenderLogger.debug("Consensus Reached");
                    output.println("Consensus Reached");
                    TokenSenderLogger.debug("Quorum Signatures length " + InitiatorConsensus.quorumSignature.length());
                    output.println(InitiatorConsensus.quorumSignature);
                }
            } else {
                output.println("No Consensus");
            }
            String signatureAuth = input.readLine();

            long endAuth = System.currentTimeMillis();
            long totalTime = endAuth - startTime;
            long startUnpin = System.currentTimeMillis();
            if (!signatureAuth.equals("200")) {
                executeIPFSCommands(" ipfs p2p close -t /ipfs/" + receiverPeerId);
                TokenSenderLogger.info("Authentication Failed");
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender not authenticated");
                return APIResponse;

            } else {
                for (int i = 0; i < tokens.length(); i++)
                    unpin(String.valueOf(tokens.get(i)), ipfs);

                repo(ipfs);
                if (!EXPLORER_IP.contains("127.0.0.1")) {
                    URL obj = new URL(EXPLORER_IP);
                    JSONObject dataToSend = new JSONObject();
                    dataToSend.put("transaction_id", tid);
                    dataToSend.put("sender_did", senderDidIpfsHash);
                    dataToSend.put("receiver_did", receiverDidIpfsHash);
                    dataToSend.put("token_id", tokens.toString());
                    dataToSend.put("total_time", totalTime);
                    String populate = dataToSend.toString();

                    HttpURLConnection con = (HttpURLConnection) obj.openConnection();

                    con.setRequestMethod("POST");
                    con.setRequestProperty("User-Agent", "signer");
                    con.setRequestProperty("Content-Type", "application/json");

                    con.setDoOutput(true);

                    DataOutputStream wr = new DataOutputStream(con.getOutputStream());
                    wr.writeBytes(populate);
                    wr.flush();
                    wr.close();
                    int responseCode = con.getResponseCode();
                    TokenSenderLogger.debug("Sending 'POST' request to URL : " + EXPLORER_IP);
                    TokenSenderLogger.debug("Post Data : " + populate);
                    TokenSenderLogger.info("Response code " + responseCode);
                }

            }
            TokenSenderLogger.debug("Unpinned Tokens");
            output.println("Unpinned");

            String confirmation = input.readLine();
            if (!confirmation.equals("Successfully Pinned")) {
                TokenSenderLogger.warn("Multiple Owners for the token");
                executeIPFSCommands(" ipfs p2p close -t /ipfs/" + receiverPeerId);
                TokenSenderLogger.info("Tokens with multiple pins");
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Tokens with multiple pins");
                return APIResponse;

            } else {
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Success");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("message", "Tokens transferred successfully!");

                output.println(InitiatorProcedure.essential);
                String respAuth = input.readLine();

                if (!respAuth.equals("Send Response")) {
                    executeIPFSCommands(" ipfs p2p close -t /ipfs/" + receiverPeerId);
                    output.close();
                    input.close();
                    senderSocket.close();
                    senderMutex = false;
                    APIResponse.put("did", senderDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Receiver process not over");
                    TokenSenderLogger.info("Incomplete Transaction");
                    return APIResponse;

                } else {

                    JSONObject transactionRecord = new JSONObject();
                    transactionRecord.put("role", "Sender");
                    transactionRecord.put("tokens", tokens);
                    transactionRecord.put("txn", tid);
                    transactionRecord.put("quorumList", quorumDidObject);
                    transactionRecord.put("senderDID", senderDidIpfsHash);
                    transactionRecord.put("receiverDID", receiverDidIpfsHash);
                    transactionRecord.put("Date", currentTime);
                    transactionRecord.put("totalTime", totalTime);
                    transactionRecord.put("comment", comment);
                    transactionRecord.put("essentialShare", InitiatorProcedure.essential);


                    JSONArray transactionHistoryEntry = new JSONArray();
                    transactionHistoryEntry.put(transactionRecord);
                    updateJSON("add", WALLET_DATA_PATH + "TransactionHistory.json", transactionHistoryEntry.toString());

                    for (int i = 0; i < tokens.length(); i++)
                        Files.deleteIfExists(Paths.get(TOKENS_PATH + tokens.get(i)));
                    TokenSenderLogger.info("Transaction Successful");

                }

            }
        }
        executeIPFSCommands(" ipfs p2p close -t /ipfs/" + receiverPeerId);
        output.close();
        input.close();
        senderSocket.close();
        senderMutex = false;
        return APIResponse;

    }
}
