package com.rubix.TokenTransfer;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Resources.Functions;
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

            int quorumSignVerifyCount = 0;
            JSONObject quorumSignatures = null;

            ArrayList<String> quorumDID = new ArrayList<>();
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

            String receiverPeerID = getPeerID(DATA_PATH + "DID.json");

            String receiverDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", receiverPeerID);

            listen(receiverPeerID, RECEIVER_PORT);
            ss = new ServerSocket(RECEIVER_PORT);
            TokenReceiverLogger.debug("Receiver Listening on " + RECEIVER_PORT + " appname " + receiverPeerID);

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
            JSONArray wholeTokens = TokenDetails.getJSONArray("whole-tokens");
            JSONArray wholeTokenChains = TokenDetails.getJSONArray("whole-tokenChains");

            JSONArray partTokens = TokenDetails.getJSONArray("part-tokens");
            JSONObject partTokenChains = TokenDetails.getJSONObject("part-tokenChains");
            JSONArray partTokenChainsHash = TokenDetails.getJSONArray("hashSender");

            JSONArray previousSendersArray = tokenObject.getJSONArray("previousSender");
            Double amount = tokenObject.getDouble("amount");
            JSONObject amountLedger = tokenObject.getJSONObject("amountLedger");
            TokenReceiverLogger.debug("Amount Ledger: " + amountLedger);
            int intPart = wholeTokens.length();
            Double decimalPart = formatAmount(amount-intPart);
            JSONArray doubleSpentToken = new JSONArray();
            boolean tokenOwners = true;
            ArrayList ownersArray = new ArrayList();
            ArrayList previousSender = new ArrayList();
            JSONArray ownersReceived = new JSONArray();
            for (int i = 0; i < wholeTokens.length(); ++i) {
                try {
                    TokenReceiverLogger.debug("Checking owners for " + wholeTokens.getString(i) + " Please wait...");
                    ownersArray = IPFSNetwork.dhtOwnerCheck(wholeTokens.getString(i));

                    if (ownersArray.size() > 2) {

                        for (int j = 0; j < previousSendersArray.length(); j++) {
                            if (previousSendersArray.getJSONObject(j).getString("token").equals(wholeTokens.getString(i)))
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
            if (!tokenOwners) {
                JSONArray owners = new JSONArray();
                for (int i = 0; i < ownersArray.size(); i++)
                    owners.put(ownersArray.get(i).toString());
                TokenReceiverLogger.debug("Multiple Owners for " + doubleSpentToken);
                TokenReceiverLogger.debug("Owners: " + owners);
                output.println("200");
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

            String senderToken = TokenDetails.toString();
            String consensusID = calculateHash(senderToken, "SHA3-256");
            writeToFile(LOGGER_PATH + "consensusID", consensusID, false);
            String consensusIDIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH + "consensusID", ipfs);
            deleteFile(LOGGER_PATH + "consensusID");

            if (!IPFSNetwork.dhtEmpty(consensusIDIPFSHash, ipfs)) {
                TokenReceiverLogger.debug("consensus ID not unique" + consensusIDIPFSHash);
                output.println("200");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Consensus ID not unique");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }

                //Check IPFS get for all Tokens
            int ipfsGetFlag = 0;
            ArrayList<String> allTokenContent = new ArrayList<>();
            ArrayList<String> allTokenChainContent = new ArrayList<>();
            for (int i = 0; i < intPart; i++) {
                String TokenChainContent = get(wholeTokenChains.getString(i), ipfs);
                allTokenChainContent.add(TokenChainContent);
                String TokenContent = get(wholeTokens.getString(i), ipfs);
                allTokenContent.add(TokenContent);
                ipfsGetFlag++;
            }
            repo(ipfs);

            if (!(ipfsGetFlag == intPart)) {
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

            JSONArray partTokenChainContent = new JSONArray();
            JSONArray partTokenContent = new JSONArray();

            for (int i = 0; i < partTokenChains.length(); i++) {

                partTokenChainContent.put(partTokenChains.getJSONArray(partTokens.getString(i)));
                String TokenContent = get(partTokens.getString(i), ipfs);
                partTokenContent.put(TokenContent);
            }

            boolean chainFlag = true;
            for(int i = 0; i < partTokenChainContent.length(); i++) {
                JSONArray tokenChainContent = partTokenChainContent.getJSONArray(i);
                for (int j = 0; j < tokenChainContent.length(); j++) {
                    String previousHash = tokenChainContent.getJSONObject(j).getString("previousHash");
                    String nextHash = tokenChainContent.getJSONObject(j).getString("nextHash");
                    String rePreviousHash, reNextHash;
                    if (tokenChainContent.length() > 1) {
                        if (j == 0) {
                            rePreviousHash = "";
                            String rePrev = calculateHash(new JSONObject().toString(), "SHA3-256");
                            reNextHash = calculateHash(tokenChainContent.getJSONObject(j + 1).getString("tid"), "SHA3-256");

                            if (!((rePreviousHash.equals(previousHash) || rePrev.equals(previousHash)) && reNextHash.equals(nextHash))) {
                                chainFlag = false;
                            }

                        } else if (j == tokenChainContent.length() - 1) {
                            rePreviousHash = calculateHash(tokenChainContent.getJSONObject(j - 1).getString("tid"), "SHA3-256");
                            reNextHash = "";

                            if (!(rePreviousHash.equals(previousHash) && reNextHash.equals(nextHash))) {
                                chainFlag = false;
                            }

                        } else {
                            rePreviousHash = calculateHash(tokenChainContent.getJSONObject(j - 1).getString("tid"), "SHA3-256");
                            reNextHash = calculateHash(tokenChainContent.getJSONObject(j + 1).getString("tid"), "SHA3-256");

                            if (!(rePreviousHash.equals(previousHash) && reNextHash.equals(nextHash))) {
                                chainFlag = false;
                            }
                        }
                    }
                }
            }
            if (!chainFlag) {

                String errorMessage = "Broken Cheque Chain";
                output.println("423");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", errorMessage);
                TokenReceiverLogger.debug(errorMessage);
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }

            boolean partsAvailable = true;
            for(int i = 0; i < partTokenChainContent.length(); i++){
                Double senderCount = 0.000D, receiverCount = 0.000D;
                JSONArray tokenChainContent = partTokenChainContent.getJSONArray(i);
                for (int k = 0; k < tokenChainContent.length(); k++) {
                    if (tokenChainContent.getJSONObject(k).has("role")) {
                        if (tokenChainContent.getJSONObject(k).getString("role").equals("Sender") && tokenChainContent.getJSONObject(k).getString("sender").equals(senderDidIpfsHash)) {
                            senderCount += tokenChainContent.getJSONObject(k).getDouble("amount");
                        } else if (tokenChainContent.getJSONObject(k).getString("role").equals("Receiver") && tokenChainContent.getJSONObject(k).getString("receiver").equals(senderDidIpfsHash)) {
                            receiverCount += tokenChainContent.getJSONObject(k).getDouble("amount");
                        }
                    }
                }
                FunctionsLogger.debug("Sender Parts: " + formatAmount(senderCount));
                FunctionsLogger.debug("Receiver Parts: " + formatAmount(receiverCount));
                Double availableParts = receiverCount - senderCount;

                availableParts = formatAmount(availableParts);
                availableParts += amountLedger.getDouble(partTokens.getString(i));
                availableParts = formatAmount(availableParts);

                if(availableParts > 1.000D) {
                    TokenReceiverLogger.debug("Token wholly spent: " + partTokens.getString(i));
                    TokenReceiverLogger.debug("Parts: " + availableParts);
                }
            }

             if (!partsAvailable) {
                String errorMessage = "Token wholly spent already";
                output.println("424");
                APIResponse.put("did", senderDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", errorMessage);
                TokenReceiverLogger.debug(errorMessage);
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }else
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


            TokenReceiverLogger.debug("Verifying Quorum ...  ");
            TokenReceiverLogger.debug("Please wait, this might take a few seconds");

            if (!Status.equals("Consensus Failed")) {
                boolean yesQuorum = false;
                if (Status.equals("Consensus Reached")) {
                    quorumSignatures = new JSONObject(QuorumDetails);
                    String selectQuorumHash = calculateHash(senderToken, "SHA3-256");
                    String verifyQuorumHash = calculateHash(selectQuorumHash.concat(receiverDidIpfsHash), "SHA3-256");

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
                JSONArray wholeTokenChainHash = new JSONArray();
                for (int i = 0; i < intPart; i++)
                    wholeTokenChainHash.put(wholeTokenChains.getString(i));

                String hash = calculateHash(wholeTokens.toString() + wholeTokenChainHash.toString() + partTokens.toString() + partTokenChainsHash.toString() + receiverDidIpfsHash + senderDidIpfsHash + comment, "SHA3-256");
                TokenReceiverLogger.debug("Hash to verify Sender: " + hash);
                JSONObject detailsForVerify = new JSONObject();
                detailsForVerify.put("did", senderDidIpfsHash);
                detailsForVerify.put("hash", hash);
                detailsForVerify.put("signature", senderSignature);

                boolean yesSender = Authenticate.verifySignature(detailsForVerify.toString());
                TokenReceiverLogger.debug("Quorum Auth : " + yesQuorum + " Sender Auth : " + yesSender);
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
                    for (int i = 0; i < intPart; i++) {
                        FileWriter fileWriter;
                        fileWriter = new FileWriter(TOKENS_PATH + wholeTokens.getString(i));
                        fileWriter.write(allTokenContent.get(i));
                        fileWriter.close();
                        add(TOKENS_PATH + wholeTokens.getString(i), ipfs);
                        pin(wholeTokens.getString(i), ipfs);
                        count++;

                    }

                    for (int i = 0; i < partTokens.length(); i++) {
                        File tokenFile = new File(PART_TOKEN_PATH + partTokens.getString(i));
                        if (!tokenFile.exists())
                            tokenFile.createNewFile();
                        FileWriter fileWriter;
                        fileWriter = new FileWriter(PART_TOKEN_PATH + partTokens.getString(i));
                        fileWriter.write(partTokenContent.getString(i));
                        fileWriter.close();
                        String tokenHash = add(PART_TOKEN_PATH + partTokens.getString(i), ipfs);
                        pin(tokenHash, ipfs);

                    }

                    if (count == intPart) {
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

                        for (int i = 0; i < intPart; i++) {

                            ArrayList<String> groupTokens = new ArrayList<>();
                            for (int k = 0; k < intPart; k++) {
                                if (!wholeTokens.getString(i).equals(wholeTokens.getString(k)))
                                    groupTokens.add(wholeTokens.getString(k));
                            }

                            JSONArray arrToken = new JSONArray();
                            JSONObject objectToken = new JSONObject();
                            objectToken.put("tokenHash", wholeTokens.getString(i));
                            arrToken.put(objectToken);
                            JSONArray arr1 = new JSONArray(allTokenChainContent.get(i));
                            JSONObject obj2 = new JSONObject();
                            obj2.put("senderSign", senderSignature);
                            obj2.put("sender", senderDidIpfsHash);
                            obj2.put("group", groupTokens);
                            obj2.put("comment", comment);
                            obj2.put("tid", tid);
                            arr1.put(obj2);
                            writeToFile(TOKENCHAIN_PATH + wholeTokens.getString(i) + ".json", arr1.toString(), false);
                        }


                        for(int i = 0; i < partTokens.length(); i++){
                            JSONObject chequeObject = new JSONObject();
                            chequeObject.put("sender", senderDidIpfsHash);
                            chequeObject.put("receiver", receiverDidIpfsHash);
                            chequeObject.put("parent-token", partTokens.getString(i));
                            chequeObject.put("parent-chain", partTokenChains.getJSONArray(partTokens.getString(i)));
                            Double partAmount = formatAmount(amountLedger.getDouble(partTokens.getString(i)));
                            chequeObject.put("amount", partAmount);
                            chequeObject.put("tid", tid);

                            writeToFile(LOGGER_PATH.concat(partTokens.getString(i)), chequeObject.toString(), false);
                            String chequeHash = IPFSNetwork.add(LOGGER_PATH.concat(partTokens.getString(i)), ipfs);
                            deleteFile(LOGGER_PATH.concat(partTokens.getString(i)));


                            JSONObject newPartObject = new JSONObject();
                            newPartObject.put("senderSign", senderSignature);
                            newPartObject.put("sender", senderDidIpfsHash);
                            newPartObject.put("receiver", receiverDidIpfsHash);
                            newPartObject.put("comment", comment);
                            newPartObject.put("tid", tid);
                            newPartObject.put("nextHash", "");
                            if(partTokenChainContent.getJSONArray(i).length() == 0)
                                newPartObject.put("previousHash", "");
                            else
                                newPartObject.put("previousHash", calculateHash(partTokenChainContent.getJSONArray(i).getJSONObject(partTokenChainContent.getJSONArray(i).length() - 1).getString("tid"), "SHA3-256"));


                            newPartObject.put("amount", partAmount);
                            newPartObject.put("cheque", chequeHash);
                            newPartObject.put("role", "Receiver");

                            File chainFile = new File(PART_TOKEN_CHAIN_PATH.concat(partTokens.getString(i)).concat(".json"));
                            if (chainFile.exists()) {

                                String readChain = readFile(PART_TOKEN_CHAIN_PATH + partTokens.getString(i) + ".json");
                                JSONArray readChainArray = new JSONArray(readChain);
                                readChainArray.put(partTokenChainContent.getJSONArray(i).getJSONObject(partTokenChainContent.getJSONArray(i).length() - 1));
                                readChainArray.put(newPartObject);

                                writeToFile(PART_TOKEN_CHAIN_PATH + partTokens.getString(i) + ".json", readChainArray.toString(), false);


                            } else {
                                partTokenChainContent.getJSONArray(i).put(newPartObject);
                                writeToFile(PART_TOKEN_CHAIN_PATH + partTokens.getString(i) + ".json", partTokenChainContent.getJSONArray(i).toString(), false);
                            }
                        }

                        JSONObject transactionRecord = new JSONObject();
                        transactionRecord.put("role", "Receiver");
                        transactionRecord.put("tokens", wholeTokens);
                        transactionRecord.put("txn", tid);
                        transactionRecord.put("quorumList", quorumSignatures.keys());
                        transactionRecord.put("senderDID", senderDidIpfsHash);
                        transactionRecord.put("receiverDID", receiverDidIpfsHash);
                        transactionRecord.put("Date", getCurrentUtcTime());
                        transactionRecord.put("totalTime", (endTime - startTime));
                        transactionRecord.put("comment", comment);
                        transactionRecord.put("essentialShare", essentialShare);
                        amount = formatAmount(amount);
                        transactionRecord.put("amount-received", amount);

                        JSONArray transactionHistoryEntry = new JSONArray();
                        transactionHistoryEntry.put(transactionRecord);
                        updateJSON("add", WALLET_DATA_PATH + "TransactionHistory.json", transactionHistoryEntry.toString());

                        for (int i = 0; i < wholeTokens.length(); i++) {
                            String bankFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
                            JSONArray bankArray = new JSONArray(bankFile);
                            JSONObject tokenObject1 = new JSONObject();
                            tokenObject1.put("tokenHash", wholeTokens.getString(i));
                            bankArray.put(tokenObject1);
                            Functions.writeToFile(PAYMENTS_PATH.concat("BNK00.json"), bankArray.toString(), false);

                        }

                        String partsFile = readFile(PAYMENTS_PATH.concat("PartsToken.json"));
                        JSONArray partsReadArray = new JSONArray(partsFile);

                        for(int i = 0; i < partTokens.length(); i++){
                            boolean writeParts = true;
                            for(int j = 0; j < partsReadArray.length(); j++){
                                if(partsReadArray.getJSONObject(j).getString("tokenHash").equals(partTokens.getString(i)))
                                    writeParts = false;
                            }
                            if(writeParts) {
                                JSONObject partObject = new JSONObject();
                                partObject.put("tokenHash", partTokens.getString(i));
                                partsReadArray.put(partObject);
                            }
                        }
                        writeToFile(PAYMENTS_PATH.concat("PartsToken.json"), partsReadArray.toString(), false);

                        TokenReceiverLogger.info("Transaction ID: " + tid + "Transaction Successful");
                        output.println("Send Response");
                        APIResponse.put("did", senderDidIpfsHash);
                        APIResponse.put("tid", tid);
                        APIResponse.put("status", "Success");
                        APIResponse.put("tokens", wholeTokens);
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
