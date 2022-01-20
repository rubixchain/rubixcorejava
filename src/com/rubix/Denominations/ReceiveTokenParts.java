package com.rubix.Denominations;

import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.IPFS_PORT;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.RECEIVER_PORT;
import static com.rubix.Resources.Functions.TOKENCHAIN_PATH;
import static com.rubix.Resources.Functions.TOKENS_PATH;
import static com.rubix.Resources.Functions.WALLET_DATA_PATH;
import static com.rubix.Resources.Functions.calculateHash;
import static com.rubix.Resources.Functions.deleteFile;
import static com.rubix.Resources.Functions.getCurrentUtcTime;
import static com.rubix.Resources.Functions.getPeerID;
import static com.rubix.Resources.Functions.getValues;
import static com.rubix.Resources.Functions.nodeData;
import static com.rubix.Resources.Functions.pathSet;
import static com.rubix.Resources.Functions.readFile;
import static com.rubix.Resources.Functions.updateJSON;
import static com.rubix.Resources.Functions.writeToFile;
import static com.rubix.Resources.IPFSNetwork.add;
import static com.rubix.Resources.IPFSNetwork.executeIPFSCommands;
import static com.rubix.Resources.IPFSNetwork.get;
import static com.rubix.Resources.IPFSNetwork.listen;
import static com.rubix.Resources.IPFSNetwork.pin;
import static com.rubix.Resources.IPFSNetwork.repo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.RoundingMode;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Resources.IPFSNetwork;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class ReceiveTokenParts {
    public static Logger TokenPartReceiverLogger = Logger.getLogger(ReceiveTokenParts.class);
    private static final JSONObject APIResponse = new JSONObject();
    private static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);

    /**
     * Receiver Node: To receive a valid token from an authentic sender
     *
     * @return Transaction Details (JSONObject)
     * @throws IOException   handles IO Exceptions
     * @throws JSONException handles JSON Exceptions
     */
    public static String receive() {
        pathSet();

        DecimalFormat df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.CEILING);

        String PART_TOKEN_CHAIN_PATH = TOKENCHAIN_PATH.concat("PARTS/");
        String PART_TOKEN_PATH = TOKENS_PATH.concat("PARTS/");
        File partFolder = new File(PART_TOKEN_PATH);
        if (!partFolder.exists())
            partFolder.mkdir();
        partFolder = new File(PART_TOKEN_CHAIN_PATH);
        if (!partFolder.exists())
            partFolder.mkdir();

        ServerSocket ss = null;
        Socket sk = null;
        String senderPeerID = null;

        try {

            int quorumSignVerifyCount = 0;
            JSONObject quorumSignatures = null;

            ArrayList<String> quorumDID = new ArrayList<>();
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

            String receiverPeerID = getPeerID(DATA_PATH + "DID.json");

            String receiverDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", receiverPeerID);

            int port = RECEIVER_PORT + 1000;

            listen(receiverPeerID.concat("parts"), port);
            ss = new ServerSocket(port);
            TokenPartReceiverLogger.debug(
                    "Part Receiver Listening on " + partFolder + " with app name " + receiverPeerID.concat("parts"));

            sk = ss.accept();
            BufferedReader input = new BufferedReader(new InputStreamReader(sk.getInputStream()));
            PrintStream output = new PrintStream(sk.getOutputStream());
            long startTime = System.currentTimeMillis();

            try {
                senderPeerID = input.readLine();
            } catch (SocketException e) {

                TokenPartReceiverLogger.warn("Sender Stream Null - Sender Details");
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
            TokenPartReceiverLogger.debug("Sender details received");

            String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);
            String senderWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid", senderPeerID);

            if (!(senderDidIpfsHash.contains("Qm") && senderWidIpfsHash.contains("Qm"))) {

                output.println("420");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender details not available in network , please sync");
                TokenPartReceiverLogger.info("Sender details not available in datatable");
                /* executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID); */

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
                TokenPartReceiverLogger.info("Sender details not available");
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            TokenPartReceiverLogger.debug("Sender details authenticated");
            output.println("200");

            String tokenDetails;
            try {
                tokenDetails = input.readLine();
            } catch (SocketException e) {

                TokenPartReceiverLogger.warn("Sender Stream Null - Token Details");
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
            TokenPartReceiverLogger.debug("Token details received: ");
            JSONArray TokenDetailsArray = new JSONArray(tokenDetails);
            String tokens = TokenDetailsArray.getJSONObject(0).getString("token");
            String tokenChains = TokenDetailsArray.getJSONObject(0).getString("tokenChain");
            String sender = TokenDetailsArray.getJSONObject(0).getString("sender");

            JSONArray tokenChainContent = new JSONArray(
                    get(TokenDetailsArray.getJSONObject(1).getString("getTokenChain"), ipfs));
            TokenPartReceiverLogger.debug("Token Chosen to be sent: " + tokens);
            TokenPartReceiverLogger.debug("Token chain hash: " + tokenChains);

            JSONObject calculateCID = new JSONObject();
            calculateCID.put("token", tokens);
            calculateCID.put("tokenChain", tokenChains);
            calculateCID.put("sender", sender);
            boolean chequeCheckFlag = true;

            double amount = TokenDetailsArray.getJSONObject(1).getDouble("amount");
            Number numberFormat = amount;
            amount = Double.parseDouble(df.format(numberFormat.doubleValue()));
            String senderToken = calculateCID.toString();

            String consensusID = calculateHash(senderToken, "SHA3-256");
            writeToFile(LOGGER_PATH + "consensusID", consensusID, false);
            String getCIDipfsHash = IPFSNetwork.addHashOnly(LOGGER_PATH + "consensusID", ipfs);
            TokenPartReceiverLogger.debug("********Consensus ID*********:  " + getCIDipfsHash);
            deleteFile(LOGGER_PATH + "consensusID");

            // boolean ownersFlag = true;
            // if (!IPFSNetwork.dhtEmpty(getCIDipfsHash, ipfs)){
            // ownersFlag = false;
            // }

            boolean chainFlag = true;
            for (int i = 0; i < tokenChainContent.length(); i++) {
                String previousHash = tokenChainContent.getJSONObject(i).getString("previousHash");
                String nextHash = tokenChainContent.getJSONObject(i).getString("nextHash");
                String rePreviousHash, reNextHash;
                if (i == 0) {
                    rePreviousHash = calculateHash(new JSONObject().toString(), "SHA3-256");
                    reNextHash = calculateHash(tokenChainContent.getJSONObject(i + 1).getString("tid"), "SHA3-256");
                } else if (i == tokenChainContent.length() - 1) {
                    rePreviousHash = calculateHash(tokenChainContent.getJSONObject(i - 1).getString("tid"), "SHA3-256");
                    reNextHash = "";
                } else {
                    rePreviousHash = calculateHash(tokenChainContent.getJSONObject(i - 1).getString("tid"), "SHA3-256");
                    reNextHash = calculateHash(tokenChainContent.getJSONObject(i + 1).getString("tid"), "SHA3-256");
                }

                if (!(rePreviousHash.equals(previousHash) && reNextHash.equals(nextHash))) {
                    chainFlag = false;
                }
            }

            double availableParts = 0;
            for (int i = 0; i < tokenChainContent.length(); i++) {
                if (tokenChainContent.getJSONObject(i).has("role")) {
                    if (tokenChainContent.getJSONObject(i).getString("role").equals("Receiver")
                            && tokenChainContent.getJSONObject(i).getString("receiver").equals(senderDidIpfsHash)) {
                        if (tokenChainContent.getJSONObject(i).has("amount"))
                            availableParts += tokenChainContent.getJSONObject(i).getDouble("amount");
                    } else if (tokenChainContent.getJSONObject(i).getString("role").equals("Sender")
                            && tokenChainContent.getJSONObject(i).getString("sender").equals(senderDidIpfsHash)) {
                        if (tokenChainContent.getJSONObject(i).has("amount"))
                            availableParts -= tokenChainContent.getJSONObject(i).getDouble("amount");
                    }
                }
            }

            availableParts += amount;
            numberFormat = availableParts;
            availableParts = Double.parseDouble(df.format(numberFormat.doubleValue()));

            String TokenContent = get(tokens, ipfs);

            if (!chequeCheckFlag) {

                String errorMessage = "Issue with cheque tokens - Either issues cheques not pinned/token pinned with no cheques issued";
                output.println("419");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", errorMessage);
                TokenPartReceiverLogger.debug(errorMessage);
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            // else if (!consensusID.equals(consensusIdCompare)) {
            // String errorMessage = "Consensus ID not unique: Hashes do not match - " +
            // "Sent " + consensusID + " Recalculated " + consensusIdCompare;
            // output.println("420");
            // APIResponse.put("did", senderDidIpfsHash);
            // APIResponse.put("tid", "null");
            // APIResponse.put("status", "Failed");
            // APIResponse.put("message", errorMessage);
            // TokenPartReceiverLogger.debug(errorMessage);
            // executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
            // output.close();
            // input.close();
            // sk.close();
            // ss.close();
            // return APIResponse.toString();
            // }
            else if (!chainFlag) {

                String errorMessage = "Broken Cheque Chain";
                output.println("421");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", errorMessage);
                TokenPartReceiverLogger.debug(errorMessage);
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            } else if (availableParts > 1) {

                String errorMessage = "Token wholly spent already";
                output.println("422");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", errorMessage);
                TokenPartReceiverLogger.debug(errorMessage);
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            repo(ipfs);

            output.println("200");
            String senderDetails;
            try {
                senderDetails = input.readLine();
            } catch (SocketException e) {

                TokenPartReceiverLogger.warn("Sender Stream Null - Sender Details");
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

            TokenPartReceiverLogger.debug("Consensus Status:  " + Status);

            TokenPartReceiverLogger.debug("Verifying Quorum ...  ");
            TokenPartReceiverLogger.debug("Please wait, this might take a few seconds");

            if (!Status.equals("Consensus Failed")) {
                boolean yesQuorum = false;
                if (Status.equals("Consensus Reached")) {
                    // TokenPartReceiverLogger.debug("Quorum Signatures: " + QuorumDetails);
                    quorumSignatures = new JSONObject(QuorumDetails);
                    int alphaSize = quorumSignatures.length() - 10;

                    String hash = calculateHash(senderToken, "SHA3-256");
                    String verifyQuorumHash = calculateHash(hash.concat(receiverDidIpfsHash), "SHA3-256");
                    TokenPartReceiverLogger.debug("Verify Quorum Hash: " + verifyQuorumHash);
                    // TokenPartReceiverLogger.debug("Quorum Hash on Receiver Side " +
                    // verifyQuorumHash);
                    // TokenPartReceiverLogger.debug("Quorum Signatures length : " +
                    // quorumSignatures.length());

                    Iterator<String> keys = quorumSignatures.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        quorumDID.add(key);
                    }

                    for (String quorumDidIpfsHash : quorumDID) {
                        String quorumWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash",
                                quorumDidIpfsHash);

                        File quorumDataFolder = new File(DATA_PATH + quorumDidIpfsHash + "/");
                        if (!quorumDataFolder.exists()) {
                            quorumDataFolder.mkdirs();
                            IPFSNetwork.getImage(quorumDidIpfsHash, ipfs, DATA_PATH + quorumDidIpfsHash + "/DID.png");
                            IPFSNetwork.getImage(quorumWidIpfsHash, ipfs,
                                    DATA_PATH + quorumDidIpfsHash + "/PublicShare.png");
                        }
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
                    TokenPartReceiverLogger.debug("Verified Quorum Count " + quorumSignVerifyCount);
                    yesQuorum = quorumSignVerifyCount >= quorumSignatures.length();
                }

                String hash = calculateHash(tokens + tokenChains + receiverDidIpfsHash + comment, "SHA3-256");

                JSONObject detailsForVerify = new JSONObject();
                detailsForVerify.put("did", senderDidIpfsHash);
                detailsForVerify.put("hash", hash);
                detailsForVerify.put("signature", senderSignature);

                boolean yesSender = Authenticate.verifySignature(detailsForVerify.toString());
                if (!(yesSender && yesQuorum)) {

                    output.println("420");
                    APIResponse.put("did", senderDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Sender / Quorum not verified");
                    TokenPartReceiverLogger.info("Sender / Quorum not verified");
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                    output.close();
                    input.close();
                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }
                repo(ipfs);
                TokenPartReceiverLogger.debug("Sender and Quorum Verified");
                output.println("200");

                String pinDetails;
                try {
                    pinDetails = input.readLine();
                } catch (SocketException e) {

                    TokenPartReceiverLogger.warn("Sender Stream Null - Pinning Status");
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
                    File tokenFile = new File(PART_TOKEN_PATH + tokens);
                    if (!tokenFile.exists())
                        tokenFile.createNewFile();
                    FileWriter fileWriter;
                    fileWriter = new FileWriter(PART_TOKEN_PATH + tokens);
                    fileWriter.write(TokenContent);
                    fileWriter.close();
                    String tokenHash = add(PART_TOKEN_PATH + tokens, ipfs);
                    pin(tokenHash, ipfs);

                    TokenPartReceiverLogger.debug("Pinned All Tokens");

                    output.println("Successfully Pinned");

                    String essentialShare;
                    try {
                        essentialShare = input.readLine();
                    } catch (SocketException e) {

                        TokenPartReceiverLogger.warn("Sender Stream Null - EShare Details");
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

                    JSONObject chequeObject = new JSONObject();
                    chequeObject.put("sender", senderDidIpfsHash);
                    chequeObject.put("receiver", receiverDidIpfsHash);
                    chequeObject.put("parent-token", tokens);
                    chequeObject.put("parent-chain", tokenChains);
                    numberFormat = amount;
                    amount = Double.parseDouble(df.format(numberFormat.doubleValue()));
                    chequeObject.put("amount", amount);
                    chequeObject.put("tid", tid);
                    writeToFile(LOGGER_PATH.concat(tokens), chequeObject.toString(), false);
                    String chequeHash = IPFSNetwork.add(LOGGER_PATH.concat(tokens), ipfs);
                    deleteFile(LOGGER_PATH.concat(tokens));

                    // File chainFile = new
                    // File(PART_TOKEN_CHAIN_PATH.concat(tokens).concat(".json"));
                    // if (chainFile.exists()) {
                    //
                    // }
                    // JSONObject newPartObject = new JSONObject();
                    // newPartObject.put("senderSign", senderSignature);
                    // newPartObject.put("sender", senderDidIpfsHash);
                    // newPartObject.put("comment", comment);
                    // newPartObject.put("tid", tid);
                    // newPartObject.put("nextHash", "");
                    // newPartObject.put("previousHash",
                    // calculateHash(tokenChainContent.getJSONObject(tokenChainContent.length()-1).getString("tid"),
                    // "SHA3-256"));
                    // newPartObject.put("amount", amount);
                    // newPartObject.put("cheque", chequeHash);
                    // newPartObject.put("role", "Receiver");
                    // tokenChainContent.put(newPartObject);
                    // writeToFile(PART_TOKEN_CHAIN_PATH + tokens + ".json",
                    // tokenChainContent.toString(), false);

                    //
                    JSONObject newPartObject = new JSONObject();
                    newPartObject.put("senderSign", senderSignature);
                    newPartObject.put("sender", senderDidIpfsHash);
                    newPartObject.put("receiver", receiverDidIpfsHash);
                    newPartObject.put("comment", comment);
                    newPartObject.put("tid", tid);
                    newPartObject.put("nextHash", "");
                    newPartObject.put("previousHash",
                            calculateHash(
                                    tokenChainContent.getJSONObject(tokenChainContent.length() - 1).getString("tid"),
                                    "SHA3-256"));
                    numberFormat = amount;
                    amount = Double.parseDouble(df.format(numberFormat.doubleValue()));
                    newPartObject.put("amount", amount);
                    newPartObject.put("cheque", chequeHash);
                    newPartObject.put("role", "Receiver");

                    File chainFile = new File(PART_TOKEN_CHAIN_PATH.concat(tokens).concat(".json"));
                    if (chainFile.exists()) {

                        String readChain = readFile(PART_TOKEN_CHAIN_PATH + tokens + ".json");
                        JSONArray readChainArray = new JSONArray(readChain);
                        readChainArray.put(tokenChainContent.getJSONObject(tokenChainContent.length() - 1));
                        readChainArray.put(newPartObject);

                        writeToFile(PART_TOKEN_CHAIN_PATH + tokens + ".json", readChainArray.toString(), false);

                    } else {
                        tokenChainContent.put(newPartObject);
                        writeToFile(PART_TOKEN_CHAIN_PATH + tokens + ".json", tokenChainContent.toString(), false);
                    }

                    // File chainFile = new
                    // File(PART_TOKEN_CHAIN_PATH.concat(tokens).concat(".json"));
                    // if (chainFile.exists()) {
                    // TokenPartReceiverLogger.debug("Token chain file already exist");
                    // debugObject.put("Debug: ", "Token chain file already exist");
                    // String contentArray =
                    // readFile(PART_TOKEN_CHAIN_PATH.concat(tokens).concat(".json"));
                    // JSONArray array = new JSONArray(contentArray);
                    //
                    // JSONObject newPartObject = new JSONObject();
                    // newPartObject.put("senderSign", senderSignature);
                    // newPartObject.put("sender", senderDidIpfsHash);
                    // newPartObject.put("comment", comment);
                    // newPartObject.put("tid", tid);
                    // newPartObject.put("nextHash", "");
                    // newPartObject.put("previousHash",
                    // calculateHash(array.getJSONObject(array.length() - 1).getString("tid"),
                    // "SHA3-256"));
                    // newPartObject.put("amount", amount);
                    // newPartObject.put("cheque", chequeHash);
                    // newPartObject.put("role", "Receiver");
                    // array.put(newPartObject);
                    //
                    // TokenPartReceiverLogger.debug("Final Chain Array: " + array);
                    // debugObject.put("Final Chain Array: ", array);
                    // writeToFile(PART_TOKEN_CHAIN_PATH + tokens + ".json", array.toString(),
                    // false);
                    // } else {
                    // TokenPartReceiverLogger.debug("Creating a new token chain");
                    // debugObject.put("Debug: ", "Creating a new token chain");
                    // chainFile.createNewFile();
                    // JSONObject newPartObject = new JSONObject();
                    // newPartObject.put("senderSign", senderSignature);
                    // newPartObject.put("sender", senderDidIpfsHash);
                    // newPartObject.put("comment", comment);
                    // newPartObject.put("tid", tid);
                    // newPartObject.put("nextHash", "");
                    // newPartObject.put("previousHash",
                    // calculateHash(tokenChainContent.getJSONObject(tokenChainContent.length() -
                    // 1).getString("tid"), "SHA3-256"));
                    // newPartObject.put("amount", amount);
                    // newPartObject.put("cheque", chequeHash);
                    // newPartObject.put("role", "Receiver");
                    // tokenChainContent.put(newPartObject);
                    //
                    // TokenPartReceiverLogger.debug("Final Chain Array:" + tokenChainContent);
                    // debugObject.put("Final Chain Array: ", tokenChainContent);
                    // writeToFile(PART_TOKEN_CHAIN_PATH + tokens + ".json",
                    // tokenChainContent.toString(), false);

                    // JSONObject newPartObject = new JSONObject();
                    // newPartObject.put("senderSign", senderSignature);
                    // newPartObject.put("sender", senderDidIpfsHash);
                    // newPartObject.put("comment", comment);
                    // newPartObject.put("tid", tid);
                    // newPartObject.put("nextHash", "");
                    // newPartObject.put("previousHash", calculateHash(new JSONObject().toString(),
                    // "SHA3-256"));
                    // newPartObject.put("amount", amount);
                    // newPartObject.put("cheque", chequeHash);
                    // newPartObject.put("role", "Receiver");
                    // JSONArray array = new JSONArray();
                    // array.put(newPartObject);
                    //
                    // writeToFile(PART_TOKEN_CHAIN_PATH + tokens + ".json", array.toString(),
                    // false);
                    // }
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
                    numberFormat = amount;
                    amount = Double.parseDouble(df.format(numberFormat.doubleValue()));
                    transactionRecord.put("amount", amount);

                    JSONArray transactionHistoryEntry = new JSONArray();
                    transactionHistoryEntry.put(transactionRecord);
                    updateJSON("add", WALLET_DATA_PATH + "TransactionHistory.json", transactionHistoryEntry.toString());

                    TokenPartReceiverLogger.info("Transaction ID: " + tid + "Transaction Successful");
                    output.println("Send Response");
                    APIResponse.put("did", senderDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Success");
                    APIResponse.put("tokens", tokens);
                    APIResponse.put("comment", comment);
                    APIResponse.put("message", "Transaction Successful");
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
                TokenPartReceiverLogger.info(" Transaction failed");
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
            TokenPartReceiverLogger.info(" Transaction failed");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
            output.close();
            input.close();
            sk.close();
            ss.close();
            return APIResponse.toString();

        } catch (Exception e) {
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
            TokenPartReceiverLogger.error("Exception Occurred", e);
            return APIResponse.toString();
        } finally {
            try {
                ss.close();
                sk.close();
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
            } catch (Exception e) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                TokenPartReceiverLogger.error("Exception Occurred", e);
            }

        }
    }
}
