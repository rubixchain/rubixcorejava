package com.rubix.NCTokenTransfer;


import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import java.io.*;
import java.net.Socket;

import java.security.NoSuchAlgorithmException;


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
        NCTokenSenderLogger.debug("Transfer with Pledge check initiated");
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


        JSONArray payloadArray = new JSONArray();
        JSONObject positionsObj = new JSONObject();
        positionsObj.put("positions", "");

        payloadArray.put(positionsObj);

        JSONObject senderSignObj = new JSONObject();
        senderSignObj.put("content", authSenderByRecHash);

        payloadArray.put(senderSignObj);

        JSONObject senderSignQObj = new JSONObject();
        senderSignQObj.put("content", calculateFileHash(doubleSpendString,"SHA3-256"));

        payloadArray.put(senderSignQObj);

        JSONObject txnDetailsObject = new JSONObject();
        txnDetailsObject.put("wholeTokens", wholeTokens.toString());
        txnDetailsObject.put("wholeTokenChainHash", wholeTokenChainHash.toString());
        txnDetailsObject.put("partTokenChainHash", partTokenChainHash.toString());
        txnDetailsObject.put("partTokens", partTokens.toString());
        txnDetailsObject.put("partTokenChainArrays", partTokenChainArrays.toString());
        txnDetailsObject.put("amountLedger", amountLedger.toString());
        txnDetailsObject.put("tokenPreviousSender", tokenPreviousSender.toString());
        txnDetailsObject.put("doubleSpendString", doubleSpendString);

        payloadArray.put(txnDetailsObject);

        
        APIResponse.put("did", senderDidIpfsHash);
        APIResponse.put("tid", tid);
        APIResponse.put("status", "Success");
        APIResponse.put("message", "Payload for Txn with id "+tid+" generated");
        APIResponse.put("payload", payloadArray.toString());
        return APIResponse;

    }

   
}
        