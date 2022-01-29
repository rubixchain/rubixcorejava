package com.rubix.TokenTransfer;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;


public class TokenReceiver {
    public static Logger TokenReceiverLogger = Logger.getLogger(TokenReceiver.class);

    private static final JSONObject APIResponse = new JSONObject();
    private static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    private static String SenWalletBin;

    /**
     * Receiver Node: To receive a valid token from an authentic sender
     *
     * @return Transaction Details (JSONObject)
     * @throws IOException   handles IO Exceptions
     * @throws JSONException handles JSON Exceptions
     */
    public static String receive() {
        pathSet();
        ServerSocket ss = null;
        Socket sk = null;
        String senderPeerID = null;

        try {

            repo(ipfs);

            int quorumSignVerifyCount = 0;
            JSONObject quorumSignatures = null;

            ArrayList<String> quorumDID = new ArrayList<>();
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

            String receiverPeerID = getPeerID(DATA_PATH + "DID.json");

            String receiverDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", receiverPeerID);

            listen(receiverPeerID, RECEIVER_PORT);
            ss = new ServerSocket(RECEIVER_PORT);
            TokenReceiverLogger.debug("Listening on " + RECEIVER_PORT + " with app name " + receiverPeerID);

            sk = ss.accept();
            BufferedReader input = new BufferedReader(new InputStreamReader(sk.getInputStream()));
            PrintStream output = new PrintStream(sk.getOutputStream());
            long startTime = System.currentTimeMillis();

            try {
                senderPeerID = input.readLine();
            } catch (SocketException e) {
                TokenReceiverLogger.warn("Sender Stream Null - Sender Details");
                APIResponse.put("did", "");
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender Stream Null - Sender Details");

                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();

            }
            syncDataTable(null, senderPeerID);

            swarmConnectP2P(senderPeerID, ipfs);

            String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);
            String senderWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid", senderPeerID);

            if (!(senderDidIpfsHash.contains("Qm") && senderWidIpfsHash.contains("Qm"))) {
                output.println("420");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender details not available in network , please sync");
                TokenReceiverLogger.info("Sender details not available in datatable");
                /* executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);*/

                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }


            nodeData(senderDidIpfsHash, senderWidIpfsHash, ipfs);
            File senderDIDFile = new File(DATA_PATH + senderDidIpfsHash + "/DID.png");
            if (!senderDIDFile.exists()) {
                output.println("420");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender details not available");
                TokenReceiverLogger.info("Sender details not available");
                /* executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);*/

                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            TokenReceiverLogger.debug("Sender details authenticated");
            output.println("200");

            String tokenDetails;
            try {
                tokenDetails = input.readLine();
            } catch (SocketException e) {
                TokenReceiverLogger.warn("Sender Stream Null - Token Details");
                APIResponse.put("did", "");
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender Stream Null - Token Details");

                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();

            }
            JSONObject tokenObject = new JSONObject(tokenDetails);
            JSONObject TokenDetails = tokenObject.getJSONObject("tokenDetails");
            JSONArray tokens = TokenDetails.getJSONArray("token");
            JSONArray tokenChains = TokenDetails.getJSONArray("tokenChain");
            JSONArray tokenHeader = TokenDetails.getJSONArray("tokenHeader");
            int tokenCount = tokens.length();

            JSONArray previousSendersArray = tokenObject.getJSONArray("previousSender");
            JSONArray doubleSpentToken = new JSONArray();
            boolean tokenOwners = true;

            ArrayList ownersArray = new ArrayList();
            ArrayList previousSender = new ArrayList();
            JSONArray ownersReceived = new JSONArray();
            for (int i = 0; i < tokens.length(); ++i) {
                try {
                    TokenReceiverLogger.debug("Checking owners for " + tokens.getString(i) + " Please wait...");
                    ownersArray = IPFSNetwork.dhtOwnerCheck(tokens.getString(i));

                    if (ownersArray.size() > 2) {

                        for (int j = 0; j < previousSendersArray.length(); j++) {
                            if (previousSendersArray.getJSONObject(j).getString("token").equals(tokens.getString(i)))
                                ownersReceived = previousSendersArray.getJSONObject(j).getJSONArray("sender");
                        }

                        for (int j = 0; j < ownersReceived.length(); j++) {
                            previousSender.add(ownersReceived.getString(j));
                        }
                        TokenReceiverLogger.debug("Previous Owners: " + previousSender);

                        for (int j = 0; j < ownersArray.size(); j++) {
                            if (!previousSender.contains(ownersArray.get(j).toString()))
                                tokenOwners = false;
                        }
                    }
                } catch (IOException e) {

                    TokenReceiverLogger.debug("Ipfs dht find did not execute");
                }
            }

            String senderToken = TokenDetails.toString();
            String consensusID = calculateHash(senderToken, "SHA3-256");
            writeToFile(LOGGER_PATH + "consensusID", consensusID, false);
            String consensusIDIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH + "consensusID", ipfs);
            deleteFile(LOGGER_PATH + "consensusID");

            //Check IPFS get for all Tokens
            int ipfsGetFlag = 0;
            ArrayList<String> allTokenContent = new ArrayList<>();
            ArrayList<String> allTokenChainContent = new ArrayList<>();
            for (int i = 0; i < tokenCount; i++) {
                String TokenChainContent = get(tokenChains.getString(i), ipfs);
                allTokenChainContent.add(TokenChainContent);
                String TokenContent = get(tokens.getString(i), ipfs);
                allTokenContent.add(TokenContent);
                ipfsGetFlag++;
            }
            repo(ipfs);

            if (!tokenOwners) {
                JSONArray owners = new JSONArray();
                for (int i = 0; i < ownersArray.size(); i++)
                    owners.put(ownersArray.get(i).toString());
                TokenReceiverLogger.debug("Multiple Owners for " + doubleSpentToken);
                TokenReceiverLogger.debug("Owners: " + owners);
                output.println("420");
                output.println(doubleSpentToken.toString());
                output.println(owners.toString());
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Multiple Owners for " + doubleSpentToken + " " + owners);
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            if (!IPFSNetwork.dhtEmpty(consensusIDIPFSHash, ipfs)) {
                TokenReceiverLogger.debug("consensus ID not unique" + consensusIDIPFSHash);
                output.println("421");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Consensus ID not unique");
                TokenReceiverLogger.info("Consensus ID not unique");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            if (!(ipfsGetFlag == tokenCount)) {
                output.println("422");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Tokens not verified");
                TokenReceiverLogger.info("Tokens not verified");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }

            output.println("200");


            String senderDetails;
            try {
                senderDetails = input.readLine();
            } catch (SocketException e) {
                TokenReceiverLogger.warn("Sender Stream Null - Sender Details");
                APIResponse.put("did", "");
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender Stream Null - Sender Details");

                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();

            }
            JSONObject SenderDetails = new JSONObject(senderDetails);
            String senderSignature = SenderDetails.getString("sign");
            String tid = SenderDetails.getString("tid");
            String comment = SenderDetails.getString("comment");
            String Status = SenderDetails.getString("status");
            String QuorumDetails = SenderDetails.getString("quorumsign");

            BufferedImage senderWidImage = ImageIO.read(new File(DATA_PATH + senderDidIpfsHash + "/PublicShare.png"));
            SenWalletBin = PropImage.img2bin(senderWidImage);

            TokenReceiverLogger.debug("Consensus Status:  " + Status);

            TokenReceiverLogger.debug("Verifying Quorum ...  ");
            TokenReceiverLogger.debug("Please wait, this might take a few seconds");

            if (!Status.equals("Consensus Failed")) {
                boolean yesQuorum = false;
                if (Status.equals("Consensus Reached")) {
                    TokenReceiverLogger.debug("Quorum Signatures: " + QuorumDetails);
                    quorumSignatures = new JSONObject(QuorumDetails);
                    int alphaSize = quorumSignatures.length() - 10;
                    String selectQuorumHash = calculateHash(senderToken, "SHA3-256");
                    String verifyQuorumHash = calculateHash(selectQuorumHash.concat(receiverDidIpfsHash), "SHA3-256");
                    TokenReceiverLogger.debug("1: " + selectQuorumHash);
                    TokenReceiverLogger.debug("2: " + receiverDidIpfsHash);
                    TokenReceiverLogger.debug("Quorum Hash on Receiver Side " + verifyQuorumHash);
                    TokenReceiverLogger.debug("Quorum Signatures length : " + quorumSignatures.length());

                    Iterator<String> keys = quorumSignatures.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        quorumDID.add(key);
                    }

                    for (String quorumDidIpfsHash : quorumDID) {
                        syncDataTable(quorumDidIpfsHash, null);
                        String quorumWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash", quorumDidIpfsHash);

                        nodeData(quorumDidIpfsHash, quorumWidIpfsHash, ipfs);
                    }

                    for (int i = 0; i < quorumSignatures.length(); i++) {

                        JSONObject detailsForVerify = new JSONObject();
                        detailsForVerify.put("did", quorumDID.get(i));
                        detailsForVerify.put("hash", verifyQuorumHash);
                        detailsForVerify.put("signature", quorumSignatures.getString(quorumDID.get(i)));
                        boolean val = Authenticate.verifySignature(detailsForVerify.toString());
                        if (val)
                            quorumSignVerifyCount++;
                    }
                    TokenReceiverLogger.debug("Verified Quorum Count " + quorumSignVerifyCount);
                    yesQuorum = quorumSignVerifyCount >= quorumSignatures.length();
                }
                ArrayList<String> allTokensChainsPushed = new ArrayList<>();
                for (int i = 0; i < tokenCount; i++)
                    allTokensChainsPushed.add(tokenChains.getString(i));

                String hash = calculateHash(tokens.toString() + allTokensChainsPushed.toString() + receiverDidIpfsHash + comment, "SHA3-256");

                JSONObject detailsForVerify = new JSONObject();
                detailsForVerify.put("did", senderDidIpfsHash);
                detailsForVerify.put("hash", hash);
                detailsForVerify.put("signature", senderSignature);

                boolean yesSender = Authenticate.verifySignature(detailsForVerify.toString());
                TokenReceiverLogger.debug("Quorum Auth : " + yesQuorum + "Sender Auth : " + yesSender);
                if (!(yesSender && yesQuorum)) {
                    output.println("420");
                    APIResponse.put("did", senderDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Sender / Quorum not verified");
                    TokenReceiverLogger.info("Sender / Quorum not verified");
                    IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                    output.close();
                    input.close();
                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }

                repo(ipfs);
                TokenReceiverLogger.debug("Sender and Quorum Verified");
                output.println("200");

                String pinDetails;
                try {
                    pinDetails = input.readLine();
                } catch (SocketException e) {
                    TokenReceiverLogger.warn("Sender Stream Null - Pinning Status");
                    APIResponse.put("did", "");
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Sender Stream Null - Pinning Status");

                    output.close();
                    input.close();
                    sk.close();
                    ss.close();
                    return APIResponse.toString();

                }
                if (pinDetails.equals("Unpinned")) {
                    int count = 0;
                    for (int i = 0; i < tokenCount; i++) {
                        FileWriter fileWriter;
                        fileWriter = new FileWriter(TOKENS_PATH + tokens.getString(i));
                        fileWriter.write(allTokenContent.get(i));
                        fileWriter.close();
                        add(TOKENS_PATH + tokens.getString(i), ipfs);
                        pin(tokens.getString(i), ipfs);
                        count++;

                    }

                    if (count == tokenCount) {
                        TokenReceiverLogger.debug("Pinned All Tokens");
                        output.println("Successfully Pinned");

                        String essentialShare;
                        try {
                            essentialShare = input.readLine();
                        } catch (SocketException e) {
                            TokenReceiverLogger.warn("Sender Stream Null - EShare Details");
                            APIResponse.put("did", "");
                            APIResponse.put("tid", "null");
                            APIResponse.put("status", "Failed");
                            APIResponse.put("message", "Sender Stream Null - EShare Details");

                            output.close();
                            input.close();
                            sk.close();
                            ss.close();
                            return APIResponse.toString();

                        }
                        long endTime = System.currentTimeMillis();

                        for (int i = 0; i < tokenCount; i++) {

                            ArrayList<String> groupTokens = new ArrayList<>();
                            for (int k = 0; k < tokenCount; k++) {
                                if (!tokens.getString(i).equals(tokens.getString(k)))
                                    groupTokens.add(tokens.getString(k));
                            }

                            JSONArray arrToken = new JSONArray();
                            JSONObject objectToken = new JSONObject();
                            objectToken.put("tokenHash", tokens.getString(i));
                            arrToken.put(objectToken);
                            JSONArray arr1 = new JSONArray(allTokenChainContent.get(i));
                            JSONObject obj2 = new JSONObject();
                            obj2.put("senderSign", senderSignature);
                            obj2.put("sender", senderDidIpfsHash);
                            obj2.put("group", groupTokens);
                            obj2.put("comment", comment);
                            obj2.put("tid", tid);
                            arr1.put(obj2);
                            writeToFile(TOKENCHAIN_PATH + tokens.getString(i) + ".json", arr1.toString(), false);
                        }

                        JSONObject transactionRecord = new JSONObject();
                        transactionRecord.put("role", "Receiver");
                        transactionRecord.put("tokens", tokens);
                        transactionRecord.put("txn", tid);
                        transactionRecord.put("quorumList", quorumSignatures.keys());
                        transactionRecord.put("senderDID", senderDidIpfsHash);
                        transactionRecord.put("receiverDID", receiverDidIpfsHash);
                        transactionRecord.put("Date", getCurrentUtcTime());
                        transactionRecord.put("totalTime", (endTime - startTime));
                        transactionRecord.put("comment", comment);
                        transactionRecord.put("essentialShare", essentialShare);

                        JSONArray transactionHistoryEntry = new JSONArray();
                        transactionHistoryEntry.put(transactionRecord);
                        updateJSON("add", WALLET_DATA_PATH + "TransactionHistory.json", transactionHistoryEntry.toString());

                        TokenReceiverLogger.info("Transaction ID: " + tid + "Transaction Successful");
                        output.println("Send Response");
                        APIResponse.put("did", senderDidIpfsHash);
                        APIResponse.put("tid", tid);
                        APIResponse.put("status", "Success");
                        APIResponse.put("tokens", tokens);
                        APIResponse.put("tokenHeader", tokenHeader);
                        APIResponse.put("comment", comment);
                        APIResponse.put("message", "Transaction Successful");
                        TokenReceiverLogger.info(" Transaction Successful");
                        executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                        output.close();
                        input.close();
                        sk.close();
                        ss.close();
                        return APIResponse.toString();

                    }
                    output.println("count mistmatch");
                    APIResponse.put("did", senderDidIpfsHash);
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "count mismacth");
                    TokenReceiverLogger.info(" Transaction failed");
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                    output.close();
                    input.close();
                    sk.close();
                    ss.close();
                    return APIResponse.toString();

                }

                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Failed to unpin");
                TokenReceiverLogger.info(" Transaction failed");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();

            }
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Consensus failed at Sender side");
            TokenReceiverLogger.info(" Transaction failed");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
            output.close();
            input.close();
            sk.close();
            ss.close();
            return APIResponse.toString();

        } catch (Exception e) {
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
            TokenReceiverLogger.error("Exception Occurred", e);
            return APIResponse.toString();
        } finally {
            try {
                ss.close();
                sk.close();
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
            } catch (Exception e) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                TokenReceiverLogger.error("Exception Occurred", e);
            }

        }
    }
}
