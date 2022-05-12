package com.rubix.TokenTransfer;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;
import static com.rubix.Constants.ConsensusConstants.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;

import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Consensus.*;
import com.rubix.Constants.ConsensusConstants;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;  

import io.ipfs.api.IPFS;

public class TokenSender {
    private static final Logger TokenSenderLogger = Logger.getLogger(TokenSender.class);
    private static final Logger eventLogger = Logger.getLogger("eventLogger");

    public static BufferedReader serverInput;
    private static PrintStream output;
    private static BufferedReader input;
    private static Socket senderSocket;
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
        repo(ipfs);
        TokenSenderLogger.debug("data is "+data);
        JSONObject APIResponse = new JSONObject();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        JSONObject detailsObject = new JSONObject(data);
        String pvt = detailsObject.getString("pvt");
        int type = detailsObject.getInt("type");
        String comment = detailsObject.getString("comment");
        APIResponse = new JSONObject();
        
        TokenSenderLogger.debug("detailsObject is "+ detailsObject.toString());
        
        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        TokenSenderLogger.debug("sender peer id" + senderPeerID);
        String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);
        TokenSenderLogger.debug("sender did ipfs hash" + senderDidIpfsHash);
        
        if (senderMutex) {
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Sender busy. Try again later");
            TokenSenderLogger.warn("Sender busy");
            return APIResponse;
        }
        
        if(!detailsObject.has(TRANS_TYPE)) {
        	APIResponse.put("ERROR", "TRANS_TYPE not found");
            APIResponse.put("did", senderDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "TRANS_TYPE not found");
            return APIResponse;
        }
        
        senderMutex = true;
        TokenSenderLogger.debug("sender mutex is "+senderMutex);
        TokenSenderLogger.debug("Transscation type is "+ detailsObject.getString(TRANS_TYPE));
        
	        if(detailsObject.getString(TRANS_TYPE).equals(PRIMARY)) {
	        	TokenSenderLogger.debug("inside Normal trnx");
	        	double requestedAmount = detailsObject.getDouble("amount");
	            int intPart = (int) requestedAmount, wholeAmount;
	            String receiverDidIpfsHash = detailsObject.getString("receiverDidIpfsHash");
	            syncDataTableByDID(receiverDidIpfsHash);
	            String receiverPeerId = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", receiverDidIpfsHash);
	            boolean sanityCheck = sanityCheck("Receiver",receiverPeerId, ipfs, port + 10);	            
	            if (!sanityCheck) {
	                APIResponse.put("did", senderDidIpfsHash);
	                APIResponse.put("tid", "null");
	                APIResponse.put("status", "Failed");
	                APIResponse.put("message", sanityMessage);
	                TokenSenderLogger.warn(sanityMessage);
	                return APIResponse;
	            }
	            
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
	
	            TokenSenderLogger.debug("Requested Part: " + requestedAmount);
	            TokenSenderLogger.debug("Int Part: " + intPart);
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
	                    TokenSenderLogger.info("Tokens Not Verified");
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
	                JSONObject lastObject = tokenChainFileArray.getJSONObject(tokenChainFileArray.length() - 1);
	                if (lastObject.has("mineID")) {
	                    wholeTokens.remove(i);
	                }
	                for (int j = 0; j < tokenChainFileArray.length(); j++) {
	                    String peerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
	                            tokenChainFileArray.getJSONObject(j).getString("sender"));
	                    previousSenderArray.put(peerID);
	                }
	
	                JSONObject previousSenderObject = new JSONObject();
	                previousSenderObject.put("token", wholeTokenHash);
	                previousSenderObject.put("sender", previousSenderArray);
	                tokenPreviousSender.put(previousSenderObject);
	
	            }
	
	            Double decimalAmount = requestedAmount - wholeAmount;
	            decimalAmount = formatAmount(decimalAmount);
	
	            TokenSenderLogger.debug("Decimal Part: " + decimalAmount);
	            boolean newPart = false, oldNew = false;
	            JSONObject amountLedger = new JSONObject();
	
	            JSONArray partTokens = new JSONArray();
	            JSONArray partTokenChainHash = new JSONArray();
	            if (decimalAmount > 0.000D) {
	                TokenSenderLogger.debug("Decimal Amount > 0.000D");
	                String partFileContent = readFile(partTokensFile.toString());
	                JSONArray partContentArray = new JSONArray(partFileContent);
	
	                if (partContentArray.length() == 0) {
	                    newPart = true;
	                    TokenSenderLogger.debug("New token for parts");
	                    String chosenToken = bankArray.getJSONObject(0).getString("tokenHash");
	                    partTokens.put(chosenToken);
	                    amountLedger.put(chosenToken, formatAmount(decimalAmount));
	
	                } else {
	                    Double counter = decimalAmount;
	                    JSONArray selectParts = new JSONArray(partFileContent);
	                    while (counter > 0.000D) {
	                        counter = formatAmount(counter);
	                        TokenSenderLogger.debug("Counter: " + formatAmount(counter));
	                        if (!(selectParts.length() == 0)) {
	                            TokenSenderLogger.debug("Old Parts");
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
	                            TokenSenderLogger.debug("Old Parts then new parts");
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
	
	            TokenSenderLogger.debug("Tokenchain path: " + tokenChainPath);
	            TokenSenderLogger.debug("Token path: " + tokenPath);
	            for (int i = 0; i < partTokens.length(); i++) {
	                File token = new File(tokenPath.concat(partTokens.getString(i)));
	                File tokenchain = new File(tokenChainPath.concat(partTokens.getString(i)) + ".json");
	                if (!(token.exists() && tokenchain.exists())) {
	                    if (!token.exists())
	                        TokenSenderLogger.debug("Token File for parts not avail");
	                    if (!tokenchain.exists())
	                        TokenSenderLogger.debug("Token Chain File for parts not avail");
	
	                    TokenSenderLogger.info("Tokens Not Verified");
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
	            TokenSenderLogger.debug("Hash to verify Sender: " + authSenderByRecHash);
	            String tid = calculateHash(authSenderByRecHash, "SHA3-256");
	            TokenSenderLogger.debug("Sender by Receiver Hash " + authSenderByRecHash);
	            TokenSenderLogger.debug("TID on sender " + tid);
	
	            JSONArray allTokens = new JSONArray();
	            for (int i = 0; i < wholeTokens.length(); i++)
	                allTokens.put(wholeTokens.getString(i));
	            for (int i = 0; i < partTokens.length(); i++)
	                allTokens.put(partTokens.getString(i));
	
	            JSONArray positionsArray = new JSONArray();
	            for (int i = 0; i < allTokens.length(); i++) {
	                String tokens = allTokens.getString(i);
	                String hashString = tokens.concat(senderDidIpfsHash);
	                String hashForPositions = calculateHash(hashString, "SHA3-256");
	                BufferedImage privateShare = ImageIO
	                        .read(new File(DATA_PATH.concat(senderDidIpfsHash).concat("/PrivateShare.png")));
	                String firstPrivate = PropImage.img2bin(privateShare);
	                int[] privateIntegerArray1 = strToIntArray(firstPrivate);
	                String privateBinary = Functions.intArrayToStr(privateIntegerArray1);
	                String positions = "";
	                for (int j = 0; j < privateIntegerArray1.length; j += 49152) {
	                    positions += privateBinary.charAt(j);
	                }
	                positionsArray.put(positions);
	
	                TokenSenderLogger.debug("Ownership Here Sender Calculation");
	                TokenSenderLogger.debug("tokens: " + tokens);
	                TokenSenderLogger.debug("hashString: " + hashString);
	                TokenSenderLogger.debug("hashForPositions: " + hashForPositions);
	                TokenSenderLogger.debug("p1: " + positions);
	            }
	
	            JSONArray alphaQuorum = new JSONArray();
	            JSONArray betaQuorum = new JSONArray();
	            JSONArray gammaQuorum = new JSONArray();
	            int alphaSize;
	
	            ArrayList alphaPeersList;
	            ArrayList betaPeersList;
	            ArrayList gammaPeersList;
	
	            JSONArray quorumArray;
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
	
	            int alphaCheck = 0, betaCheck = 0, gammaCheck = 0;
	            JSONArray sanityFailedQuorum = new JSONArray();
	            for (int i = 0; i < quorumArray.length(); i++) {
	                String quorumPeerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
	                        quorumArray.getString(i));
	                boolean quorumSanityCheck = sanityCheck("Quorum",quorumPeerID, ipfs, port + 11);
	
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
	                TokenSenderLogger.warn("Quorum: ".concat(message.concat(sanityMessage)));
	                return APIResponse;
	            }
	
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
	                TokenSenderLogger.warn("Quorum Members not available");
	                senderMutex = false;
	                return APIResponse;
	            }
	
	            syncDataTable(receiverDidIpfsHash, null);
	            // receiverPeerId = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
	            // receiverDidIpfsHash);
	
	            if (!receiverPeerId.equals("")) {
	                TokenSenderLogger.debug("Swarm connecting to " + receiverPeerId);
	                swarmConnectP2P(receiverPeerId, ipfs);
	                TokenSenderLogger.debug("Swarm connected");
	            } else {
	                APIResponse.put("did", senderDidIpfsHash);
	                APIResponse.put("tid", "null");
	                APIResponse.put("status", "Failed");
	                APIResponse.put("message", "Receiver Peer ID null");
	                TokenSenderLogger.warn("Receiver Peer ID null");
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
	                TokenSenderLogger.warn("Receiver WID null");
	                senderMutex = false;
	                return APIResponse;
	            }
	
	            TokenSenderLogger.debug("Sender IPFS forwarding to DID: " + receiverDidIpfsHash + " PeerID: " + receiverPeerId);
	            forward(receiverPeerId, port, receiverPeerId);
	            TokenSenderLogger.debug("Forwarded to " + receiverPeerId + " on " + port);
	            senderSocket = new Socket("127.0.0.1", port);
	
	            input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
	            output = new PrintStream(senderSocket.getOutputStream());
	
	            startTime = System.currentTimeMillis();
	
	            /**
	             * Sending Sender Peer ID to Receiver
	             * Receiver to authenticate Sender's DID (Identity)
	             */
	            output.println(senderPeerID);
	            TokenSenderLogger.debug("Sent PeerID");
	
	            String peerAuth;
	            try {
	                peerAuth = input.readLine();
	            } catch (SocketException e) {
	                TokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Sender Auth");
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
	
	            String senderSign = getSignFromShares(pvt, authSenderByRecHash);
	            JSONObject senderDetails2Receiver = new JSONObject();
	            senderDetails2Receiver.put("sign", senderSign);
	            senderDetails2Receiver.put("tid", tid);
	            senderDetails2Receiver.put("comment", comment);
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
	
	                newLastObject.put("senderSign", senderSign);
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
	
	            String doubleSpend = calculateHash(doubleSpendString, "SHA3-256");
	            writeToFile(LOGGER_PATH + "doubleSpend", doubleSpend, false);
	            TokenSenderLogger.debug("********Double Spend Hash*********:  " + doubleSpend);
	            IPFSNetwork.addHashOnly(LOGGER_PATH + "doubleSpend", ipfs);
	            deleteFile(LOGGER_PATH + "doubleSpend");
	
	            JSONObject tokenObject = new JSONObject();
	            tokenObject.put("tokenDetails", tokenDetails);
	            tokenObject.put("previousSender", tokenPreviousSender);
	            tokenObject.put("positions", positionsArray);
	            tokenObject.put("amount", requestedAmount);
	            tokenObject.put("amountLedger", amountLedger);
	
	            /**
	             * Sending Token Details to Receiver
	             * Receiver to authenticate Tokens (Double Spending, IPFS availability)
	             */
	            output.println(tokenObject);
	
	            String tokenAuth;
	            try {
	                tokenAuth = input.readLine();
	                TokenSenderLogger.debug("Token Auth Code: " + tokenAuth);
	            } catch (SocketException e) {
	                TokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Token Auth");
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
	            if (tokenAuth != null && (tokenAuth.startsWith("4"))) {
	                switch (tokenAuth) {
	                    case "420":
	                        String doubleSpent = input.readLine();
	                        String owners = input.readLine();
	                        JSONArray ownersArray = new JSONArray(owners);
	                        TokenSenderLogger.info("Multiple Owners for " + doubleSpent);
	                        TokenSenderLogger.info("Owners " + ownersArray);
	                        TokenSenderLogger.info("Kindly re-initiate transaction");
	                        APIResponse.put("message", "Multiple Owners for " + doubleSpent + " Owners: " + ownersArray
	                                + ". Kindly re-initiate transaction");
	                        break;
	                    case "421":
	                        TokenSenderLogger.info("Consensus ID not unique. Kindly re-initiate transaction");
	                        APIResponse.put("message", "Consensus ID not unique. Kindly re-initiate transaction");
	                        break;
	                    case "422":
	                        TokenSenderLogger.info("Tokens Not Verified. Kindly re-initiate transaction");
	                        APIResponse.put("message", "Tokens Not Verified. Kindly re-initiate transaction");
	                        break;
	                    case "423":
	                        TokenSenderLogger.info("Broken Cheque Chain. Kindly re-initiate transaction");
	                        APIResponse.put("message", "Broken Cheque Chain. Kindly re-initiate transaction");
	                        break;
	
	                    case "424":
	                        String invalidTokens = input.readLine();
	                        JSONArray tokensArray = new JSONArray(invalidTokens);
	                        TokenSenderLogger.info("Ownership Check Failed for " + tokensArray);
	                        APIResponse.put("message", "Ownership Check Failed");
	                        break;
	
	                    case "425":
	                        TokenSenderLogger.info("Token wholly spent already. Kindly re-initiate transaction");
	                        APIResponse.put("message", "Token wholly spent already. Kindly re-initiate transaction");
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
	            TokenSenderLogger.debug("Token Auth Code: " + tokenAuth);
	
	            JSONObject dataObject = new JSONObject();
	            dataObject.put("tid", tid);
	            dataObject.put("message", doubleSpendString);
	            dataObject.put("receiverDidIpfs", receiverDidIpfsHash);
	            dataObject.put("pvt", pvt);
	            dataObject.put("senderDidIpfs", senderDidIpfsHash);
	            dataObject.put("token", wholeTokens.toString());
	            dataObject.put("alphaList", alphaPeersList);
	            dataObject.put("betaList", betaPeersList);
	            dataObject.put("gammaList", gammaPeersList);
	
	            InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 100, alphaSize, "");
	            TokenSenderLogger.debug("length on sender " + InitiatorConsensus.quorumSignature.length() + "response count "
	                    + InitiatorConsensus.quorumResponse);
	            if (InitiatorConsensus.quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
	                TokenSenderLogger.debug("Consensus Failed");
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
	
	            TokenSenderLogger.debug("Consensus Reached");
	            senderDetails2Receiver.put("status", "Consensus Reached");
	            senderDetails2Receiver.put("quorumsign", InitiatorConsensus.quorumSignature.toString());
	
	            output.println(senderDetails2Receiver);
	            TokenSenderLogger.debug("Quorum Signatures length " + InitiatorConsensus.quorumSignature.length());
	
	            String signatureAuth;
	            try {
	                signatureAuth = input.readLine();
	                TokenSenderLogger.debug("signature auth is "+signatureAuth);
	            } catch (SocketException e) {
	                TokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Signature Auth");
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
	            TokenSenderLogger.info("signatureAuth : " + signatureAuth);
	            endTime = System.currentTimeMillis();
	            totalTime = endTime - startTime;
	            if (signatureAuth != null && (!signatureAuth.equals("200"))) {
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
	
	            for (int i = 0; i < wholeTokens.length(); i++)
	                unpin(String.valueOf(wholeTokens.get(i)), ipfs);
	            repo(ipfs);
	
	            TokenSenderLogger.debug("Unpinned Tokens");
	            output.println("Unpinned");
	            String confirmation;
	            try {
	                confirmation = input.readLine();
	            } catch (SocketException e) {
	                TokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Pinning Auth");
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
	                TokenSenderLogger.warn("Multiple Owners for the token");
	                executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
	                TokenSenderLogger.info("Tokens with multiple pins");
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
	            TokenSenderLogger.debug("3");
	            TokenSenderLogger.debug("Whole tokens: " + wholeTokens);
	            TokenSenderLogger.debug("Part tokens: " + partTokens);
	            output.println(InitiatorProcedure.essential);
	            String respAuth;
	            try {
	                respAuth = input.readLine();
	            } catch (SocketException e) {
	                TokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Share Confirmation");
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
	                TokenSenderLogger.info("Incomplete Transaction");
	                return APIResponse;
	
	            }
	
	            TokenSenderLogger.debug("Operation over");
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
	
	            for (int i = 0; i < wholeTokens.length(); i++)
	                Files.deleteIfExists(Paths.get(tokenPath + wholeTokens.get(i)));
	
	            for (int i = 0; i < wholeTokens.length(); i++) {
	                Functions.updateJSON("remove", PAYMENTS_PATH.concat("BNK00.json"), wholeTokens.getString(i));
	            }
	
	            if (newPart) {
	                TokenSenderLogger.debug("Updating files for new parts");
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
	                            calculateHash(chainArray.getJSONObject(chainArray.length() - 1).getString("tid"), "SHA3-256"));
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
	                TokenSenderLogger.debug("Updating files for old parts");
	                for (int i = 0; i < partTokens.length(); i++) {
	                    String newTokenChain = readFile(TOKENCHAIN_PATH.concat("PARTS/") + partTokens.getString(i) + ".json");
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
	
	                    TokenSenderLogger
	                            .debug("Amount from ledger: " + formatAmount(amountLedger.getDouble(partTokens.getString(i))));
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
	
	                    TokenSenderLogger.debug("Checking Parts Token Balance ...");
	                    Double availableParts = partTokenBalance(partTokens.getString(i));
	                    TokenSenderLogger.debug("Available: " + availableParts);
	                    if (availableParts >= 1.000 || availableParts <= 0.000) {
	                        TokenSenderLogger.debug("Wholly Spent, Removing token from parts");
	                        String partFileContent2 = readFile(PAYMENTS_PATH.concat("PartsToken.json"));
	                        JSONArray partContentArray2 = new JSONArray(partFileContent2);
	                        for (int j = 0; j < partContentArray2.length(); j++) {
	                            if (partContentArray2.getJSONObject(j).getString("tokenHash").equals(partTokens.getString(i)))
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
	
	            TokenSenderLogger.info("Transaction Successful");
	            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
	            output.close();
	            input.close();
	            senderSocket.close();
	        }
        
        
        /*
         * 
         * Data Jar Starts here
         * 
         * 
         */
	        TokenSenderLogger.debug("skipping normal trnx");
	         if (detailsObject.getString(TRANS_TYPE).equals(DATA)) {
	        	
	        	
	        	String datumFolderPath = DATUM_CHAIN_PATH;
	        	File datumFolder = new File(datumFolderPath);
	        	File datumCommitHistory = new File(datumFolderPath.concat("datumCommitHistory.json"));
	        	File datumCommitToken = new File(PAYMENTS_PATH.concat("dataToken.json"));
	        	//File datumCommitChain = 
	        	if(!datumFolder.exists()) {
	        		TokenSenderLogger.debug("datumFolder is missing");
	        		datumFolder.mkdir();
	        		TokenSenderLogger.debug("datumFolder created");

	        	}
	        	if(!datumCommitHistory.exists()) {
	        		TokenSenderLogger.debug("datumCommitHistory is missing");
	        		datumCommitHistory.createNewFile();
	        		writeToFile(datumCommitHistory.toString(), "[]", false);
	        		TokenSenderLogger.debug("datumCommitHistory is created");
	        	}
	        	if(!datumCommitToken.exists()) {
	        		TokenSenderLogger.debug("datumCommitToken is missing");
	        		datumCommitToken.createNewFile();
	        		writeToFile(datumCommitToken.toString(), "[]", false);
	        	}
	        	
	        	String blockHash = detailsObject.getString("blockHash");
	        	TokenSenderLogger.debug("blockhash is "+ blockHash);
	            String authSenderByRecHash = calculateHash(blockHash + senderDidIpfsHash + comment,
	                    "SHA3-256");
	        //    TokenSenderLogger.debug("iauthSenderByRecHash is "+authSenderByRecHash);
	        //    TokenSenderLogger.debug("Hash to verify Data: " + authSenderByRecHash);
	            String tid = calculateHash(authSenderByRecHash, "SHA3-256");
	        //    TokenSenderLogger.debug("DataInitator by Data Hash " + authSenderByRecHash);
	        //    TokenSenderLogger.debug("TID on Initator " + tid);
	            
	        //    TokenSenderLogger.debug("BlockHash "+blockHash+" fetched from datumCommitChain is "+ 
	        //    		getValues(datumFolderPath.concat("datumCommitChain.json"), "blockHash", "blockHash", blockHash));
	            
	            String bankFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
	            JSONArray bankArray = new JSONArray(bankFile);
	            //JSONArray wholeTokens = new JSONArray();
	            JSONArray wholeTokensForData = new JSONArray();

	            String DAT_TOKEN_FILE_PATH = PAYMENTS_PATH.concat("DAT01.json");
	            File DAT_TOKEN_FILE = new File(DAT_TOKEN_FILE_PATH);

	            
	            if (!DAT_TOKEN_FILE.exists()) {
	            	TokenSenderLogger.debug("Data token file not found");
	            	DAT_TOKEN_FILE.createNewFile();
	                writeToFile(DAT_TOKEN_FILE.toString(), "[]", false);
	            	TokenSenderLogger.debug("Data token file created");

	            }else {
	            	TokenSenderLogger.debug("Data token file exists");
	            }
	            
	            //JSONArray datArray = new JSONArray(DAT_TOKEN_FILE);
	            String dataTokenFile = readFile(PAYMENTS_PATH.concat("DAT01.json"));
	            JSONArray datArray = new JSONArray(dataTokenFile);
	            JSONArray dataCommitToken = new JSONArray();
	            
	            TokenSenderLogger.debug("DAT01 size is "+ datArray.length());
	            
	            if(datArray.length()<1) {
	            	TokenSenderLogger.debug("Data token is empty");
	            	TokenSenderLogger.debug("Token to be moved to DAT01 "+bankArray.get(0).toString());
	            	JSONObject tokenChainName = new JSONObject(bankArray.get(0).toString());
	            	TokenSenderLogger.debug("tokenChainName is "+tokenChainName);
	            	TokenSenderLogger.debug("tokenChainType is "+tokenChainName.getClass().getName());

	            	updateJSON("add", DAT_TOKEN_FILE_PATH, "["+bankArray.get(0).toString()+"]");
	            }
	            
	            int wholeAmount = datArray.length();
	            
	           if(getValues(datumFolderPath.concat("datumCommitHistory.json"), "blockHash", "blockHash", blockHash).equals(blockHash)) 
	           {
	        	   APIResponse.put("did", senderDidIpfsHash);
                   APIResponse.put("tid", "null");
                   APIResponse.put("status", "Failed");
                   APIResponse.put("message", "Block Hash already exists");
                   return APIResponse;
	           }
	        	   
	
	          //  JSONArray allTokens = new JSONArray();
	            
	            
	            for (int i = 0; i < wholeAmount; i++) {
	               // wholeTokens.put(bankArray.getJSONObject(i).getString("tokenHash"));
	                wholeTokensForData.put(datArray.getJSONObject(i).getString("tokenHash"));
	                
	            }
	            
	            TokenSenderLogger.debug("WholeTokens for data is "+wholeTokensForData.toString());

	            for (int i = 0; i < wholeTokensForData.length(); i++) {
	                String tokenRemove = wholeTokensForData.getString(i);
	                for (int j = 0; j < datArray.length(); j++) {
	                    if (datArray.getJSONObject(j).getString("tokenHash").equals(tokenRemove))
	                    	datArray.remove(j);
	                }
	            }
	           // JSONArray wholeTokenChainHash = new JSONArray();
	            JSONArray wholeTokenForDataChainHash = new JSONArray();
	            JSONArray tokenPreviousSender = new JSONArray();
	            
	            for (int i = 0; i < wholeTokensForData.length(); i++) {
	                File token = new File(TOKENS_PATH + wholeTokensForData.get(i));
	                File tokenchain = new File(TOKENCHAIN_PATH + wholeTokensForData.get(i) + ".json");
	                if (!(token.exists() && tokenchain.exists())) {
	                    TokenSenderLogger.info("Tokens Not Verified");
	                    senderMutex = false;
	                    APIResponse.put("did", senderDidIpfsHash);
	                    APIResponse.put("tid", "null");
	                    APIResponse.put("status", "Failed");
	                    APIResponse.put("message", "Invalid token(s)");
	                    return APIResponse;
	
	                }
	                String wholeTokenForDataHash = add(TOKENS_PATH + wholeTokensForData.get(i), ipfs);
	              //  TokenSenderLogger.debug("Whole Token Hash us "+ wholeTokenHash);
	                pin(wholeTokenForDataHash, ipfs);
	                String tokenChainForDataHash = add(TOKENCHAIN_PATH + wholeTokensForData.get(i) + ".json", ipfs);
	                wholeTokenForDataChainHash.put(tokenChainForDataHash);
	                
	                
	               TokenSenderLogger.debug("Token chain for data's hash "+tokenChainForDataHash);
	               TokenSenderLogger.debug("Whole token chain hash "+wholeTokenForDataChainHash);
	
	                String tokenChainFileContent = readFile(TOKENCHAIN_PATH + wholeTokensForData.get(i) + ".json");
	                TokenSenderLogger.debug("tokenChainFile content is "+ tokenChainFileContent);
	                JSONArray tokenChainFileArray = new JSONArray(tokenChainFileContent);
	                TokenSenderLogger.debug("tokenChainFileArray is "+tokenChainFileArray.toString());
	                JSONArray previousSenderArray = new JSONArray();
	                JSONObject lastObject = tokenChainFileArray.getJSONObject(tokenChainFileArray.length() - 1);
	                TokenSenderLogger.debug("LastObject "+lastObject.toString());
	             //   TokenSenderLogger.debug("Last object is "+lastObject.toString());
	                if (lastObject.has("mineID")) {
	                	wholeTokensForData.remove(i);
	                }
	                
	                for (int j = 0; j < tokenChainFileArray.length(); j++) {
	                    String peerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
	                            tokenChainFileArray.getJSONObject(j).getString("sender"));
	                    previousSenderArray.put(peerID);
	                }
	
	                JSONObject previousSenderObject = new JSONObject();
	                previousSenderObject.put("token", wholeTokenForDataHash);
	                previousSenderObject.put("sender", previousSenderArray);
	                tokenPreviousSender.put(previousSenderObject);
	                
	                TokenSenderLogger.debug("previousSenderObject is "+previousSenderObject.toString());
	
	            }
	                  
	    	            
	            JSONArray allTokens = new JSONArray();
	            for (int i = 0; i < wholeTokensForData.length(); i++)
	                allTokens.put(wholeTokensForData.getString(i));
	            TokenSenderLogger.debug("WholeToken for data is "+wholeTokensForData.toString());
	            TokenSenderLogger.debug("allTokens for data is "+allTokens.toString());

	           TokenSenderLogger.debug("Whole token length is "+ wholeTokensForData.length()+" and Whole token content is "+wholeTokensForData.toString());
	            JSONArray positionsArray = new JSONArray();
	            String tokens=new String();
	            TokenSenderLogger.debug("All token lenght is "+allTokens.length()+" Whole token length is "+ wholeTokensForData.length());
	            
	            for (int i = 0; i < allTokens.length(); i++) {
	                tokens = allTokens.getString(i);
	                String hashString = tokens.concat(senderDidIpfsHash);
	                String hashForPositions = calculateHash(hashString, "SHA3-256");
	                BufferedImage privateShare = ImageIO
	                        .read(new File(DATA_PATH.concat(senderDidIpfsHash).concat("/PrivateShare.png")));
	                String firstPrivate = PropImage.img2bin(privateShare);
	                int[] privateIntegerArray1 = strToIntArray(firstPrivate);
	                String privateBinary = Functions.intArrayToStr(privateIntegerArray1);
	                String positions = "";
	                for (int j = 0; j < privateIntegerArray1.length; j += 49152) {
	                    positions += privateBinary.charAt(j);
	                }
	                String ownerIdentity = hashForPositions.concat(positions);
                    String ownerIdentityHash = calculateHash(ownerIdentity, "SHA3-256");
                    
                    
                    TokenSenderLogger.debug("Ownership Here Sender Calculation");
	                TokenSenderLogger.debug("tokens: " + tokens);
	                TokenSenderLogger.debug("hashString: " + hashString);
	                TokenSenderLogger.debug("hashForPositions: " + hashForPositions);
	                TokenSenderLogger.debug("p1: " + positions);
	                TokenSenderLogger.debug("ownerIdentity: " + ownerIdentity);
	                TokenSenderLogger.debug("ownerIdentityHash: " + ownerIdentityHash);
	                
	                positionsArray.put(positions);
	                
	                TokenSenderLogger.debug("WholeTokensForData.get(i) is "+wholeTokenForDataChainHash.get(i).toString());
                    JSONArray arrToken = new JSONArray();
                    JSONObject objectToken = new JSONObject();
                    objectToken.put("tokenHash", wholeTokensForData.getString(i));
                    arrToken.put(objectToken);
                    
                    
                    arr1.put(objectToken);
                    arr1.put("tokens: " + tokens);
                    arr1.put("hashString: " + hashString);
                    arr1.put("hashForPositions: " + hashForPositions);
                    arr1.put("p1: " + positions);
                    arr1.put("ownerIdentity: " + ownerIdentity);
                    arr1.put("ownerIdentityHash: " + ownerIdentityHash);

	
	                
	                
	                
                    writeToFile(DATUM_CHAIN_PATH + wholeTokensForData.getString(i) + ".json", arr1.toString(), false);

	                
	            }
	            
	            
	            JSONArray alphaQuorum = new JSONArray();
	            JSONArray betaQuorum = new JSONArray();
	            JSONArray gammaQuorum = new JSONArray();
	            int alphaSize;
	
	            ArrayList alphaPeersList;
	            ArrayList betaPeersList;
	            ArrayList gammaPeersList;
	
	            JSONArray quorumArray;
	       //     TokenSenderLogger.debug("Type "+type);
	            switch (type) {
	                case 1: {
	                    writeToFile(LOGGER_PATH + "tempbeta", tid.concat(senderDidIpfsHash), false);
	                    String betaHash = IPFSNetwork.add(LOGGER_PATH + "tempbeta", ipfs);
	                    deleteFile(LOGGER_PATH + "tempbeta");
	
	                    writeToFile(LOGGER_PATH + "tempgamma", tid.concat(blockHash), false);
	                    String gammaHash = IPFSNetwork.add(LOGGER_PATH + "tempgamma", ipfs);
	                    deleteFile(LOGGER_PATH + "tempgamma");
	
	                    quorumArray = getQuorum(senderDidIpfsHash, blockHash,
	                            0);
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
	            
	            TokenSenderLogger.debug("Quorum list "+quorumArray.toString());
	            int alphaCheck = 0, betaCheck = 0, gammaCheck = 0;
	            JSONArray sanityFailedQuorum = new JSONArray();
	            TokenSenderLogger.debug("Getting into Sanity Check for quorum");
	            for (int i = 0; i < quorumArray.length(); i++) {
	                String quorumPeerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
	                        quorumArray.getString(i));
	                boolean quorumSanityCheck = sanityCheck("Quorum",quorumPeerID, ipfs, port + 11);
	                TokenSenderLogger.debug("quorumSanityCheck is "+quorumSanityCheck);
	
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
		            TokenSenderLogger.debug("Inisde alpha beta gamma check");
	                APIResponse.put("did", senderDidIpfsHash);
	                APIResponse.put("tid", "null");
	                APIResponse.put("status", "Failed");
	                String message = "Quorum: ".concat(sanityFailedQuorum.toString()).concat(" ");
	                APIResponse.put("message", message.concat(sanityMessage));
	                TokenSenderLogger.warn("Quorum: ".concat(message.concat(sanityMessage)));
	                return APIResponse;
	            }
	
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
	                TokenSenderLogger.warn("Quorum Members not available");
	                senderMutex = false;
	                return APIResponse;
	            }
	            
	            
	            JSONObject tokenDetails = new JSONObject();
	            tokenDetails.put("whole-tokens", wholeTokensForData);
	            tokenDetails.put("whole-tokenChains", wholeTokenForDataChainHash);
	            //tokenDetails.put("hashSender", partTokenChainHash);
	            //tokenDetails.put("part-tokens", partTokens);
	            //tokenDetails.put("part-tokenChains", partTokenChainArrays);
	            tokenDetails.put("sender", senderDidIpfsHash);
	            TokenSenderLogger.debug("tokenDetails "+tokenDetails.toString());
	            String doubleSpendString = tokenDetails.toString();
	
	            String doubleSpend = calculateHash(doubleSpendString, "SHA3-256");
	            writeToFile(LOGGER_PATH + "doubleSpend", doubleSpend, false);
	            TokenSenderLogger.debug("********Double Spend Hash*********:  " + doubleSpend);
	            IPFSNetwork.addHashOnly(LOGGER_PATH + "doubleSpend", ipfs);
	            deleteFile(LOGGER_PATH + "doubleSpend");
	            
	            JSONObject dataObject = new JSONObject();
	            dataObject.put("tid", tid);
	            dataObject.put(TRANS_TYPE, detailsObject.getString(TRANS_TYPE));
	            dataObject.put("blockHash", blockHash);
	            dataObject.put("pvt", pvt);
	            dataObject.put("senderDidIpfs", senderDidIpfsHash);
	            dataObject.put("token", wholeTokensForData.toString());
	            dataObject.put("alphaList", alphaPeersList);
	            dataObject.put("betaList", betaPeersList);
	            dataObject.put("gammaList", gammaPeersList);
	            
	            TokenSenderLogger.debug("dataobject for Data (Double Spend Hash) " + dataObject.toString());
	            
	       //     TokenSenderLogger.debug("Starting consensusSetUp");
	            InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 100, alphaSize, ConsensusConstants.DATA);
	       //     TokenSenderLogger.debug("length on sender " + InitiatorConsensus.quorumSignature.length() + "response count "
	       //            + InitiatorConsensus.quorumResponse);
	            if (InitiatorConsensus.quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
	                TokenSenderLogger.debug("Consensus Failed");
	                //senderDetails2Receiver.put("status", "Consensus Failed");
	                //output.println(senderDetails2Receiver);
	                //executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
	                output.close();
	                input.close();
	                senderSocket.close();
	                senderMutex = false;
	                updateQuorum(quorumArray, null, false, type);
	                APIResponse.put("did", senderDidIpfsHash);
	                APIResponse.put("tid", tid);
	                APIResponse.put("status", "Failed");
	                APIResponse.put("message", "Data commit declined by Quorum");
	                return APIResponse;
	
	            }
	            
	            TokenSenderLogger.debug("Consensus Reached");
	            TokenSenderLogger.debug("Quorum Signatures length " + InitiatorConsensus.quorumSignature.length());
	            String senderSign = getSignFromShares(pvt, authSenderByRecHash);
	            Iterator<String> keys = InitiatorConsensus.quorumSignature.keys();
	            JSONArray signedQuorumList = new JSONArray();
	            
            //	TokenSenderLogger.debug("signed quorumlist count is "+keys.toString());
            	
            	int ctr = 0; 
            	
	            while (keys.hasNext()) {
	            	ctr++;
	                signedQuorumList.put(keys.next());

	            }
	            TokenSenderLogger.debug("signed Quorum list count is "+ ctr);
	         //   TokenSenderLogger.debug("signed Quorum list is "+ signedQuorumList.toString());

	            APIResponse.put("tid", tid);
	            APIResponse.put("blockHash",blockHash);
	            APIResponse.put("status", "Success");
	            APIResponse.put("did", senderDidIpfsHash);
	            APIResponse.put("message", "Data added successfully");
	            APIResponse.put("quorumlist", signedQuorumList);
	            //APIResponse.put("receiver", receiverDidIpfsHash);
	            APIResponse.put("totaltime", totalTime);
	
	         //   TokenSenderLogger.debug("API Response is "+APIResponse.toString());
	            
	            updateQuorum(quorumArray, signedQuorumList, true, type);
	
	            JSONObject dataBlockRecord = new JSONObject();
	            dataBlockRecord.put("role", "DataCommitter");
	            dataBlockRecord.put("tokens", allTokens);
	            dataBlockRecord.put("txn", tid);
	            dataBlockRecord.put("quorumList", signedQuorumList);
	            dataBlockRecord.put("senderDID", senderDidIpfsHash);
	            dataBlockRecord.put("receiverDID", senderDidIpfsHash);
	            dataBlockRecord.put("blockHash", blockHash);
	            dataBlockRecord.put("Date", getCurrentUtcTime());
	            dataBlockRecord.put("totalTime", totalTime);
	            dataBlockRecord.put("comment", comment);
	            dataBlockRecord.put("essentialShare", InitiatorProcedure.essential);
	            //requestedAmount = formatAmount(requestedAmount);
	            //dataBlockRecord.put("amount-spent", requestedAmount);
	            
	         //   TokenSenderLogger.debug("data block record is "+ dataBlockRecord.toString());
	
	            JSONArray dataBlockEntry = new JSONArray();
	            dataBlockEntry.put(dataBlockRecord);
	            TokenSenderLogger.debug("dataBlockRecord being added to json files");
	            updateJSON("add",datumFolderPath.concat("datumCommitHistory.json") , dataBlockEntry.toString());
	            updateJSON("add", WALLET_DATA_PATH.concat("TransactionHistory.json"), dataBlockEntry.toString());
	            
	            // Token receiver part starts here
	            
	            JSONObject tokenObject = new JSONObject(tokenDetails);
	            JSONObject TokenDetails = tokenObject.getJSONObject("tokenDetails");
	            JSONArray wholeTokens = TokenDetails.getJSONArray("whole-tokens");
	            JSONArray wholeTokenChains = TokenDetails.getJSONArray("whole-tokenChains");
	            JSONArray previousSendersArray = tokenObject.getJSONArray("previousSender");
	           // Double amount = tokenObject.getDouble("amount");
	            
	            
	           
	         
	            
	            
	            
	            
	            //JSONObject amountLedger = tokenObject.getJSONObject("amountLedger");
	            
	            
	
	
	            TokenSenderLogger.info("Data Block Build Successful");
	           // executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
	            //output.close();
	            //input.close();
	            //senderSocket.close();
	
	        }

        
        senderMutex = false;
        return APIResponse;

    }
}
