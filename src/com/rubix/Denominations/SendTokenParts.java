package com.rubix.Denominations;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Consensus.InitiatorConsensus;
import com.rubix.Consensus.InitiatorProcedure;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;


public class SendTokenParts {
    private static final Logger TokenPartsSenderLogger = Logger.getLogger(SendTokenParts.class);
    public static BufferedReader serverInput;
    private static boolean senderMutex = false;

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
        pathSet();

        String PART_TOKEN_CHAIN_PATH = TOKENCHAIN_PATH.concat("PARTS/");
        String PART_TOKEN_PATH = TOKENS_PATH.concat("PARTS/");
        File partFolder = new File(PART_TOKEN_PATH);
        if (!partFolder.exists())
            partFolder.mkdir();
        partFolder = new File(PART_TOKEN_CHAIN_PATH);
        if (!partFolder.exists())
            partFolder.mkdir();
        File partTokensFile = new File(PAYMENTS_PATH.concat("PartsToken.json"));
        if (!partTokensFile.exists()) {
            partTokensFile.createNewFile();
            writeToFile(partTokensFile.toString(), "[]", false);
        }

        JSONObject APIResponse = new JSONObject();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String receiverPeerId;
        JSONObject detailsObject = new JSONObject(data);
        String receiverDidIpfsHash = detailsObject.getString("receiverDidIpfsHash");
        String pvt = detailsObject.getString("pvt");
        float amount = detailsObject.getFloat("amount");
        int type = detailsObject.getInt("type");
        String comment = detailsObject.getString("comment");

        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);

        String tokens;
        String partFileContent = readFile(partTokensFile.toString());
        JSONArray partContentArray = new JSONArray(partFileContent);
        if (partContentArray.length() == 0) {
            TokenPartsSenderLogger.debug("New token for parts");
            String bnkArrayContent = readFile(PAYMENTS_PATH.concat("BNK00.json"));
            JSONArray bnkArray = new JSONArray(bnkArrayContent);
            JSONObject object = bnkArray.getJSONObject(0);
            tokens = object.getString("tokenHash");
            JSONObject object1 = new JSONObject();
            object1.put("tokenHash", tokens);
            partContentArray.put(object1);
            writeToFile(partTokensFile.toString(), partContentArray.toString(), false);

            File tokenFile = new File(TOKENS_PATH.concat(tokens));
            tokenFile.renameTo(new File(PART_TOKEN_PATH.concat(tokens)));
            File chainFile = new File(TOKENCHAIN_PATH.concat(tokens).concat(".json"));
            chainFile.renameTo(new File(PART_TOKEN_CHAIN_PATH.concat(tokens).concat(".json")));

            bnkArray.remove(0);
            writeToFile(PAYMENTS_PATH.concat("BNK00.json"), bnkArray.toString(), false);


            File shiftedFile = new File(PAYMENTS_PATH.concat("ShiftedTokens.json"));
            if (!shiftedFile.exists()) {
                shiftedFile.createNewFile();
                JSONArray shiftedTokensArray = new JSONArray();
                shiftedTokensArray.put(tokens);
                writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedTokensArray.toString(), false);
            } else {
                String shiftedContent = readFile(PAYMENTS_PATH.concat("ShiftedTokens.json"));
                JSONArray shiftedArray = new JSONArray(shiftedContent);
                shiftedArray.put(tokens);
                writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedArray.toString(), false);
            }

        } else {
            TokenPartsSenderLogger.debug("Token parts present");
            tokens = partContentArray.getJSONObject(0).getString("tokenHash");
            String tokenChain = readFile(PART_TOKEN_CHAIN_PATH.concat(tokens).concat(".json"));
            JSONArray chainArray = new JSONArray(tokenChain);
            float availableParts = 0, senderCount = 0, receiverCount = 0;
            for (int i = 0; i < chainArray.length(); i++) {
                if (chainArray.getJSONObject(i).has("role")) {
                    if (chainArray.getJSONObject(i).getString("role").equals("Sender") && chainArray.getJSONObject(i).getString("sender").equals(senderDidIpfsHash)) {
                        senderCount += chainArray.getJSONObject(i).getFloat("amount");
                    } else if (chainArray.getJSONObject(i).getString("role").equals("Receiver") && chainArray.getJSONObject(i).getString("receiver").equals(senderDidIpfsHash)) {
                        receiverCount += chainArray.getJSONObject(i).getFloat("amount");
                    }
                }
            }
            availableParts = 1 - (senderCount - receiverCount);
//            for (int i = 0; i < chainArray.length(); i++) {
//                if(chainArray.getJSONObject(i).has("role")) {
//                    if (chainArray.getJSONObject(i).getString("role").equals("Receiver") && chainArray.getJSONObject(i).getString("receiver").equals(senderDidIpfsHash)) {
//                        if (chainArray.getJSONObject(i).has("amount")) {
//                            availableParts += chainArray.getJSONObject(i).getFloat("amount");
//                        }
//                    } else if (chainArray.getJSONObject(i).getString("role").equals("Sender") && chainArray.getJSONObject(i).getString("sender").equals(senderDidIpfsHash)) {
//                        if (chainArray.getJSONObject(i).has("amount")) {
//                            availableParts -= chainArray.getJSONObject(i).getFloat("amount");
//                        }
//                    }
//                }
//            }
//

//            if(availableParts < 0)
//                availableParts = 1 - availableParts;
            TokenPartsSenderLogger.debug("Amount Available to spend: " + availableParts);

            if (amount > availableParts) {
                TokenPartsSenderLogger.debug("Amount greater than existing");
                String bnkFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
                JSONArray bankArray = new JSONArray(bnkFile);
                if (bankArray.length() != 0) {
                    JSONObject bnkFirstObject = bankArray.getJSONObject(0);
                    tokens = bnkFirstObject.getString("tokenHash");
                    JSONArray newArray = new JSONArray();
                    JSONObject newPartsObject = new JSONObject();
                    newPartsObject.put("tokenHash", tokens);
                    newArray.put(newPartsObject);
                    newArray.putAll(partContentArray);
                    writeToFile(partTokensFile.toString(), newArray.toString(), false);

                    File tokenFile = new File(TOKENS_PATH.concat(tokens));
                    tokenFile.renameTo(new File(PART_TOKEN_PATH.concat(tokens)));
                    File chainFile = new File(TOKENCHAIN_PATH.concat(tokens).concat(".json"));
                    chainFile.renameTo(new File(PART_TOKEN_CHAIN_PATH.concat(tokens).concat(".json")));

                    bankArray.remove(0);
                    writeToFile(PAYMENTS_PATH.concat("BNK00.json"), bankArray.toString(), false);

                    File shiftedFile = new File(PAYMENTS_PATH.concat("ShiftedTokens.json"));
                    if (!shiftedFile.exists()) {
                        shiftedFile.createNewFile();
                        JSONArray shiftedTokensArray = new JSONArray();
                        shiftedTokensArray.put(tokens);
                        writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedTokensArray.toString(), false);
                    } else {
                        String shiftedContent = readFile(PAYMENTS_PATH.concat("ShiftedTokens.json"));
                        JSONArray shiftedArray = new JSONArray(shiftedContent);
                        shiftedArray.put(tokens);
                        writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedArray.toString(), false);
                    }

                } else {
                    TokenPartsSenderLogger.debug("No Tokens available");
                    APIResponse.put("did", senderDidIpfsHash);
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "No Tokens available");
                    return APIResponse;
                }
            }
        }

        if (senderMutex) {

            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Sender busy. Try again later");
            TokenPartsSenderLogger.warn("Sender busy");
            return APIResponse;
        }

        senderMutex = true;


        APIResponse = new JSONObject();
        TokenPartsSenderLogger.debug("Checking files for token " + tokens);
        TokenPartsSenderLogger.debug("In location " + TOKENS_PATH.concat("PARTS/") + tokens + " " + TOKENCHAIN_PATH.concat("PARTS/") + tokens + ".json");
        File token = new File(TOKENS_PATH.concat("PARTS/") + tokens);
        File tokenchain = new File(TOKENCHAIN_PATH.concat("PARTS/") + tokens + ".json");
        if (!(token.exists() && tokenchain.exists())) {
            if (!token.exists())
                TokenPartsSenderLogger.debug("Token File not avail");
            if (!tokenchain.exists())
                TokenPartsSenderLogger.debug("Token Chain File not avail");

            TokenPartsSenderLogger.info("Tokens Not Verified");
            senderMutex = false;
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Invalid token(s)");
            return APIResponse;

        }
        String hash = add(PART_TOKEN_PATH + tokens, ipfs);
        pin(hash, ipfs);

        String chainContent = readFile(PART_TOKEN_CHAIN_PATH + tokens + ".json");
        JSONArray chainArray = new JSONArray();
        JSONArray finalChainArray = new JSONArray(chainContent);
        for (int i = 0; i < finalChainArray.length(); i++) {
            JSONObject object = finalChainArray.getJSONObject(i);
            if (i == 0) {
                object.put("previousHash", calculateHash(new JSONObject().toString(), "SHA3-256"));
                object.put("nextHash", calculateHash(finalChainArray.getJSONObject(i + 1).getString("tid"), "SHA3-256"));
            } else if (i == finalChainArray.length() - 1) {
                object.put("previousHash", calculateHash(finalChainArray.getJSONObject(i - 1).getString("tid"), "SHA3-256"));
                object.put("nextHash", "");
            } else {
                object.put("previousHash", calculateHash(finalChainArray.getJSONObject(i - 1).getString("tid"), "SHA3-256"));
                object.put("nextHash", calculateHash(finalChainArray.getJSONObject(i + 1).getString("tid"), "SHA3-256"));
            }
            chainArray.put(object);

        }

        writeToFile(PART_TOKEN_CHAIN_PATH + tokens + ".json", chainArray.toString(), false);
        String tokenChainHash1 = add(PART_TOKEN_CHAIN_PATH + tokens + ".json", ipfs);

        String authSenderByRecHash = calculateHash(tokens + tokenChainHash1 + receiverDidIpfsHash + comment, "SHA3-256");
        String tid = calculateHash(authSenderByRecHash, "SHA3-256");
        writeToFile(LOGGER_PATH + "tempbeta", tid.concat(senderDidIpfsHash), false);
        String betaHash = IPFSNetwork.add(LOGGER_PATH + "tempbeta", ipfs);
        deleteFile(LOGGER_PATH + "tempbeta");

        writeToFile(LOGGER_PATH + "tempgamma", tid.concat(receiverDidIpfsHash), false);
        String gammaHash = IPFSNetwork.add(LOGGER_PATH + "tempgamma", ipfs);
        deleteFile(LOGGER_PATH + "tempgamma");


        JSONArray quorumArray;
        JSONArray alphaQuorum = new JSONArray();
        JSONArray betaQuorum = new JSONArray();
        JSONArray gammaQuorum = new JSONArray();
        int alphaSize;

        ArrayList alphaPeersList;
        ArrayList betaPeersList;
        ArrayList gammaPeersList;
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
                TokenPartsSenderLogger.error("Unknown quorum type input, cancelling transaction");
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
        alphaPeersList = QuorumCheck(alphaQuorum, alphaSize);
        betaPeersList = QuorumCheck(betaQuorum, 7);
        gammaPeersList = QuorumCheck(gammaQuorum, 7);

        if (alphaPeersList.size() < minQuorum(alphaSize) || betaPeersList.size() < 5 || gammaPeersList.size() < 5) {
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Quorum Members not available");
            TokenPartsSenderLogger.warn("Quorum Members not available");
            senderMutex = false;
            return APIResponse;
        }


        String senderSign = getSignFromShares(pvt, authSenderByRecHash);

        JSONObject senderDetails2Receiver = new JSONObject();
        senderDetails2Receiver.put("sign", senderSign);
        senderDetails2Receiver.put("tid", tid);
        senderDetails2Receiver.put("comment", comment);

        String newTokenChain = readFile(PART_TOKEN_CHAIN_PATH + tokens + ".json");
        chainArray = new JSONArray(newTokenChain);
        String tokenChainHash2;
        JSONObject newLastObject = new JSONObject();
        if (chainArray.length() == 0) {
            newLastObject.put("previousHash", calculateHash(new JSONObject().toString(), "SHA3-256"));

        } else {
            JSONObject secondLastObject = chainArray.getJSONObject(chainArray.length() - 1);
            secondLastObject.put("nextHash", calculateHash(tid, "SHA3-256"));

            newLastObject.put("previousHash", calculateHash(chainArray.getJSONObject(chainArray.length() - 1).getString("tid"), "SHA3-256"));
        }
        newLastObject.put("senderSign", senderSign);
        newLastObject.put("sender", senderDidIpfsHash);
        newLastObject.put("receiver", receiverDidIpfsHash);
        newLastObject.put("comment", comment);
        newLastObject.put("tid", tid);
        newLastObject.put("nextHash", "");
        newLastObject.put("role", "Sender");
        newLastObject.put("amount", amount);
        chainArray.put(newLastObject);
        writeToFile(PART_TOKEN_CHAIN_PATH + tokens + ".json", chainArray.toString(), false);
        tokenChainHash2 = add(PART_TOKEN_CHAIN_PATH + tokens + ".json", ipfs);

        TokenPartsSenderLogger.debug("Token Chosen to be sent: " + tokens);
        TokenPartsSenderLogger.debug("Token chain hash: " + tokenChainHash2);

        JSONArray tokenBindDetailsArray = new JSONArray();
        JSONObject tokenDetails = new JSONObject();
        tokenDetails.put("token", tokens);
        tokenDetails.put("tokenChain", tokenChainHash1);
        tokenDetails.put("sender", senderDidIpfsHash);

        String senderToken = tokenDetails.toString();

        String consensusID = calculateHash(senderToken, "SHA3-256");
        writeToFile(LOGGER_PATH + "consensusID", consensusID, false);
        //String consensusIDIPFSHash = IPFSNetwork.add(LOGGER_PATH + "consensusID", ipfs);
        String consensusIDIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH + "consensusID", ipfs);
        TokenPartsSenderLogger.debug("********Consensus ID*********:  " + consensusIDIPFSHash);
        // pin(consensusIDIPFSHash,ipfs);
        deleteFile(LOGGER_PATH + "consensusID");

        JSONObject ipfsObject = new JSONObject();
        ipfsObject.put("ipfsHash", consensusIDIPFSHash);
        ipfsObject.put("amount", amount);
        ipfsObject.put("getTokenChain", tokenChainHash2);
        tokenBindDetailsArray.put(tokenDetails);
        tokenBindDetailsArray.put(ipfsObject);

        receiverPeerId = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", receiverDidIpfsHash);
        swarmConnectP2P(receiverPeerId, ipfs);

        String receiverWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash", receiverDidIpfsHash);
        nodeData(receiverDidIpfsHash, receiverWidIpfsHash, ipfs);

        TokenPartsSenderLogger.debug("Sending to " + receiverPeerId.concat("parts") + "on port " + port);
        forward(receiverPeerId.concat("parts"), port, receiverPeerId);

        TokenPartsSenderLogger.debug("Forwarded to " + receiverPeerId + " on " + port);
        Socket senderSocket = new Socket("127.0.0.1", port);

        BufferedReader input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
        PrintStream output = new PrintStream(senderSocket.getOutputStream());

        long startTime = System.currentTimeMillis();

        output.println(senderPeerID);
        TokenPartsSenderLogger.debug("Sent PeerID");

        String peerAuth;
        try {
            peerAuth = input.readLine();
        } catch (SocketException e) {

            TokenPartsSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Sender Auth");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Receiver " + receiverDidIpfsHash + "is unable to respond! - Sender Auth");

            return APIResponse;
        }

        if (peerAuth != null && (!peerAuth.equals("200"))) {

            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            TokenPartsSenderLogger.info("Sender Data Not Available");
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

        String tokenAuth;
        try {
            tokenAuth = input.readLine();
        } catch (SocketException e) {
            TokenPartsSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Token Auth");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Receiver " + receiverDidIpfsHash + "is unable to respond! - Token Auth");

            return APIResponse;
        }

        if (tokenAuth != null && (!tokenAuth.equals("200"))) {
            String errorMessage = null;
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);

            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            switch (tokenAuth) {
                case "419":
                    errorMessage = "Issue with cheque tokens - Either issues cheques not pinned/token pinned with no cheques issued";
                    break;
                case "420":
                    errorMessage = "Consensus ID not unique: Hashes do not match";
                    break;
                case "421":
                    errorMessage = "Broken Cheque Chain";
                    break;
                case "422":
                    errorMessage = "Token wholly spent already";
                    break;
            }

            TokenPartsSenderLogger.info(errorMessage);
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", errorMessage);

            return APIResponse;

        }

        JSONObject dataObject = new JSONObject();
        dataObject.put("tid", tid);
        dataObject.put("message", senderToken);
        dataObject.put("receiverDidIpfs", receiverDidIpfsHash);
        dataObject.put("pvt", pvt);
        dataObject.put("senderDidIpfs", senderDidIpfsHash);
        dataObject.put("token", tokens);
        dataObject.put("alphaList", alphaPeersList);
        dataObject.put("betaList", betaPeersList);
        dataObject.put("gammaList", gammaPeersList);

        InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 100, alphaSize, "");
        //TokenPartsSenderLogger.debug("length on sender " + InitiatorConsensus.quorumSignature.length() + "response count " + InitiatorConsensus.quorumResponse);
        if (InitiatorConsensus.quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
            TokenPartsSenderLogger.debug("Consensus Failed");
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

        TokenPartsSenderLogger.debug("Consensus Reached");
        senderDetails2Receiver.put("status", "Consensus Reached");
        senderDetails2Receiver.put("quorumsign", InitiatorConsensus.quorumSignature.toString());

        output.println(senderDetails2Receiver);
        TokenPartsSenderLogger.debug("Quorum Signatures length " + InitiatorConsensus.quorumSignature.length());

        String signatureAuth;
        try {
            signatureAuth = input.readLine();
        } catch (SocketException e) {
            TokenPartsSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Signature Auth");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Receiver " + receiverDidIpfsHash + "is unable to respond! - Signature Auth");

            return APIResponse;
        }
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        if (signatureAuth != null && (!signatureAuth.equals("200"))) {
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            TokenPartsSenderLogger.info("Authentication Failed");
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

        TokenPartsSenderLogger.debug("Unpinned Tokens");
        output.println("Unpinned");

        String confirmation;
        try {
            confirmation = input.readLine();
        } catch (SocketException e) {
            TokenPartsSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Pinning Auth");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Receiver " + receiverDidIpfsHash + "is unable to respond! - Pinning Auth");

            return APIResponse;
        }
        if (confirmation != null && (!confirmation.equals("Successfully Pinned"))) {
            TokenPartsSenderLogger.warn("Multiple Owners for the token");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            TokenPartsSenderLogger.info("Tokens with multiple pins");
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
        String respAuth;
        try {
            respAuth = input.readLine();
        } catch (SocketException e) {

            TokenPartsSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Share Confirmation");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;
            updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Receiver " + receiverDidIpfsHash + "is unable to respond! - Share Confirmation");

            return APIResponse;
        }

        if (respAuth != null && (!respAuth.equals("Send Response"))) {
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
            TokenPartsSenderLogger.info("Incomplete Transaction");
            return APIResponse;

        }

//        JSONArray TcArray = new JSONArray(readFile(PART_TOKEN_CHAIN_PATH.concat(tokens).concat(".json")));
//        if (TcArray.length() > 0) {
//            debugObject.put("Debug: ", "TC length is > 0");
//            JSONArray finalHistoryArray = new JSONArray();
//            for (int i = 0; i < TcArray.length() - 1; i++)
//                finalHistoryArray.put(TcArray.getJSONObject(i));
//
//            JSONObject newLastObject = new JSONObject();
//            newLastObject.put("senderSign", senderSign);
//            newLastObject.put("sender", senderDidIpfsHash);
//            newLastObject.put("comment", comment);
//            newLastObject.put("tid", tid);
//            newLastObject.put("nextHash", "");
//            newLastObject.put("role", "Sender");
//            newLastObject.put("previousHash", calculateHash(finalHistoryArray.getJSONObject(finalHistoryArray.length() - 1).toString(), "SHA3-256"));
//            newLastObject.put("amount", amount);
//
//            finalHistoryArray.put(newLastObject);
//
//            for (int i = 0; i < finalHistoryArray.length(); i++) {
//                JSONObject object = finalHistoryArray.getJSONObject(i);
//                if (i == 0) {
//                    object.put("previousHash", calculateHash(new JSONObject().toString(), "SHA3-256"));
//                    object.put("nextHash", calculateHash(finalHistoryArray.getJSONObject(i + 1).getString("tid"), "SHA3-256"));
//                }else if(i == finalHistoryArray.length() - 1){
//                    object.put("previousHash", calculateHash(finalHistoryArray.getJSONObject(i - 1).getString("tid"), "SHA3-256"));
//                    object.put("nextHash", "");
//                } else {
//                    object.put("previousHash", calculateHash(finalHistoryArray.getJSONObject(i - 1).getString("tid"), "SHA3-256"));
//                    object.put("nextHash", calculateHash(finalHistoryArray.getJSONObject(i + 1).getString("tid"), "SHA3-256"));
//                }
//
//            }
//            writeToFile(PART_TOKEN_CHAIN_PATH + tokens + ".json", finalHistoryArray.toString(), false);
//
//            debugObject.put("New tokenChain Content: ", finalHistoryArray);
////            for (int i = 0; i < finalHistoryArray.length(); i++) {
////                JSONObject object = finalHistoryArray.getJSONObject(i);
////                if (i == 0) {
////                    object.put("previousHash", calculateHash(new JSONObject().toString(), "SHA3-256"));
////                    JSONObject nextObject = finalHistoryArray.getJSONObject(i + 1);
////                    object.put("nextHash", calculateHash(nextObject.getString("tid"), "SHA3-256"));
////                } else {
////                    JSONObject previousObject = finalHistoryArray.getJSONObject(i - 1);
////                    object.put("previousHash", calculateHash(previousObject.getString("tid"), "SHA3-256"));
////                    JSONObject nextObject = finalHistoryArray.getJSONObject(i + 1);
////                    object.put("nextHash", calculateHash(nextObject.getString("tid"), "SHA3-256"));
////                }
////
////            }
////            writeToFile(PART_TOKEN_CHAIN_PATH + tokens + ".json", finalHistoryArray.toString(), false);
//        } else{
//            debugObject.put("Debug: ", "TC length is 0");
//            JSONArray finalHistoryArray = new JSONArray();
//
//            JSONObject newLastObject = new JSONObject();
//            newLastObject.put("senderSign", senderSign);
//            newLastObject.put("sender", senderDidIpfsHash);
//            newLastObject.put("comment", comment);
//            newLastObject.put("tid", tid);
//            newLastObject.put("nextHash", "");
//            newLastObject.put("role", "Sender");
//            newLastObject.put("previousHash", calculateHash(new JSONObject().toString(), "SHA3-256"));
//            newLastObject.put("amount", amount);
//
//            finalHistoryArray.put(newLastObject);
//
//            debugObject.put("New tokenChain Content: ", finalHistoryArray);
//            writeToFile(PART_TOKEN_CHAIN_PATH + tokens + ".json", finalHistoryArray.toString(), false);
//        }

        float availableParts = 0, senderCount = 0, receiverCount = 0;
        for (int i = 0; i < chainArray.length(); i++) {
            if (chainArray.getJSONObject(i).has("role")) {
                if (chainArray.getJSONObject(i).getString("role").equals("Sender") && chainArray.getJSONObject(i).getString("sender").equals(senderDidIpfsHash)) {
                    senderCount += chainArray.getJSONObject(i).getFloat("amount");
                } else if (chainArray.getJSONObject(i).getString("role").equals("Receiver") && chainArray.getJSONObject(i).getString("receiver").equals(senderDidIpfsHash)) {
                    receiverCount += chainArray.getJSONObject(i).getFloat("amount");
                }
            }
        }

        if ((senderCount - receiverCount) > 0)
            availableParts = 1 - (senderCount - receiverCount);

        if (availableParts >= 1) {
            TokenPartsSenderLogger.debug("Wholly Spent, Removing token from parts");
            String partFileContent2 = readFile(PAYMENTS_PATH.concat("PartsToken.json"));
            JSONArray partContentArray2 = new JSONArray(partFileContent2);
            for (int i = 0; i < partContentArray2.length(); i++) {
                if (partContentArray2.getJSONObject(i).getString("tokenHash").equals(tokens))
                    partContentArray2.remove(i);
                writeToFile(PAYMENTS_PATH.concat("PartsToken.json"), partContentArray2.toString(), false);
            }
            deleteFile(PART_TOKEN_CHAIN_PATH.concat(tokens));
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

        TokenPartsSenderLogger.debug("Times: " + Authenticate.verifyCount);
        Authenticate.verifyCount = 0;

//        //Populating data to explorer
//        if (!EXPLORER_IP.contains("127.0.0.1")) {
//            String url = EXPLORER_IP + "/CreateOrUpdateRubixTransaction";
//            URL obj = new URL(url);
//            HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
//
//            // Setting basic post request
//            con.setRequestMethod("POST");
//            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
//            con.setRequestProperty("Accept", "application/json");
//            con.setRequestProperty("Content-Type", "application/json");
//            con.setRequestProperty("Authorization", "null");
//
//            // Serialization
//            JSONObject dataToSend = new JSONObject();
//            dataToSend.put("transaction_id", tid);
//            dataToSend.put("sender_did", senderDidIpfsHash);
//            dataToSend.put("receiver_did", receiverDidIpfsHash);
//            dataToSend.put("token_id", tokens);
//            dataToSend.put("token_time", (int) totalTime);
//            dataToSend.put("amount", amount);
//            String populate = dataToSend.toString();
//
//            JSONObject jsonObject = new JSONObject();
//            jsonObject.put("inputString", populate);
//            String postJsonData = jsonObject.toString();
//
//            // Send post request
//            con.setDoOutput(true);
//            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
//            wr.writeBytes(postJsonData);
//            wr.flush();
//            wr.close();
//
//            int responseCode = con.getResponseCode();
//            TokenPartsSenderLogger.debug("Sending 'POST' request to URL : " + url);
//            TokenPartsSenderLogger.debug("Post Data : " + postJsonData);
//            TokenPartsSenderLogger.debug("Response Code : " + responseCode);
//
//            BufferedReader in = new BufferedReader(
//                    new InputStreamReader(con.getInputStream()));
//            String outputCode;
//            StringBuffer response = new StringBuffer();
//
//            try {
//                while ((outputCode = in.readLine()) != null) {
//                    response.append(outputCode);
//                }
//                in.close();
//            }
//            catch (IOException exception){
//                TokenPartsSenderLogger.debug("Explorer not responded");
//            }
//
//            TokenPartsSenderLogger.debug(response.toString());
//        }


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

        TokenPartsSenderLogger.info("Transaction Successful");
        executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
        output.close();
        input.close();
        senderSocket.close();
        senderMutex = false;
        return APIResponse;

    }
}
