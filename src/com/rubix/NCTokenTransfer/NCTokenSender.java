package com.rubix.NCTokenTransfer;

import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Consensus.InitiatorConsensus;
import com.rubix.Consensus.InitiatorProcedure;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;
import com.rubix.TokenTransfer.TransferPledge.Initiator;
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
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;

public class NCTokenSender {
    private static final Logger NCTokenSenderLogger = Logger.getLogger(NCTokenSender.class);
    private static final Logger eventLogger = Logger.getLogger("eventLogger");

    public static BufferedReader serverInput;
    private static PrintStream output;
    private static BufferedReader input;
    private static Socket senderSocket;
    private static boolean senderMutex = false;

    /**
     * Functio to gatherPayload for pre transfer sigatures
     *
     * @param data Details required for tokenTransfer
     * @param ipfs IPFS instance
     * @param port Sender port for communication
     * @return Transaction Details (JSONObject)
     * @throws IOException              handles IO Exceptions
     * @throws JSONException            handles JSON Exceptions
     * @throws NoSuchAlgorithmException handles No Such Algorithm Exceptions
     */
    public static JSONObject preProcess(String data, IPFS ipfs) throws Exception {
        NCTokenSenderLogger.debug("PreProcess Phase started for NC wallet txn. ");
        repo(ipfs);
        JSONObject APIResponse = new JSONObject();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONObject detailsObject = new JSONObject(data);
        String receiverDidIpfsHash = detailsObject.getString("receiverDidIpfsHash");
        syncDataTableByDID(receiverDidIpfsHash);
        String receiverPeerId = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", receiverDidIpfsHash);
        double requestedAmount = detailsObject.getDouble("amount");
        String comment = detailsObject.getString("comment");
        APIResponse = new JSONObject();

        int intPart = (int) requestedAmount, wholeAmount;
        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        NCTokenSenderLogger.debug("sender peer id" + senderPeerID);
        String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);
        NCTokenSenderLogger.debug("sender did ipfs hash" + senderDidIpfsHash);

        if (senderMutex) {
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Sender busy. Try again later");
            NCTokenSenderLogger.warn("Sender busy");
            return APIResponse;
        }

        senderMutex = true;

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

        NCTokenSenderLogger.debug("Requested Part: " + requestedAmount);
        NCTokenSenderLogger.debug("Int Part: " + intPart);
        String bankFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
        JSONArray bankArray = new JSONArray(bankFile);
        JSONArray wholeTokens = new JSONArray();
        if (intPart <= bankArray.length())
            wholeAmount = intPart;
        else
            wholeAmount = bankArray.length();

        for (int i = 0; i < wholeAmount; i++) {
            wholeTokens.put(bankArray.getJSONObject(i).getString("tokenHash"));
        }

        for (int i = 0; i < wholeTokens.length(); i++) {
            String tokenRemove = wholeTokens.getString(i);
            for (int j = 0; j < bankArray.length(); j++) {
                if (bankArray.getJSONObject(j).getString("tokenHash").equals(tokenRemove))
                    bankArray.remove(j);
            }
        }
        JSONArray wholeTokenChainHash = new JSONArray();
        JSONArray tokenPreviousSender = new JSONArray();

        for (int i = 0; i < wholeTokens.length(); i++) {
            File token = new File(TOKENS_PATH + wholeTokens.get(i));
            File tokenchain = new File(TOKENCHAIN_PATH + wholeTokens.get(i) + ".json");
            if (!(token.exists() && tokenchain.exists())) {
                NCTokenSenderLogger.info("Tokens Not Verified");
                senderMutex = false;
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Invalid token(s)");
                return APIResponse;

            }
            String wholeTokenHash = add(TOKENS_PATH + wholeTokens.get(i), ipfs);
            pin(wholeTokenHash, ipfs);
            String tokenChainHash = add(TOKENCHAIN_PATH + wholeTokens.get(i) + ".json", ipfs);
            wholeTokenChainHash.put(tokenChainHash);

            String tokenChainFileContent = readFile(TOKENCHAIN_PATH + wholeTokens.get(i) + ".json");
            JSONArray tokenChainFileArray = new JSONArray(tokenChainFileContent);
            JSONArray previousSenderArray = new JSONArray();

            if (tokenChainFileArray.length() > 0) {

                for (int j = 0; j < tokenChainFileArray.length(); j++) {
                    String peerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
                            tokenChainFileArray.getJSONObject(j).getString("sender"));
                    previousSenderArray.put(peerID);
                }
            }

            JSONObject previousSenderObject = new JSONObject();
            previousSenderObject.put("token", wholeTokenHash);
            previousSenderObject.put("sender", previousSenderArray);
            tokenPreviousSender.put(previousSenderObject);

        }

        Double decimalAmount = requestedAmount - wholeAmount;
        decimalAmount = formatAmount(decimalAmount);

        NCTokenSenderLogger.debug("Decimal Part: " + decimalAmount);
        boolean newPart = false, oldNew = false;
        JSONObject amountLedger = new JSONObject();

        JSONArray partTokens = new JSONArray();
        JSONArray partTokenChainHash = new JSONArray();
        if (decimalAmount > 0.000D) {
            NCTokenSenderLogger.debug("Decimal Amount > 0.000D");
            String partFileContent = readFile(partTokensFile.toString());
            JSONArray partContentArray = new JSONArray(partFileContent);

            if (partContentArray.length() == 0) {
                newPart = true;
                NCTokenSenderLogger.debug("New token for parts");
                String chosenToken = bankArray.getJSONObject(0).getString("tokenHash");
                partTokens.put(chosenToken);
                amountLedger.put(chosenToken, formatAmount(decimalAmount));

            } else {
                Double counter = decimalAmount;
                JSONArray selectParts = new JSONArray(partFileContent);
                while (counter > 0.000D) {
                    counter = formatAmount(counter);
                    NCTokenSenderLogger.debug("Counter: " + formatAmount(counter));
                    if (!(selectParts.length() == 0)) {
                        NCTokenSenderLogger.debug("Old Parts");
                        String currentPartToken = selectParts.getJSONObject(0).getString("tokenHash");
                        Double currentPartBalance = partTokenBalance(currentPartToken);
                        currentPartBalance = formatAmount(currentPartBalance);
                        if (counter >= currentPartBalance)
                            amountLedger.put(currentPartToken, formatAmount(currentPartBalance));
                        else
                            amountLedger.put(currentPartToken, formatAmount(counter));

                        partTokens.put(currentPartToken);
                        counter -= currentPartBalance;
                        selectParts.remove(0);
                    } else {
                        oldNew = true;
                        NCTokenSenderLogger.debug("Old Parts then new parts");
                        String chosenToken = bankArray.getJSONObject(0).getString("tokenHash");
                        partTokens.put(chosenToken);
                        amountLedger.put(chosenToken, formatAmount(counter));
                        File tokenFile = new File(TOKENS_PATH.concat(chosenToken));
                        tokenFile.renameTo(new File(PART_TOKEN_PATH.concat(chosenToken)));
                        File chainFile = new File(TOKENCHAIN_PATH.concat(chosenToken).concat(".json"));
                        chainFile.renameTo(new File(PART_TOKEN_CHAIN_PATH.concat(chosenToken).concat(".json")));

                        File shiftedFile = new File(PAYMENTS_PATH.concat("ShiftedTokens.json"));
                        if (!shiftedFile.exists()) {
                            shiftedFile.createNewFile();
                            JSONArray shiftedTokensArray = new JSONArray();
                            shiftedTokensArray.put(chosenToken);
                            writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedTokensArray.toString(),
                                    false);
                        } else {
                            String shiftedContent = readFile(PAYMENTS_PATH.concat("ShiftedTokens.json"));
                            JSONArray shiftedArray = new JSONArray(shiftedContent);
                            shiftedArray.put(chosenToken);
                            writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedArray.toString(), false);
                        }
                        counter = 0.000D;
                    }
                }
            }
        }
        String tokenChainPath = "", tokenPath = "";
        if (newPart) {
            tokenChainPath = TOKENCHAIN_PATH;
            tokenPath = TOKENS_PATH;
        } else {
            tokenChainPath = TOKENCHAIN_PATH.concat("PARTS/");
            tokenPath = TOKENS_PATH.concat("PARTS/");
        }

        NCTokenSenderLogger.debug("Tokenchain path: " + tokenChainPath);
        NCTokenSenderLogger.debug("Token path: " + tokenPath);
        for (int i = 0; i < partTokens.length(); i++) {
            File token = new File(tokenPath.concat(partTokens.getString(i)));
            File tokenchain = new File(tokenChainPath.concat(partTokens.getString(i)) + ".json");
            if (!(token.exists() && tokenchain.exists())) {
                if (!token.exists())
                    NCTokenSenderLogger.debug("Token File for parts not avail");
                if (!tokenchain.exists())
                    NCTokenSenderLogger.debug("Token Chain File for parts not avail");

                NCTokenSenderLogger.info("Tokens Not Verified");
                senderMutex = false;
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Invalid part token(s)");
                return APIResponse;

            }
            String hash = add(tokenPath + partTokens.getString(i), ipfs);
            pin(hash, ipfs);

            String chainContent = readFile(tokenChainPath.concat(partTokens.getString(i)).concat(".json"));
            JSONArray chainArray = new JSONArray();
            JSONArray finalChainArray = new JSONArray(chainContent);
            for (int j = 0; j < finalChainArray.length(); j++) {
                JSONObject object = finalChainArray.getJSONObject(j);
                if (finalChainArray.length() == 1) {
                    object.put("previousHash", "");
                    object.put("nextHash", "");
                } else if (finalChainArray.length() > 1) {
                    if (j == 0) {
                        object.put("previousHash", "");
                        object.put("nextHash",
                                calculateHash(finalChainArray.getJSONObject(j + 1).getString("tid"), "SHA3-256"));
                    } else if (j == finalChainArray.length() - 1) {
                        object.put("previousHash",
                                calculateHash(finalChainArray.getJSONObject(j - 1).getString("tid"), "SHA3-256"));
                        object.put("nextHash", "");
                    } else {
                        object.put("previousHash",
                                calculateHash(finalChainArray.getJSONObject(j - 1).getString("tid"), "SHA3-256"));
                        object.put("nextHash",
                                calculateHash(finalChainArray.getJSONObject(j + 1).getString("tid"), "SHA3-256"));
                    }
                }
                chainArray.put(object);

            }
            writeToFile(tokenChainPath.concat(partTokens.getString(i)).concat(".json"), chainArray.toString(), false);

            partTokenChainHash.put(add(tokenChainPath.concat(partTokens.getString(i)).concat(".json"), ipfs));
        }

        String authSenderByRecHash = calculateHash(
                wholeTokens.toString() + wholeTokenChainHash.toString() + partTokens.toString()
                        + partTokenChainHash.toString() + receiverDidIpfsHash + senderDidIpfsHash + comment,
                "SHA3-256");

        NCTokenSenderLogger.debug("Hash to verify Sender: " + authSenderByRecHash);
        String tid = calculateHash(authSenderByRecHash, "SHA3-256");
        NCTokenSenderLogger.debug("Sender by Receiver Hash " + authSenderByRecHash);
        NCTokenSenderLogger.debug("TID on sender " + tid);

        JSONArray allTokens = new JSONArray();
        for (int i = 0; i < wholeTokens.length(); i++)
            allTokens.put(wholeTokens.getString(i));
        for (int i = 0; i < partTokens.length(); i++)
            allTokens.put(partTokens.getString(i));

        JSONObject partTokenChainArrays = new JSONObject();
        for (int i = 0; i < partTokens.length(); i++) {
            String chainContent = readFile(tokenChainPath.concat(partTokens.getString(i)).concat(".json"));
            JSONArray chainArray = new JSONArray(chainContent);
            JSONObject newLastObject = new JSONObject();
            if (chainArray.length() == 0) {
                newLastObject.put("previousHash", "");

            } else {
                JSONObject secondLastObject = chainArray.getJSONObject(chainArray.length() - 1);
                secondLastObject.put("nextHash", calculateHash(tid, "SHA3-256"));
                newLastObject.put("previousHash",
                        calculateHash(chainArray.getJSONObject(chainArray.length() - 1).getString("tid"), "SHA3-256"));
            }

            Double amount = formatAmount(amountLedger.getDouble(partTokens.getString(i)));

            newLastObject.put("sender", senderDidIpfsHash);
            newLastObject.put("receiver", receiverDidIpfsHash);
            newLastObject.put("comment", comment);
            newLastObject.put("tid", tid);
            newLastObject.put("nextHash", "");
            newLastObject.put("role", "Sender");
            newLastObject.put("amount", amount);
            chainArray.put(newLastObject);
            partTokenChainArrays.put(partTokens.getString(i), chainArray);

        }

        JSONObject tokenDetails = new JSONObject();
        tokenDetails.put("whole-tokens", wholeTokens);
        tokenDetails.put("whole-tokenChains", wholeTokenChainHash);
        tokenDetails.put("hashSender", partTokenChainHash);
        tokenDetails.put("part-tokens", partTokens);
        tokenDetails.put("part-tokenChains", partTokenChainArrays);
        tokenDetails.put("sender", senderDidIpfsHash);
        String doubleSpendString = tokenDetails.toString();

        JSONObject payloadObj = new JSONObject();

        JSONObject senderSignContentObj = new JSONObject();
        senderSignContentObj.put("content", authSenderByRecHash);

        payloadObj.put("senderSign", senderSignContentObj);

        JSONObject senderSignQContentObj = new JSONObject();
        senderSignQContentObj.put("content", calculateFileHash(doubleSpendString, "SHA3-256"));

        payloadObj.put("senderSignQ", senderSignQContentObj);

        JSONObject txnDetailsContentObject = new JSONObject();
        txnDetailsContentObject.put("wholeTokens", wholeTokens.toString());
        txnDetailsContentObject.put("wholeTokenChainHash", wholeTokenChainHash.toString());
        txnDetailsContentObject.put("partTokenChainHash", partTokenChainHash.toString());
        txnDetailsContentObject.put("partTokens", partTokens.toString());
        txnDetailsContentObject.put("partTokenChainArrays", partTokenChainArrays.toString());
        txnDetailsContentObject.put("amountLedger", amountLedger.toString());
        txnDetailsContentObject.put("tokenPreviousSender", tokenPreviousSender.toString());
        txnDetailsContentObject.put("doubleSpendString", doubleSpendString);
        txnDetailsContentObject.put("receiverDidIpfsHash", receiverDidIpfsHash);
        txnDetailsContentObject.put("requestedAmount", requestedAmount);
        txnDetailsContentObject.put("comment", comment);
        txnDetailsContentObject.put("tid", tid);
        txnDetailsContentObject.put("allTokens", allTokens.toString());
        txnDetailsContentObject.put("newPart", newPart);
        txnDetailsContentObject.put("oldNew", oldNew);

        payloadObj.put("txnDetails", txnDetailsContentObject);

        APIResponse.put("did", senderDidIpfsHash);
        APIResponse.put("tid", tid);
        APIResponse.put("status", "Success");
        APIResponse.put("message", "Payload for Txn with id " + tid + " generated");
        APIResponse.put("payload", payloadObj.toString());
        return APIResponse;

    }

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
    public static JSONObject Send(String data, IPFS ipfs, int port) {
        JSONObject APIResponse = new JSONObject();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        try {
            JSONObject detailsObject = new JSONObject(data);

            String receiverDidIpfsHash = detailsObject.getString("receiverDidIpfsHash");
            String comment = detailsObject.getString("comment");
            double requestedAmount = detailsObject.getDouble("requestedAmount");
            int type = detailsObject.getInt("type");

            int wholeAmount = 0;
            int intPart = (int) requestedAmount;
            Double decimalAmount = requestedAmount - wholeAmount;
            decimalAmount = formatAmount(decimalAmount);
            boolean newPart = false, oldNew = false;

            JSONObject payloadSigned = new JSONObject(detailsObject.getString("payloadSigned"));

            String positions = payloadSigned.getString("positions");

            JSONObject senderSignObject = new JSONObject(payloadSigned.getJSONObject("senderSign"));
            String senderSign = senderSignObject.getString("signature");
            String authSenderByRecHash = senderSignObject.getString("content");

            JSONObject senderSignQObject = new JSONObject(payloadSigned.getJSONObject("senderSignQ"));
            String senderSignQ = senderSignQObject.getString("signature");
            String authSenderByQuorumHash = senderSignQObject.getString("content");

            JSONObject txnDetailsObject = new JSONObject(payloadSigned.getJSONObject("txnDetails"));
            JSONArray wholeTokens = new JSONArray(txnDetailsObject.getString("wholeTokens"));
            JSONArray wholeTokenChainHash = new JSONArray(txnDetailsObject.getString("wholeTokenChainHash"));
            JSONArray partTokenChainHash = new JSONArray(txnDetailsObject.getString("partTokenChainHash"));
            JSONArray partTokens = new JSONArray(txnDetailsObject.getString("partTokens"));
            JSONObject partTokenChainArrays = new JSONObject(txnDetailsObject.getString("partTokenChainArrays"));
            JSONObject amountLedger = new JSONObject(txnDetailsObject.getString("amountLedger"));
            JSONArray tokenPreviousSender = new JSONArray(txnDetailsObject.getString("tokenPreviousSender"));
            String doubleSpendString = txnDetailsObject.getString("doubleSpendString");
            String tid = txnDetailsObject.getString("tid");
            JSONArray allTokens = new JSONArray(txnDetailsObject.getString("allTokens"));
            newPart = txnDetailsObject.getBoolean("newPart");
            oldNew = txnDetailsObject.getBoolean("oldNew");

            syncDataTableByDID(receiverDidIpfsHash);
            String receiverPeerId = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", receiverDidIpfsHash);
            String senderPeerID = getPeerID(DATA_PATH + "DID.json");
            NCTokenSenderLogger.debug("sender peer id" + senderPeerID);
            String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);
            NCTokenSenderLogger.debug("sender did ipfs hash" + senderDidIpfsHash);

            if (senderMutex) {
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender busy. Try again later");
                NCTokenSenderLogger.warn("Sender busy");
                return APIResponse;
            }

            boolean sanityCheck = sanityCheck("Receiver", receiverPeerId, ipfs, port + 10);
            if (!sanityCheck) {
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", sanityMessage);
                NCTokenSenderLogger.warn(sanityMessage);
                senderMutex = false;
                return APIResponse;
            }
            String PART_TOKEN_CHAIN_PATH = TOKENCHAIN_PATH.concat("PARTS/");
            String PART_TOKEN_PATH = TOKENS_PATH.concat("PARTS/");
            JSONArray alphaQuorum = new JSONArray();
            JSONArray betaQuorum = new JSONArray();
            JSONArray gammaQuorum = new JSONArray();
            JSONArray quorumArray = new JSONArray();
            switch (type) {
                case 1: {
                    writeToFile(LOGGER_PATH + "tempbeta", tid.concat(senderDidIpfsHash), false);
                    String betaHash = IPFSNetwork.add(LOGGER_PATH + "tempbeta", ipfs);
                    deleteFile(LOGGER_PATH + "tempbeta");

                    writeToFile(LOGGER_PATH + "tempgamma", tid.concat(receiverDidIpfsHash), false);
                    String gammaHash = IPFSNetwork.add(LOGGER_PATH + "tempgamma", ipfs);
                    deleteFile(LOGGER_PATH + "tempgamma");

                    quorumArray = getQuorum(senderDidIpfsHash, receiverDidIpfsHash,
                            allTokens.length());
                    break;
                }

                case 2: {
                    File quorumFile = new File(DATA_PATH.concat("quorumlist.json"));
                    if (!quorumFile.exists()) {
                        NCTokenSenderLogger.error("Quorum List for Subnet not found");
                        APIResponse.put("status", "Failed");
                        APIResponse.put("message", "Quorum List for Subnet not found");
                        return APIResponse;
                    } else {
                        String quorumList = readFile(DATA_PATH + "quorumlist.json");
                        if (quorumList != null) {
                            quorumArray = new JSONArray(readFile(DATA_PATH + "quorumlist.json"));
                        } else {
                            NCTokenSenderLogger.error("File for Quorum List for Subnet is empty");
                            APIResponse.put("status", "Failed");
                            APIResponse.put("message", "File for Quorum List for Subnet is empty");
                            return APIResponse;
                        }

                    }

                    break;
                }
                case 3: {
                    quorumArray = detailsObject.getJSONArray("quorum");
                    break;
                }
                default: {
                    NCTokenSenderLogger.error("Unknown quorum type input, cancelling transaction");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Unknown quorum type input, cancelling transaction");
                    return APIResponse;

                }
            }
            int alphaSize;

            ArrayList alphaPeersList;
            ArrayList betaPeersList;
            ArrayList gammaPeersList;

            String errMessage = null;
            for (int i = 0; i < quorumArray.length(); i++) {

                if (quorumArray.get(i).equals(senderDidIpfsHash)) {
                    NCTokenSenderLogger.error("SenderDID " + senderDidIpfsHash + " cannot be a Quorum");
                    errMessage = "SenderDID " + senderDidIpfsHash;
                }
                if (quorumArray.get(i).equals(receiverDidIpfsHash)) {
                    NCTokenSenderLogger.error("ReceiverDID " + receiverDidIpfsHash + " cannot be a Quorum");
                    if (errMessage != null) {
                        errMessage = errMessage + " and ";
                    }
                    errMessage = "ReceiverDID " + receiverDidIpfsHash;
                }
                if (errMessage != null) {
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", errMessage + " cannot be a Quorum ");
                    return APIResponse;
                }

            }

            NCTokenSenderLogger.debug("Updated quorumlist is " + quorumArray.toString());

            // sanity check for Quorum - starts
            int alphaCheck = 0, betaCheck = 0, gammaCheck = 0;
            JSONArray sanityFailedQuorum = new JSONArray();
            for (int i = 0; i < quorumArray.length(); i++) {
                String quorumPeerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
                        quorumArray.getString(i));
                boolean quorumSanityCheck = sanityCheck("Quorum", quorumPeerID, ipfs, port + 10);

                if (!quorumSanityCheck) {
                    sanityFailedQuorum.put(quorumPeerID);
                    if (i <= 6)
                        alphaCheck++;
                    if (i >= 7 && i <= 13)
                        betaCheck++;
                    if (i >= 14 && i <= 20)
                        gammaCheck++;
                }
            }

            if (alphaCheck > 2 || betaCheck > 2 || gammaCheck > 2) {
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                String message = "Quorum: ".concat(sanityFailedQuorum.toString()).concat(" ");
                APIResponse.put("message", message.concat(sanityMessage));
                NCTokenSenderLogger.warn("Quorum: ".concat(message.concat(sanityMessage)));
                return APIResponse;
            }
            // sanity check for Quorum - Ends
            long startTime, endTime, totalTime;

            QuorumSwarmConnect(quorumArray, ipfs);

            alphaSize = quorumArray.length() - 14;

            for (int i = 0; i < alphaSize; i++)
                alphaQuorum.put(quorumArray.getString(i));

            for (int i = 0; i < 7; i++) {
                betaQuorum.put(quorumArray.getString(alphaSize + i));
                gammaQuorum.put(quorumArray.getString(alphaSize + 7 + i));
            }
            startTime = System.currentTimeMillis();

            alphaPeersList = QuorumCheck(alphaQuorum, alphaSize);
            betaPeersList = QuorumCheck(betaQuorum, 7);
            gammaPeersList = QuorumCheck(gammaQuorum, 7);

            endTime = System.currentTimeMillis();
            totalTime = endTime - startTime;
            eventLogger.debug("Quorum Check " + totalTime);

            if (alphaPeersList.size() < minQuorum(alphaSize) || betaPeersList.size() < 5 || gammaPeersList.size() < 5) {
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Quorum Members not available");
                NCTokenSenderLogger.warn("Quorum Members not available");
                senderMutex = false;
                return APIResponse;
            }

            NCTokenSenderLogger.debug("Final Selected Alpha Members: " + alphaPeersList);
            JSONArray alphaList = new JSONArray();
            for (int i = 0; i < alphaPeersList.size(); i++) {
                alphaList.put(alphaPeersList.get(i));
            }

            int numberOfTokensToPledge = 0;
            if (wholeAmount > 0) {
                numberOfTokensToPledge += wholeAmount;
                if (decimalAmount > 0)
                    numberOfTokensToPledge += 1;
            } else
                numberOfTokensToPledge = 1;

            NCTokenSenderLogger.debug("Amount being transferred: " + requestedAmount
                    + " and number of tokens required to be pledged: " + numberOfTokensToPledge);

            JSONObject dataToSendToInitiator = new JSONObject();
            dataToSendToInitiator.put("alphaList", alphaList);
            dataToSendToInitiator.put("tokenList", wholeTokens);
            dataToSendToInitiator.put("amount", numberOfTokensToPledge);
            dataToSendToInitiator.put("tid", tid);

            NCTokenSenderLogger.debug("Details being sent to Initiator: " + dataToSendToInitiator);

            boolean abort = Initiator.pledgeSetUp(dataToSendToInitiator.toString(), ipfs, 22143);
            if (abort) {
                Initiator.abort = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                if (Initiator.abortReason.has("Quorum"))
                    APIResponse.put("message", "Alpha Node " + Initiator.abortReason.getString("Quorum") + " "
                            + Initiator.abortReason.getString("Reason"));
                else
                    APIResponse.put("message", Initiator.abortReason.getString("Reason"));
                NCTokenSenderLogger.warn("Quorum Members with insufficient Tokens/Credits");
                senderMutex = false;
                Initiator.abortReason = new JSONObject();
                return APIResponse;
            }

            NCTokenSenderLogger.debug("Nodes that pledged tokens: " + Initiator.pledgedNodes);

            syncDataTable(receiverDidIpfsHash, null);

            if (!receiverPeerId.equals("")) {
                NCTokenSenderLogger.debug("Swarm connecting to " + receiverPeerId);
                swarmConnectP2P(receiverPeerId, ipfs);
                NCTokenSenderLogger.debug("Swarm connected");
            } else {
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver Peer ID null");
                NCTokenSenderLogger.warn("Receiver Peer ID null");
                senderMutex = false;
                return APIResponse;
            }

            String receiverWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash",
                    receiverDidIpfsHash);
            if (!receiverWidIpfsHash.equals("")) {
                nodeData(receiverDidIpfsHash, receiverWidIpfsHash, ipfs);
            } else {
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver WID null");
                NCTokenSenderLogger.warn("Receiver WID null");
                senderMutex = false;
                return APIResponse;
            }

            NCTokenSenderLogger
                    .debug("Sender IPFS forwarding to DID: " + receiverDidIpfsHash + " PeerID: " + receiverPeerId);
            forward(receiverPeerId, port, receiverPeerId);
            NCTokenSenderLogger.debug("Forwarded to " + receiverPeerId + " on " + port);
            senderSocket = new Socket("127.0.0.1", port);

            input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
            output = new PrintStream(senderSocket.getOutputStream());

            startTime = System.currentTimeMillis();

            /**
             * Sending Sender Peer ID to Receiver
             * Receiver to authenticate Sender's DID (Identity)
             */
            output.println(senderPeerID);
            NCTokenSenderLogger.debug("Sent PeerID");

            String peerAuth;
            try {
                peerAuth = input.readLine();
            } catch (SocketException e) {
                NCTokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Sender Auth");
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

            if (peerAuth == null) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                NCTokenSenderLogger.info("Receiver is unable to authenticate the sender!");
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver is unable to authenticate the sender!");
                return APIResponse;

            } else if (peerAuth != null && (!peerAuth.equals("200"))) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                NCTokenSenderLogger.info("Sender Data Not Available");
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
            JSONObject senderDetails2Receiver = new JSONObject();
            senderDetails2Receiver.put("sign", senderSign);
            senderDetails2Receiver.put("tid", tid);
            senderDetails2Receiver.put("comment", comment);

            String doubleSpend = calculateHash(doubleSpendString, "SHA3-256");
            writeToFile(LOGGER_PATH + "doubleSpend", doubleSpend, false);
            NCTokenSenderLogger.debug("********Double Spend Hash*********:  " + doubleSpend);
            IPFSNetwork.addHashOnly(LOGGER_PATH + "doubleSpend", ipfs);
            deleteFile(LOGGER_PATH + "doubleSpend");

            JSONObject tokenObject = new JSONObject();
            tokenObject.put("tokenDetails", doubleSpendString);
            tokenObject.put("previousSender", tokenPreviousSender);
            tokenObject.put("positions", positions);
            tokenObject.put("amount", requestedAmount);
            tokenObject.put("amountLedger", amountLedger);

            if (Functions.multiplePinCheck(senderDidIpfsHash, tokenObject, ipfs) == 420) {
                APIResponse.put("message", "Multiple Owners Found. Kindly re-initiate transaction");
                return APIResponse;
            } else {
                NCTokenSenderLogger.debug("No Multiple Pins found, initating transcation");
            }

            /**
             * Sending Token Details to Receiver
             * Receiver to authenticate Tokens (Double Spending, IPFS availability)
             */
            output.println(tokenObject);

            String tokenAuth;
            try {
                tokenAuth = input.readLine();
                NCTokenSenderLogger.debug("Token Auth Code: " + tokenAuth);
            } catch (SocketException e) {
                NCTokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Token Auth");
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
            if (tokenAuth == null) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                NCTokenSenderLogger.info("Receiver is unable to verify the tokens!");
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver is unable to verify the tokens!");
                return APIResponse;
            } else if (tokenAuth != null && (tokenAuth.startsWith("4"))) {
                switch (tokenAuth) {
                    case "419":
                        String pledgedTokens = input.readLine();
                        JSONArray pledgedTokensArray = new JSONArray(pledgedTokens);
                        NCTokenSenderLogger.info("These tokens are pledged " + pledgedTokensArray);
                        NCTokenSenderLogger.info("Kindly re-initiate transaction");
                        APIResponse.put("message",
                                "Pledged Tokens " + pledgedTokensArray + ". Kindly re-initiate transaction");
                        File pledgeFile = new File(PAYMENTS_PATH.concat("PledgedTokens.json"));
                        if (!pledgeFile.exists()) {
                            pledgeFile.createNewFile();
                            writeToFile(PAYMENTS_PATH.concat("PledgedTokens.json"), pledgedTokensArray.toString(),
                                    false);
                        } else {
                            String pledgedContent = readFile(PAYMENTS_PATH.concat("PledgedTokens.json"));
                            JSONArray pledgedArray = new JSONArray(pledgedContent);
                            for (int i = 0; i < pledgedTokensArray.length(); i++) {
                                pledgedArray.put(pledgedTokensArray.getJSONObject(i));
                            }
                            writeToFile(PAYMENTS_PATH.concat("PledgedTokens.json"), pledgedArray.toString(), false);
                        }
                        break;
                    case "420":
                        String doubleSpent = input.readLine();
                        String owners = input.readLine();
                        JSONArray ownersArray = new JSONArray(owners);
                        NCTokenSenderLogger.info("Multiple Owners for " + doubleSpent);
                        NCTokenSenderLogger.info("Owners " + ownersArray);
                        NCTokenSenderLogger.info("Kindly re-initiate transaction");
                        APIResponse.put("message", "Multiple Owners for " + doubleSpent + " Owners: " + ownersArray
                                + ". Kindly re-initiate transaction");
                        break;
                    case "421":
                        NCTokenSenderLogger.info("Consensus ID not unique. Kindly re-initiate transaction");
                        APIResponse.put("message", "Consensus ID not unique. Kindly re-initiate transaction");
                        break;
                    case "422":
                        NCTokenSenderLogger.info("Tokens Not Verified. Kindly re-initiate transaction");
                        APIResponse.put("message", "Tokens Not Verified. Kindly re-initiate transaction");
                        break;
                    case "423":
                        NCTokenSenderLogger.info("Broken Cheque Chain. Kindly re-initiate transaction");
                        APIResponse.put("message", "Broken Cheque Chain. Kindly re-initiate transaction");
                        break;

                    case "424":
                        String invalidTokens = input.readLine();
                        JSONArray tokensArray = new JSONArray(invalidTokens);
                        NCTokenSenderLogger.info("Ownership Check Failed for " + tokensArray);
                        APIResponse.put("message", "Ownership Check Failed");
                        break;

                    case "425":
                        NCTokenSenderLogger.info("Token wholly spent already. Kindly re-initiate transaction");
                        APIResponse.put("message", "Token wholly spent already. Kindly re-initiate transaction");
                        break;

                    case "426":
                        NCTokenSenderLogger.info("Contains Invalid Tokens. Kindly check tokens in your wallet");
                        APIResponse.put("message",
                                "Contains Invalid Tokens. Kindly check tokens in your wallet");
                        break;

                }
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                return APIResponse;
            }

            NCTokenSenderLogger.debug("Token Auth Code: " + tokenAuth);

            JSONObject dataObject = new JSONObject();
            dataObject.put("tid", tid);
            dataObject.put("message", doubleSpendString);
            dataObject.put("receiverDidIpfs", receiverDidIpfsHash);
            dataObject.put("authSenderByQuorumHash", authSenderByQuorumHash);
            dataObject.put("senderSignQ", senderSignQ);
            dataObject.put("senderDidIpfs", senderDidIpfsHash);
            dataObject.put("token", wholeTokens.toString());
            dataObject.put("alphaList", alphaPeersList);
            dataObject.put("betaList", betaPeersList);
            dataObject.put("gammaList", gammaPeersList);

            InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 100, alphaSize, "");

            if (InitiatorConsensus.quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
                NCTokenSenderLogger.debug("Consensus Failed");
                senderDetails2Receiver.put("status", "Consensus Failed");
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

            NCTokenSenderLogger.debug("Consensus Reached");
            senderDetails2Receiver.put("status", "Consensus Reached");
            senderDetails2Receiver.put("quorumsign", InitiatorConsensus.quorumSignature.toString());

            output.println(senderDetails2Receiver);
            NCTokenSenderLogger.debug("Quorum Signatures length " + InitiatorConsensus.quorumSignature.length());

            String signatureAuth;
            try {
                signatureAuth = input.readLine();
            } catch (SocketException e) {
                NCTokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Signature Auth");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message",
                        "Receiver " + receiverDidIpfsHash + "is unable to respond! - Signature Auth");

                return APIResponse;
            }
            NCTokenSenderLogger.info("signatureAuth : " + signatureAuth);
            endTime = System.currentTimeMillis();
            totalTime = endTime - startTime;
            if (signatureAuth == null) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                NCTokenSenderLogger.info("Receiver is unable to authenticate Sender!");
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver is unable to authenticate Sender!");
                return APIResponse;

            } else if (signatureAuth != null && (!signatureAuth.equals("200"))) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                NCTokenSenderLogger.info("Authentication Failed!");
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
            NCTokenSenderLogger.debug("Unpinned Tokens");
            output.println("Unpinned");

            String confirmation;
            try {
                confirmation = input.readLine();
            } catch (SocketException e) {
                NCTokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Pinning Auth");
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
            if (confirmation == null) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                NCTokenSenderLogger.info("Receiver is unable to Pin the tokens!");
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver is unable to Pin the tokens!");
                return APIResponse;

            } else if (confirmation != null && (!confirmation.equals("Successfully Pinned"))) {
                NCTokenSenderLogger.warn("Multiple Owners for the token");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                NCTokenSenderLogger.info("Tokens with multiple pins");
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

            NCTokenSenderLogger.debug("3");
            NCTokenSenderLogger.debug("Whole tokens: " + wholeTokens);
            NCTokenSenderLogger.debug("Part tokens: " + partTokens);
            output.println(InitiatorProcedure.essential);

            String respAuth;
            try {
                respAuth = input.readLine();
            } catch (SocketException e) {
                NCTokenSenderLogger
                        .warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Share Confirmation");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message",
                        "Receiver " + receiverDidIpfsHash + "is unable to respond! - Share Confirmation");

                return APIResponse;
            }
            if (respAuth == null) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
                NCTokenSenderLogger.info("Receiver is unable to complete the transaction!");
                output.close();
                input.close();
                senderSocket.close();
                senderMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver is unable to complete the transaction!");
                return APIResponse;

            } else if (respAuth != null && (!respAuth.equals("Send Response"))) {
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
                NCTokenSenderLogger.info("Incomplete Transaction");
                return APIResponse;
            }

            NCTokenSenderLogger.debug("Operation over");

            for (int i = 0; i < wholeTokens.length(); i++)
                unpin(String.valueOf(wholeTokens.get(i)), ipfs);
            repo(ipfs);

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

            JSONObject transactionRecord = new JSONObject();
            transactionRecord.put("role", "Sender");
            transactionRecord.put("tokens", allTokens);
            transactionRecord.put("txn", tid);
            transactionRecord.put("quorumList", signedQuorumList);
            transactionRecord.put("senderDID", senderDidIpfsHash);
            transactionRecord.put("receiverDID", receiverDidIpfsHash);
            transactionRecord.put("Date", getCurrentUtcTime());
            transactionRecord.put("totalTime", totalTime);
            transactionRecord.put("comment", comment);
            transactionRecord.put("essentialShare", InitiatorProcedure.essential);
            requestedAmount = formatAmount(requestedAmount);
            transactionRecord.put("amount-spent", requestedAmount);

            JSONArray transactionHistoryEntry = new JSONArray();
            transactionHistoryEntry.put(transactionRecord);

            updateJSON("add", WALLET_DATA_PATH + "TransactionHistory.json", transactionHistoryEntry.toString());

            for (int i = 0; i < wholeTokens.length(); i++) {
                deleteFile(TOKENS_PATH.concat(wholeTokens.getString(i)));
                Functions.updateJSON("remove", PAYMENTS_PATH.concat("BNK00.json"), wholeTokens.getString(i));
            }

            if (newPart) {
                NCTokenSenderLogger.debug("Updating files for new parts");
                JSONObject newPartTokenObject = new JSONObject();
                newPartTokenObject.put("tokenHash", partTokens.getString(0));
                JSONArray newPartArray = new JSONArray();
                newPartArray.put(newPartTokenObject);
                writeToFile(PAYMENTS_PATH.concat("PartsToken.json"), newPartArray.toString(), false);

                String bankNew = readFile(PAYMENTS_PATH.concat("BNK00.json"));
                JSONArray bankNewArray = new JSONArray(bankNew);
                bankNewArray.remove(0);
                writeToFile(PAYMENTS_PATH.concat("BNK00.json"), bankNewArray.toString(), false);

                String newTokenChain = readFile(TOKENCHAIN_PATH + partTokens.getString(0) + ".json");
                JSONArray chainArray = new JSONArray(newTokenChain);

                JSONObject newLastObject = new JSONObject();
                if (chainArray.length() == 0) {
                    newLastObject.put("previousHash", "");

                } else {
                    JSONObject secondLastObject = chainArray.getJSONObject(chainArray.length() - 1);
                    secondLastObject.put("nextHash", calculateHash(tid, "SHA3-256"));
                    newLastObject.put("previousHash",
                            calculateHash(chainArray.getJSONObject(chainArray.length() - 1).getString("tid"),
                                    "SHA3-256"));
                }

                Double amount = formatAmount(decimalAmount);

                newLastObject.put("senderSign", senderSign);
                newLastObject.put("sender", senderDidIpfsHash);
                newLastObject.put("receiver", receiverDidIpfsHash);
                newLastObject.put("comment", comment);
                newLastObject.put("tid", tid);
                newLastObject.put("nextHash", "");
                newLastObject.put("role", "Sender");
                newLastObject.put("amount", amount);
                chainArray.put(newLastObject);
                writeToFile(TOKENCHAIN_PATH + partTokens.getString(0) + ".json", chainArray.toString(), false);

                File tokenFile = new File(TOKENS_PATH.concat(partTokens.getString(0)));
                tokenFile.renameTo(new File(PART_TOKEN_PATH.concat(partTokens.getString(0))));
                File chainFile = new File(TOKENCHAIN_PATH.concat(partTokens.getString(0)).concat(".json"));
                chainFile.renameTo(new File(PART_TOKEN_CHAIN_PATH.concat(partTokens.getString(0)).concat(".json")));

                File shiftedFile = new File(PAYMENTS_PATH.concat("ShiftedTokens.json"));
                if (!shiftedFile.exists()) {
                    shiftedFile.createNewFile();
                    JSONArray shiftedTokensArray = new JSONArray();
                    shiftedTokensArray.put(partTokens.getString(0));
                    writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedTokensArray.toString(), false);
                } else {
                    String shiftedContent = readFile(PAYMENTS_PATH.concat("ShiftedTokens.json"));
                    JSONArray shiftedArray = new JSONArray(shiftedContent);
                    shiftedArray.put(partTokens.getString(0));
                    writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedArray.toString(), false);
                }
            } else {
                NCTokenSenderLogger.debug("Updating files for old parts");
                for (int i = 0; i < partTokens.length(); i++) {
                    String newTokenChain = readFile(
                            TOKENCHAIN_PATH.concat("PARTS/") + partTokens.getString(i) + ".json");
                    JSONArray chainArray = new JSONArray(newTokenChain);

                    JSONObject newLastObject = new JSONObject();
                    if (chainArray.length() == 0) {
                        newLastObject.put("previousHash", "");

                    } else {
                        JSONObject secondLastObject = chainArray.getJSONObject(chainArray.length() - 1);
                        secondLastObject.put("nextHash", calculateHash(tid, "SHA3-256"));
                        newLastObject.put("previousHash", calculateHash(
                                chainArray.getJSONObject(chainArray.length() - 1).getString("tid"), "SHA3-256"));
                    }

                    NCTokenSenderLogger
                            .debug("Amount from ledger: "
                                    + formatAmount(amountLedger.getDouble(partTokens.getString(i))));
                    Double amount = formatAmount(amountLedger.getDouble(partTokens.getString(i)));

                    newLastObject.put("senderSign", senderSign);
                    newLastObject.put("sender", senderDidIpfsHash);
                    newLastObject.put("receiver", receiverDidIpfsHash);
                    newLastObject.put("comment", comment);
                    newLastObject.put("tid", tid);
                    newLastObject.put("nextHash", "");
                    newLastObject.put("role", "Sender");
                    newLastObject.put("amount", amount);
                    chainArray.put(newLastObject);
                    writeToFile(TOKENCHAIN_PATH.concat("PARTS/").concat(partTokens.getString(i)).concat(".json"),
                            chainArray.toString(), false);

                    NCTokenSenderLogger.debug("Checking Parts Token Balance ...");
                    Double availableParts = partTokenBalance(partTokens.getString(i));
                    NCTokenSenderLogger.debug("Available: " + availableParts);
                    if (availableParts >= 1.000 || availableParts <= 0.000) {
                        NCTokenSenderLogger.debug("Wholly Spent, Removing token from parts");
                        String partFileContent2 = readFile(PAYMENTS_PATH.concat("PartsToken.json"));
                        JSONArray partContentArray2 = new JSONArray(partFileContent2);
                        for (int j = 0; j < partContentArray2.length(); j++) {
                            if (partContentArray2.getJSONObject(j).getString("tokenHash")
                                    .equals(partTokens.getString(i)))
                                partContentArray2.remove(j);
                            writeToFile(PAYMENTS_PATH.concat("PartsToken.json"), partContentArray2.toString(), false);
                        }
                        deleteFile(PART_TOKEN_PATH.concat(partTokens.getString(i)));
                    }
                }
                if (oldNew) {
                    String token = partTokens.getString(partTokens.length() - 1);
                    String bnk = readFile(PAYMENTS_PATH.concat("BNK00.json"));
                    JSONArray bnkArray = new JSONArray(bnk);
                    for (int i = 0; i < bnkArray.length(); i++) {
                        if (bnkArray.getJSONObject(i).getString("tokenHash").equals(token))
                            bnkArray.remove(i);
                    }
                    writeToFile(PAYMENTS_PATH.concat("BNK00.json"), bnkArray.toString(), false);

                    JSONArray pArray = new JSONArray();
                    JSONObject pObject = new JSONObject();
                    pObject.put("tokenHash", token);
                    pArray.put(pObject);
                    writeToFile(PAYMENTS_PATH.concat("PartsToken.json"), pArray.toString(), false);

                }
            }
            NCTokenSenderLogger.info("Transaction Successful");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
            updateQuorum(quorumArray, signedQuorumList, true, type);
            output.close();
            input.close();
            senderSocket.close();
            senderMutex = false;

            // Populating data to explorer
            if (!EXPLORER_IP.contains("127.0.0.1")) {

                List<String> tokenList = new ArrayList<>();
                for (int i = 0; i < allTokens.length(); i++)
                    tokenList.add(allTokens.getString(i));
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
                dataToSend.put("amount", requestedAmount);
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
                NCTokenSenderLogger.debug("Sending 'POST' request to URL : " + url);
                NCTokenSenderLogger.debug("Post Data : " + postJsonData);
                NCTokenSenderLogger.debug("Response Code : " + responseCode);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String output;
                StringBuffer response = new StringBuffer();

                while ((output = in.readLine()) != null) {
                    response.append(output);
                }
                in.close();

                NCTokenSenderLogger.debug(response.toString());
            }

        } catch (JSONException e) {
            // TODO: handle exception
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return APIResponse;
    }
}
