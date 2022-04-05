package com.rubix.NFT;

import com.rubix.Consensus.InitiatorConsensus;
import com.rubix.Consensus.InitiatorProcedure;
import com.rubix.Resources.Functions;
import static com.rubix.Resources.IPFSNetwork.*;
import io.ipfs.api.IPFS;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.rubix.NFTResources.EnableNft.*;

import static com.rubix.Resources.Functions.*;

public class NftBuyer {

    private static final Logger nftBuyerLogger = Logger.getLogger(NftBuyer.class);
    private static final String USER_AGENT = "Mozilla/5.0";
    public static BufferedReader serverInput;
    private static PrintStream output;
    private static BufferedReader input;
    private static Socket buyerSocket;
    private static boolean buyerMutex = false;
    public static String sellerPeerID;

    public static JSONObject send(String data, IPFS ipfs, int port) {
        nftPathSet();
        JSONArray quorumArray;
        JSONObject APIResponse = new JSONObject();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONArray alphaQuorum = new JSONArray();
        JSONArray betaQuorum = new JSONArray();
        JSONArray gammaQuorum = new JSONArray();

        BufferedReader sysInput = new BufferedReader(new InputStreamReader(System.in));

        try {
            JSONObject detailsObject = new JSONObject(data);

            String sellerPubKeyIpfsHash, saleContractIpfsHash;
            String buyerPubKeyIpfsHash = detailsObject.getString("buyerPubKeyIpfsHash");

            String buyerDid = detailsObject.getString("buyerDidIpfsHash");
            String sellerDid = detailsObject.getString("sellerDidIpfsHash");
            double requestedAmount;
            int type = detailsObject.getInt("type");
            String comment = detailsObject.getString("comment");
            String nftTokenIpfsHash = detailsObject.getString("nftToken");

            /*
             * if (buyerMutex) {
             * APIResponse.put("did", buyerDid);
             * APIResponse.put("tid", "null");
             * APIResponse.put("status", "Failed");
             * APIResponse.put("message", "Buyer busy. Try again later");
             * nftBuyerLogger.warn("Sender busy");
             * return APIResponse;
             * }
             * 
             * buyerMutex = true;
             */

            sellerPeerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", sellerDid);
            // TODO if sellerPeerID returns null we have to exit and call sync
            nftBuyerLogger.debug("Swarm connecting to " + sellerPeerID);
            swarmConnectP2P(sellerPeerID, ipfs);
            nftBuyerLogger.debug("Swarm connected");
            String sellerWidIpfsHash = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "walletHash",
                    "didHash", sellerDid);
            Functions.nodeData(sellerDid, sellerWidIpfsHash, ipfs);
            forward(sellerPeerID + "NFT", port, sellerPeerID);
            nftBuyerLogger.debug("Forwarded to " + sellerPeerID + " on " + port);
            buyerSocket = new Socket("127.0.0.1", port);
            input = new BufferedReader(new InputStreamReader(buyerSocket.getInputStream()));
            output = new PrintStream(buyerSocket.getOutputStream());
            long startTime = System.currentTimeMillis();

            String buyerPeerId = getPeerID(DATA_PATH + "DID.json");
            output.println(buyerPeerId);
            nftBuyerLogger.debug("Buyer PeerID sent to Seller");
            String peerAuth;
            try {
                peerAuth = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! - Sender Auth");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                // nftBuyerLogger.info("Buyer data not available in the network");
                output.close();
                input.close();
                buyerSocket.close();
                // buyerMutex=false;
                // Functions.updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond! - Sender Auth");
                return APIResponse;
            }

            nftBuyerLogger.debug("PeerAuth received from Seller. Code: " + peerAuth);
            if (peerAuth != null && (!peerAuth.equals("200"))) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                nftBuyerLogger.info("Buyer data not available in the network");
                output.close();
                input.close();
                buyerSocket.close();
                // buyerMutex=false;
                // sellerMutex = false;
                // Functions.updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer data not available in the network");
                return APIResponse;
            }

            nftBuyerLogger.debug("sending nft token hash to seller");
            output.println(nftTokenIpfsHash);

            String nftTokenAuth;

            try {
                nftTokenAuth = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! ");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();
                buyerMutex = false;
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond!");
                return APIResponse;
            }

            if (nftTokenAuth != null && (!nftTokenAuth.equals("200"))) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                nftBuyerLogger.info("Seller does not have NFT token" + nftTokenIpfsHash);
                output.close();
                input.close();
                buyerSocket.close();
                buyerMutex = false;
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller does not have NFT token" + nftTokenIpfsHash);
                return APIResponse;
            }

            String rbtAmount;
            try {
                rbtAmount = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! ");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();
                buyerMutex = false;
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond!");
                return APIResponse;
            }

            if (rbtAmount != null) {
                nftBuyerLogger.info(
                        "Received value of NFT in rbt from Seller for sale, value = " + Double.parseDouble(rbtAmount));
            }

            requestedAmount = Double.parseDouble(rbtAmount);

            nftBuyerLogger.info("Do you agree with the amount " + requestedAmount + "RBT for NFT sale ? [Y/N]");

            String buyerResponse = sysInput.readLine();

            if (buyerResponse != null && (buyerResponse.equals("Y") || buyerResponse.equals("y"))) {
                nftBuyerLogger.debug("Buyer agreed to the RBT amount asked for the NFT");
                output.println("200");
            } else {
                output.println("420");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                sysInput.close();
                buyerMutex = false;
                buyerSocket.close();
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer " + buyerDid + " did not agree to RBT asked for NFT");
                return APIResponse;

            }

            nftBuyerLogger.debug("Buyer confirmed RBT amount. Buyer checking balance RBT for NFT txn");

            Double available = getBalance();
            if (requestedAmount > available) {
                output.println("420");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                sysInput.close();
                buyerMutex = false;
                buyerSocket.close();
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer " + buyerDid + " does not have enough RBT balance for NFT sale");
                return APIResponse;
            }

            output.println("200");

            nftBuyerLogger.debug("Buyer waiting for NFT sale contract");

            String saleContract;

            try {
                saleContract = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! ");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();
                buyerMutex = false;
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond!");
                return APIResponse;
            }

            JSONObject saleContractObj = new JSONObject(saleContract);

            if (saleContractObj.has("auth") && !saleContractObj.getString("auth").equals("200")) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                sysInput.close();
                buyerMutex = false;
                buyerSocket.close();
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller " + sellerDid + " was not able to generate sale contract");
                return APIResponse;
            }

            saleContractIpfsHash = saleContractObj.getString("saleContractIpfsHash");

            nftBuyerLogger.debug("sale contract generated : " + saleContractIpfsHash);

            nftBuyerLogger.debug("Buyer inquires the Seller Public Key ipfs hash");

            try {
                sellerPubKeyIpfsHash = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! ");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();
                buyerMutex = false;
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond!");
                return APIResponse;
            }

            String nftTokenDetails;
            try {
                nftTokenDetails = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! ");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                // nftBuyerLogger.info("Buyer data not available in the network");
                output.close();
                input.close();
                buyerSocket.close();
                // buyerMutex=false;
                // sellerMutex = false;
                // Functions.updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond!");
                return APIResponse;
            }

            JSONObject nftDetailsObject = new JSONObject(nftTokenDetails);
            String nftConsensusID = nftDetailsObject.getString("nftConsensusID");
            nftBuyerLogger.debug("NFT Consesnsus auth send to seller");
            if (!dhtEmpty(nftConsensusID, ipfs)) {
                output.println("420");
                APIResponse.put("did", sellerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "NFT Tokens not verified");
                nftBuyerLogger.info("NFT Tokens not verified");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();
                // buyerMutex=false;
            }
            output.println("200");

            /*
             * area for selecting rbt tokens based on amount and create rbt consesnus id and
             * verification
             */
            // JSONObject rbtTokenDetails = new JSONObject();

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

            int intPart = (int) requestedAmount, wholeAmount;
            nftBuyerLogger.debug("Requested Part: " + requestedAmount);
            nftBuyerLogger.debug("Int Part: " + intPart);
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
                    nftBuyerLogger.info("Tokens Not Verified");
                    // senderMutex = false;
                    // buyerMutex=false;
                    APIResponse.put("did", buyerDid);
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

            nftBuyerLogger.debug("Decimal Part: " + decimalAmount);
            boolean newPart = false, oldNew = false;
            JSONObject amountLedger = new JSONObject();

            JSONArray partTokens = new JSONArray();
            JSONArray partTokenChainHash = new JSONArray();
            if (decimalAmount > 0.000D) {
                nftBuyerLogger.debug("Decimal Amount > 0.000D");

                String partFileContent = readFile(partTokensFile.toString());
                nftBuyerLogger.debug("partTokensFile : path : " + partTokensFile.toString());
                JSONArray partContentArray = new JSONArray(partFileContent);

                if (partContentArray.length() == 0) {
                    newPart = true;
                    nftBuyerLogger.debug("New token for parts");
                    String chosenToken = bankArray.getJSONObject(0).getString("tokenHash");
                    partTokens.put(chosenToken);
                    amountLedger.put(chosenToken, formatAmount(decimalAmount));

                } else {
                    Double counter = decimalAmount;

                    nftBuyerLogger.debug("partContentArray " + partContentArray);
                    JSONArray selectParts = new JSONArray(partFileContent);
                    while (counter > 0.000D) {
                        counter = formatAmount(counter);
                        nftBuyerLogger.debug("Counter: " + formatAmount(counter));
                        if (!(selectParts.length() == 0)) {
                            nftBuyerLogger.debug("Old Parts");
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
                            nftBuyerLogger.debug("Old Parts then new parts");
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

            nftBuyerLogger.debug(decimalAmount > 0.000D);
            nftBuyerLogger.debug("newPart value " + newPart);
            if (newPart) {
                tokenChainPath = TOKENCHAIN_PATH;
                tokenPath = TOKENS_PATH;
            } else {
                tokenChainPath = TOKENCHAIN_PATH.concat("PARTS/");
                tokenPath = TOKENS_PATH.concat("PARTS/");
            }

            nftBuyerLogger.debug("Tokenchain path: " + tokenChainPath);
            nftBuyerLogger.debug("Token path: " + tokenPath);
            for (int i = 0; i < partTokens.length(); i++) {
                File token = new File(tokenPath.concat(partTokens.getString(i)));
                File tokenchain = new File(tokenChainPath.concat(partTokens.getString(i)) + ".json");
                if (!(token.exists() && tokenchain.exists())) {
                    if (!token.exists())
                        nftBuyerLogger.debug("Token File for parts not avail");
                    if (!tokenchain.exists())
                        nftBuyerLogger.debug("Token Chain File for parts not avail");

                    nftBuyerLogger.info("Tokens Not Verified");
                    // senderMutex = false;
                    // buyerMutex=false;
                    APIResponse.put("did", buyerDid);
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
                writeToFile(tokenChainPath.concat(partTokens.getString(i)).concat(".json"), chainArray.toString(),
                        false);

                partTokenChainHash.put(add(tokenChainPath.concat(partTokens.getString(i)).concat(".json"), ipfs));
            }

            nftBuyerLogger.debug("1");
            nftBuyerLogger.debug("Whole tokens: " + wholeTokens);
            nftBuyerLogger.debug("Part tokens: " + partTokens);

            String authSenderByRecHash = calculateHash(wholeTokens.toString() + wholeTokenChainHash.toString()
                    + partTokens.toString() + partTokenChainHash.toString() + buyerDid + sellerDid + comment,
                    "SHA3-256");
            String rbtTid = calculateHash(authSenderByRecHash, "SHA3-256");
            String pvt = DATA_PATH + buyerDid + "/PrivateShare.png";
            String rbtSenderSign = getSignFromShares(pvt, authSenderByRecHash);
            JSONObject senderDetails2Receiver = new JSONObject();
            senderDetails2Receiver.put("sign", rbtSenderSign);
            senderDetails2Receiver.put("rbtTid", rbtTid);
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
                    secondLastObject.put("nextHash", calculateHash(rbtTid, "SHA3-256"));
                    newLastObject.put("previousHash", calculateHash(
                            chainArray.getJSONObject(chainArray.length() - 1).getString("tid"), "SHA3-256"));
                }

                Double amount = formatAmount(amountLedger.getDouble(partTokens.getString(i)));

                newLastObject.put("senderSign", rbtSenderSign);
                newLastObject.put("sender", buyerDid);
                newLastObject.put("receiver", sellerDid);
                newLastObject.put("comment", comment);
                newLastObject.put("tid", rbtTid);
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
            tokenDetails.put("sender", buyerDid);
            String doubleSpendString = tokenDetails.toString();

            nftBuyerLogger.debug("RBT token details " + doubleSpendString);

            String doubleSpend = calculateHash(doubleSpendString, "SHA3-256");
            writeToFile(LOGGER_PATH + "doubleSpend", doubleSpend, false);
            nftBuyerLogger.debug("********Double Spend Hash*********:  " + doubleSpend);
            addHashOnly(LOGGER_PATH + "doubleSpend", ipfs);
            deleteFile(LOGGER_PATH + "doubleSpend");

            JSONObject tokenObject = new JSONObject();
            tokenObject.put("tokenDetails", tokenDetails);
            tokenObject.put("previousSender", tokenPreviousSender);
            tokenObject.put("amount", requestedAmount);
            tokenObject.put("amountLedger", amountLedger);

            /**
             * Sending Token Details to Receiver
             * Receiver to authenticate Tokens (Double Spending, IPFS availability)
             */
            output.println(tokenObject);

            /*
             * String rbtConsensusAuth;
             * 
             * try {
             * rbtConsensusAuth = input.readLine();
             * } catch (SocketException e) {
             * nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! ");
             * executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
             * // nftBuyerLogger.info("Buyer data not available in the network");
             * output.close();
             * input.close();
             * buyerSocket.close();
             * // sellerMutex = false;
             * // Functions.updateQuorum(quorumArray, null, false, type);
             * APIResponse.put("did", buyerDid);
             * APIResponse.put("tid", "");
             * APIResponse.put("status", "Failed");
             * APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond!");
             * return APIResponse;
             * }
             * 
             * nftBuyerLogger.debug("recived rbtConsesnsusAuth from seller" +
             * rbtConsensusAuth);
             * if (rbtConsensusAuth != null && (!rbtConsensusAuth.equals("200"))) {
             * nftBuyerLogger.info("NFT Consensus ID not unique");
             * APIResponse.put("message", "RBT Consensus ID not unique");
             * APIResponse.put("did", buyerDid);
             * APIResponse.put("tid", "");
             * APIResponse.put("status", "Failed");
             * executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
             * output.close();
             * input.close();
             * buyerSocket.close();
             * return APIResponse;
             * }
             */

            String tokenAuth;
            try {
                tokenAuth = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Receiver " + sellerDid + " is unable to Respond! - Token Auth");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerDid);
                output.close();
                input.close();
                buyerSocket.close();
                // buyerMutex=false;
                // senderMutex = false;
                // updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver " + sellerDid + "is unable to respond! - Token Auth");

                return APIResponse;
            }

            nftBuyerLogger.debug("RBT token Auth recived from NFT seller " + tokenAuth);
            if (tokenAuth != null && (!tokenAuth.equals("200"))) {
                switch (tokenAuth) {
                    case "420":
                        String doubleSpent = input.readLine();
                        String owners = input.readLine();
                        JSONArray ownersArray = new JSONArray(owners);
                        nftBuyerLogger.info("Multiple Owners for " + doubleSpent);
                        APIResponse.put("message", "Multiple Owners for " + doubleSpent);
                        APIResponse.put("Owners", ownersArray);
                        // removeToken();
                        break;
                    case "421":
                        nftBuyerLogger.info("Consensus ID not unique");
                        APIResponse.put("message", "Consensus ID not unique");
                        // removeToken();
                        break;
                    case "422":
                        nftBuyerLogger.info("Tokens Not Verified");
                        APIResponse.put("message", "Tokens Not Verified");
                        // removeToken();
                        break;
                    case "423":
                        nftBuyerLogger.info("Broken Cheque Chain");
                        APIResponse.put("message", "Broken Cheque Chain");
                        break;

                    case "424":
                        nftBuyerLogger.info("Token wholly spent already");
                        APIResponse.put("message", "Token wholly spent already");
                        break;

                }
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);

                output.close();
                input.close();
                buyerSocket.close();
                // buyerMutex=false;
                // senderMutex = false;

                // updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", rbtTid);
                APIResponse.put("status", "Failed");
                return APIResponse;
            }

            nftBuyerLogger.debug("out of token auth check case with tokenAuth " + tokenAuth);

            String tid = calculateHash(nftDetailsObject.toString() + tokenDetails.toString(), "SHA3-256");
            nftBuyerLogger.debug("TID  " + tid);

            nftBuyerLogger.debug("connecting to quorum");
            Functions.writeToFile(Functions.LOGGER_PATH + "tempbeta", tid.concat(buyerDid),
                    Boolean.valueOf(false));
            String betaHash = add(Functions.LOGGER_PATH + "tempbeta", ipfs);
            Functions.deleteFile(Functions.LOGGER_PATH + "tempbeta");
            Functions.writeToFile(Functions.LOGGER_PATH + "tempgamma", tid.concat(sellerDid),
                    Boolean.valueOf(false));
            String gammaHash = add(Functions.LOGGER_PATH + "tempgamma", ipfs);
            Functions.deleteFile(Functions.LOGGER_PATH + "tempgamma");
            switch (type) {
                case 1:
                    quorumArray = Functions.getQuorum(betaHash, gammaHash, sellerDid, buyerDid, wholeTokens.length());
                    break;
                case 2:
                    quorumArray = new JSONArray(Functions.readFile(Functions.DATA_PATH + "quorumlist.json"));
                    break;
                case 3:
                    quorumArray = detailsObject.getJSONArray("quorum");
                    break;
                default:
                    nftBuyerLogger.error("Unknown quorum type input, cancelling transaction");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Unknown quorum type input, cancelling transaction");
                    APIResponse.put("did", buyerDid);
                    APIResponse.put("tid", tid);
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();
                    buyerSocket.close();
                    // buyerMutex=false;
                    return APIResponse;
            }
            Functions.QuorumSwarmConnect(quorumArray, ipfs);
            int alphaSize = quorumArray.length() - 14;
            int j;
            for (j = 0; j < alphaSize; j++)
                alphaQuorum.put(quorumArray.getString(j));
            for (j = 0; j < 7; j++) {
                betaQuorum.put(quorumArray.getString(alphaSize + j));
                gammaQuorum.put(quorumArray.getString(alphaSize + 7 + j));
            }
            nftBuyerLogger.debug("alphaquorum " + alphaQuorum + " size " + alphaQuorum.length());
            nftBuyerLogger.debug("betaquorum " + betaQuorum + " size " + betaQuorum.length());
            nftBuyerLogger.debug("gammaquorum " + gammaQuorum + " size " + gammaQuorum.length());
            ArrayList alphaPeersList = Functions.QuorumCheck(alphaQuorum, alphaSize);
            ArrayList betaPeersList = Functions.QuorumCheck(betaQuorum, 7);
            ArrayList gammaPeersList = Functions.QuorumCheck(gammaQuorum, 7);
            nftBuyerLogger.debug("alphaPeersList size " + alphaPeersList.size());
            nftBuyerLogger.debug("betaPeersList size " + betaPeersList.size());
            nftBuyerLogger.debug("gammaPeersList size " + gammaPeersList.size());
            nftBuyerLogger.debug("minQuorumAlpha size " + Functions.minQuorum(alphaSize));
            if (alphaPeersList.size() < Functions.minQuorum(alphaSize) || betaPeersList.size() < 5
                    || gammaPeersList.size() < 5) {
                Functions.updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Quorum Members not available");
                nftBuyerLogger.warn("Quorum Members not available");
                // buyerMutex=false;
                return APIResponse;
            }

            /*
             * output.println(detailsObject.getString("sellerPvtKeyStr"));
             * 
             * output.println(detailsObject.getString("sellerPvtKeyPass"));
             * String sellerNFTSign;
             * 
             * try {
             * sellerNFTSign = input.readLine();
             * } catch (SocketException e) {
             * nftBuyerLogger.info("seller did not respond");
             * APIResponse.put("message", "seller did not respond");
             * APIResponse.put("did", buyerDid);
             * APIResponse.put("tid", "");
             * APIResponse.put("status", "Failed");
             * executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
             * output.close();
             * input.close();
             * buyerSocket.close();
             * ;
             * return APIResponse;
             * }
             */

            // prepare dataObject to be used to set up and calling consensus
            JSONObject consensusDataObject = new JSONObject();

            consensusDataObject.put("message", doubleSpendString);
            consensusDataObject.put("tid", tid);
            consensusDataObject.put("receiverDidIpfs", sellerDid);
            consensusDataObject.put("senderDidIpfs", buyerDid);
            consensusDataObject.put("nftTokenDetails", nftDetailsObject);
            consensusDataObject.put("token", wholeTokens.toString());
            consensusDataObject.put("rbtTokenDetails", tokenDetails);
            consensusDataObject.put("pvt", DATA_PATH + buyerDid + "/PrivateShare.png"); // add buyer pvt share
            consensusDataObject.put("sellerPubKeyIpfsHash", sellerPubKeyIpfsHash); // add seller pub key
            consensusDataObject.put("saleContractIpfsHash", saleContractIpfsHash); // contract created for sale of nft
            consensusDataObject.put("tokenAmount", requestedAmount);// value of nft in rbt
            // consensusDataObject.put("sellerPvtKeySign", sellerNFTSign);// sign for nft
            // transfer created by seller and
            // sent to buyer
            consensusDataObject.put("alphaList", alphaPeersList);
            consensusDataObject.put("betaList", betaPeersList);
            consensusDataObject.put("gammaList", gammaPeersList);

            nftBuyerLogger.debug(consensusDataObject.toString());
            nftBuyerLogger.debug("NFT and RBT transfer Consensus setup begins");

            // InitiatorProcedure.nftConsensusSetUp(consensusDataObject.toString(), ipfs,
            // SELLER_PORT + 225, alphaSize);
            InitiatorProcedure.consensusSetUp(consensusDataObject.toString(), ipfs, SELLER_PORT + 225, alphaSize,
                    "NFT");

            nftBuyerLogger.debug("NFT and RBT transfer Consensus Done");

            nftBuyerLogger.debug("came back to NftBuyer class ");

            nftBuyerLogger.debug("quorum signature length for NFT : " + InitiatorConsensus.nftQuorumSignature.length()
                    + " Response count " + InitiatorConsensus.nftQuorumResponse);

            nftBuyerLogger.debug("quorum signature length for RBT : " + InitiatorConsensus.quorumSignature.length()
                    + " Response count " + InitiatorConsensus.quorumResponse);

            JSONObject consensusDetails = new JSONObject();
            consensusDetails.put("tid", tid);
            consensusDetails.put("comment", comment);
            consensusDetails.put("sign", rbtSenderSign);
            if (InitiatorConsensus.quorumSignature
                    .length() > ((Functions.minQuorum(alphaSize) + 2 * Functions.minQuorum(7)))
                    && InitiatorConsensus.nftQuorumSignature.length() < ((minQuorum(alphaSize) + 2 * minQuorum(7)))) {
                nftBuyerLogger.debug("Consensus Failed");
                consensusDetails.put("status", "Consensus Failed");
                // consensusDetails.put("quorumsign",
                // NftInitiatorConsensus.quorumSignature.toString());
                output.println(consensusDetails);
                APIResponse.put("message", " Consensus Failed");
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                Functions.updateQuorum(quorumArray, null, false, type);
                output.close();
                input.close();
                buyerSocket.close();
                // buyerMutex=false;
                return APIResponse;
            }
            nftBuyerLogger.debug("Consensus Reached");

            consensusDetails.put("status", "Consensus Reached");
            consensusDetails.put("quorumsign", InitiatorConsensus.quorumSignature.toString());
            // consensusDetails.put("nftQuorumSign", InitiatorConsensus.nftQuorumSignature);

            nftBuyerLogger.debug("sent consensus details to seller");
            output.println(consensusDetails);

            String signatureAuth;
            try {
                signatureAuth = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Receiver " + sellerDid + " is unable to Respond! - Signature Auth");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();
                // senderMutex = false;
                // buyerMutex=false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver " + sellerDid + "is unable to respond! - Signature Auth");

                return APIResponse;
            }
            nftBuyerLogger.info("signatureAuth : " + signatureAuth);

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            if (signatureAuth != null && (!signatureAuth.equals("200"))) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                nftBuyerLogger.info("Authentication Failed");
                output.close();
                input.close();
                buyerSocket.close();
                // senderMutex = false;
                // buyerMutex=false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender not authenticated");
                return APIResponse;

            }

            nftBuyerLogger.debug("Waiting for NFT Seller to Unpin NFT");

            String nftUnpin;

            try {
                nftUnpin = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Receiver " + sellerDid + " is unable to Respond! - nftUnpin Auth");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();
                // senderMutex = false;
                // buyerMutex=false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver " + sellerDid + "is unable to respond! - nftUnpin Auth");

                return APIResponse;
            }
            nftBuyerLogger.debug("NFT Seller Response  " + nftUnpin);
            if (!nftUnpin.equals("NFT-UnPinned")) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                nftBuyerLogger.info("Seller unpin NFT Failed");
                output.close();
                input.close();
                buyerSocket.close();
                // senderMutex = false;
                // buyerMutex=false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller unpin NFT Failed");
                return APIResponse;
            }

            nftBuyerLogger.debug("Buyer starts to Unpin RBT ");

            for (int i = 0; i < wholeTokens.length(); i++)
                unpin(String.valueOf(wholeTokens.get(i)), ipfs);
            // repo(ipfs);

            nftBuyerLogger.debug("RBT-UnPinned");
            output.println("RBT-UnPinned");

            nftBuyerLogger.debug("Waiting for Seller RBT pin Confirmation");
            String confirmation;
            try {
                confirmation = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Receiver " + sellerDid + " is unable to Respond! - Pinning Auth");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();
                // senderMutex = false;
                // buyerMutex=false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver " + sellerDid + "is unable to respond! - Pinning Auth");

                return APIResponse;
            }

            nftBuyerLogger.debug("Seller RBT pin Confirmation " + confirmation);
            if (confirmation != null && (!confirmation.equals("Successfully Pinned"))) {
                nftBuyerLogger.warn("Multiple Owners for the token");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                nftBuyerLogger.info("Tokens with multiple pins");
                output.close();
                input.close();
                buyerSocket.close();
                // buyerMutex=false;
                // senderMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Tokens with multiple pins");
                return APIResponse;

            }

            FileWriter fileWriter;
            fileWriter = new FileWriter(NFT_TOKENS_PATH + nftTokenIpfsHash);
            fileWriter.write(get(nftTokenIpfsHash, ipfs));
            fileWriter.close();
            add(NFT_TOKENS_PATH + nftTokenIpfsHash, ipfs);
            pin(nftTokenIpfsHash, ipfs);

            nftBuyerLogger.debug("3");
            nftBuyerLogger.debug("Whole tokens: " + wholeTokens);
            nftBuyerLogger.debug("Part tokens: " + partTokens);
            output.println(InitiatorProcedure.essential);
            String respAuth;
            try {
                respAuth = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Receiver " + sellerDid + " is unable to Respond! - Share Confirmation");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();
                // buyerMutex=false;
                // senderMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver " + sellerDid + "is unable to respond! - Share Confirmation");

                return APIResponse;
            }

            if (respAuth != null && (!respAuth.equals("Send Response"))) {

                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();
                // buyerMutex=false;
                // senderMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver process not over");
                nftBuyerLogger.info("Incomplete Transaction");
                return APIResponse;

            }

            nftBuyerLogger.debug("Operation over");
            Iterator<String> keys = InitiatorConsensus.quorumSignature.keys();
            JSONArray signedQuorumList = new JSONArray();
            while (keys.hasNext())
                signedQuorumList.put(keys.next());

            Iterator<String> nftKeys = InitiatorConsensus.nftQuorumSignature.keys();
            nftBuyerLogger.debug("nftQuorumSignature content " + InitiatorConsensus.nftQuorumSignature);
            JSONArray nftSignedQuorumList = new JSONArray();
            while (nftKeys.hasNext())
                nftSignedQuorumList.put(nftKeys.next());

            APIResponse.put("tid", tid);
            APIResponse.put("status", "Success");
            APIResponse.put("did", buyerDid);
            APIResponse.put("message", "Tokens transferred successfully!");
            APIResponse.put("quorumlist", signedQuorumList);
            APIResponse.put("receiver", sellerDid);
            APIResponse.put("totaltime", totalTime);

            updateQuorum(quorumArray, signedQuorumList, true, type);

            // updating quorum credit for signing nft txn
            // updateQuorum(quorumArray, nftSignedQuorumList, true, type);

            JSONArray allTokens = new JSONArray();
            for (int i = 0; i < wholeTokens.length(); i++)
                allTokens.put(wholeTokens.getString(i));
            for (int i = 0; i < partTokens.length(); i++)
                allTokens.put(partTokens.getString(i));

            nftBuyerLogger.debug("4");
            nftBuyerLogger.debug("All tokens: " + allTokens);
            nftBuyerLogger.debug("Whole tokens: " + wholeTokens);
            nftBuyerLogger.debug("Part tokens: " + partTokens);

            JSONObject rbtTransactionRecord = new JSONObject();
            rbtTransactionRecord.put("role", "Sender");
            rbtTransactionRecord.put("tokens", allTokens);
            rbtTransactionRecord.put("txn", tid);
            rbtTransactionRecord.put("quorumList", signedQuorumList);
            rbtTransactionRecord.put("senderDID", buyerDid);
            rbtTransactionRecord.put("receiverDID", sellerDid);
            rbtTransactionRecord.put("Date", getCurrentUtcTime());
            rbtTransactionRecord.put("totalTime", totalTime);
            rbtTransactionRecord.put("comment", comment);
            rbtTransactionRecord.put("essentialShare", InitiatorProcedure.essential);
            requestedAmount = formatAmount(requestedAmount);
            rbtTransactionRecord.put("amount-spent", requestedAmount);

            JSONArray transactionHistoryEntry = new JSONArray();
            transactionHistoryEntry.put(rbtTransactionRecord);

            updateJSON("add", WALLET_DATA_PATH + "TransactionHistory.json", transactionHistoryEntry.toString());

            JSONObject nftTransactionRecord = new JSONObject();
            nftTransactionRecord.put("role", "Buyer");
            nftTransactionRecord.put("nftToken", nftTokenIpfsHash);
            nftTransactionRecord.put("txn", tid);
            nftTransactionRecord.put("quorumList", signedQuorumList);
            nftTransactionRecord.put("senderDID", sellerDid);
            nftTransactionRecord.put("receiverDID", buyerDid);
            nftTransactionRecord.put("Date", getCurrentUtcTime());
            nftTransactionRecord.put("totalTime", totalTime);
            nftTransactionRecord.put("comment", comment);
            nftTransactionRecord.put("essentialShare", InitiatorProcedure.essential);
            requestedAmount = formatAmount(requestedAmount);
            // nftTransactionRecord.put("amount-spent", requestedAmount);

            JSONArray nftTransactionHistoryEntry = new JSONArray();
            nftTransactionHistoryEntry.put(nftTransactionRecord);
            updateJSON("add", WALLET_DATA_PATH + "nftTransactionHistory.json", nftTransactionHistoryEntry.toString());

            /*
             * String nftSellerSign;
             * try {
             * nftSellerSign= input.readLine();
             * } catch (SocketException e) {
             * nftBuyerLogger.warn("Receiver " + sellerDid +
             * " is unable to Respond! - nft seller signature");
             * executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
             * output.close();
             * input.close();
             * buyerSocket.close();
             * //senderMutex = false;
             * updateQuorum(quorumArray, null, false, type);
             * APIResponse.put("did", buyerDid);
             * APIResponse.put("tid", "null");
             * APIResponse.put("status", "Failed");
             * APIResponse.put("message", "Receiver " + sellerDid +
             * "is unable to respond! - nft seller signature");
             * 
             * return APIResponse;
             * }
             */

            // update recived nfttokenchain
            String saleContractContent = get(saleContractIpfsHash, ipfs);
            JSONObject saleContractObject = new JSONObject(saleContractContent);
            String nftTokenChainContent = get(nftDetailsObject.getString("nftTokenChain"), ipfs);
            JSONArray currentNftTokenChain = new JSONArray(nftTokenChainContent);
            JSONObject newRecord = new JSONObject();
            newRecord.put("sellerDID", sellerDid);
            newRecord.put("BuyerDID", buyerDid);
            newRecord.put("sellerSign", saleContractObject.getString("sign"));
            newRecord.put("comment", comment);
            newRecord.put("tid", tid);
            newRecord.put("sellerPubKeyIpfsHash", sellerPubKeyIpfsHash);
            newRecord.put("buyerPubKeyIpfsHash", buyerPubKeyIpfsHash);
            // newRecord.put("buyerPubKeyIpfsHash",detailsObject.getString("buyerPubKeyIpfsHash"));
            currentNftTokenChain.put(newRecord);
            Functions.writeToFile(NFT_TOKENCHAIN_PATH + nftTokenIpfsHash + ".json", currentNftTokenChain.toString(),
                    Boolean.valueOf(false));

            for (int i = 0; i < wholeTokens.length(); i++)
                Files.deleteIfExists(Paths.get(tokenPath + wholeTokens.get(i)));

            for (int i = 0; i < wholeTokens.length(); i++) {
                Functions.updateJSON("remove", PAYMENTS_PATH.concat("BNK00.json"), wholeTokens.getString(i));
            }

            if (newPart) {
                nftBuyerLogger.debug("Updating files for new parts");
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
                    newLastObject.put("previousHash", calculateHash(
                            chainArray.getJSONObject(chainArray.length() - 1).getString("tid"), "SHA3-256"));
                }

                Double amount = formatAmount(decimalAmount);

                newLastObject.put("senderSign", rbtSenderSign);
                newLastObject.put("sender", buyerDid);
                newLastObject.put("receiver", sellerDid);
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
                nftBuyerLogger.debug("Updating files for old parts");
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

                    nftBuyerLogger.debug(
                            "Amount from ledger: " + formatAmount(amountLedger.getDouble(partTokens.getString(i))));
                    Double amount = formatAmount(amountLedger.getDouble(partTokens.getString(i)));

                    newLastObject.put("senderSign", rbtSenderSign);
                    newLastObject.put("sender", buyerDid);
                    newLastObject.put("receiver", sellerDid);
                    newLastObject.put("comment", comment);
                    newLastObject.put("tid", tid);
                    newLastObject.put("nextHash", "");
                    newLastObject.put("role", "Sender");
                    newLastObject.put("amount", amount);
                    chainArray.put(newLastObject);
                    writeToFile(TOKENCHAIN_PATH.concat("PARTS/").concat(partTokens.getString(i)).concat(".json"),
                            chainArray.toString(), false);

                    nftBuyerLogger.debug("Checking Parts Token Balance ...");
                    Double availableParts = partTokenBalance(partTokens.getString(i));
                    nftBuyerLogger.debug("Available: " + availableParts);
                    if (availableParts >= 1.000 || availableParts <= 0.000) {
                        nftBuyerLogger.debug("Wholly Spent, Removing token from parts");
                        String partFileContent2 = readFile(PAYMENTS_PATH.concat("PartsToken.json"));
                        JSONArray partContentArray2 = new JSONArray(partFileContent2);
                        for (j = 0; j < partContentArray2.length(); j++) {
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
                // dataToSend.put("transaction_type", "NFT");
                dataToSend.put("transaction_id", tid);
                dataToSend.put("sender_did", buyerDid);
                dataToSend.put("receiver_did", sellerDid);
                dataToSend.put("token_id", tokenList);
                dataToSend.put("token_time", (int) totalTime);
                dataToSend.put("amount", requestedAmount);
                // dataToSend.put("nftToken", nftTokenIpfsHash);
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
                nftBuyerLogger.debug("Sending 'POST' request to URL : " + url);
                nftBuyerLogger.debug("Post Data : " + postJsonData);
                nftBuyerLogger.debug("Response Code : " + responseCode);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String output;
                StringBuffer response = new StringBuffer();

                while ((output = in.readLine()) != null) {
                    response.append(output);
                }
                in.close();

                nftBuyerLogger.debug(response.toString());
            }

            /*
             * nftBuyerLogger.info("Transaction Successful");
             * executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
             * output.close();
             * input.close();
             * buyerSocket.close();
             * //senderMutex = false;
             * return APIResponse;
             */
            nftBuyerLogger.info("Transaction ID: " + tid + " NFT Transaction Successful");
            output.println("Send Response");
            APIResponse.put("did", buyerDid);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Success");
            APIResponse.put("nfttoken", nftTokenIpfsHash);
            APIResponse.put("comment", comment);
            APIResponse.put("message", "NFT Transaction Successful");
        } catch (JSONException e) {
            nftBuyerLogger.error("JSONEXception at reading data", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ParseException e) {
        }
        nftBuyerLogger.info("NFT Transaction Successful");
        executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
        try {
            buyerSocket.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // buyerMutex=false;
        return APIResponse;
    }

}
