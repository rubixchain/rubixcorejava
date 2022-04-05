package com.rubix.Consensus;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.security.PublicKey;
import java.text.ParseException;
import java.util.HashSet;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;
import static com.rubix.NFTResources.NFTFunctions.*;

public class QuorumConsensus implements Runnable {

    public static Logger QuorumConsensusLogger = Logger.getLogger(QuorumConsensus.class);

    /**
     * This method is used to run a thread for Quorum Members
     * <p>
     * This involves
     * <ol>
     * <li>Verify sender signature</li>
     * <li>Signing the transaction</li>
     * <li>Receiving share from sender</li>
     * </ol>
     */

    int port;
    IPFS ipfs;
    String role;

    public QuorumConsensus(String role, int port) {
        this.role = role;
        this.port = port;
        this.ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    }

    @Override
    public void run() {
        String peerID, transactionID = "", verifySenderHash = "", receiverDID = "", appName, senderPrivatePos = "",
                senderDidIpfsHash = "", senderPID = "";
        while (true) {
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

            ServerSocket serverSocket = null;
            Socket socket = null;
            try {
                File creditsFolder = new File(Functions.WALLET_DATA_PATH.concat("/Credits"));
                if (!creditsFolder.exists())
                    creditsFolder.mkdirs();
                peerID = getPeerID(DATA_PATH + "DID.json");
                String didHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID);
                appName = peerID.concat(role);

                listen(appName, port);

                QuorumConsensusLogger.debug("Quorum Listening on " + port + " appname " + appName);
                serverSocket = new ServerSocket(port);
                socket = serverSocket.accept();

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintStream out = new PrintStream(socket.getOutputStream());

                JSONObject readSenderData;
                String operation = null;
                try {
                    operation = in.readLine();
                } catch (SocketException e) {
                    QuorumConsensusLogger.debug("Sender Input Stream Null - Operation");
                    socket.close();
                    serverSocket.close();
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                }
                if (operation.equals("new-credits-mining")) {
                    QuorumConsensusLogger.debug("New Credits");
                    String getNewCreditsData = null;
                    try {
                        getNewCreditsData = in.readLine();
                    } catch (SocketException e) {
                        QuorumConsensusLogger.debug("Sender Input Stream Null - New Credits Details");
                        socket.close();
                        serverSocket.close();
                        executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    }
                    // Verify QST Credits
                    JSONObject qstObject = new JSONObject(getNewCreditsData);

                    // Get level of token from advisory node
                    int creditsRequired = 0;
                    JSONObject resJsonData_credit = new JSONObject();
                    String GET_URL_credit = SYNC_IP + "/getlevel";
                    URL URLobj_credit = new URL(GET_URL_credit);
                    HttpURLConnection con_credit = (HttpURLConnection) URLobj_credit.openConnection();
                    con_credit.setRequestMethod("GET");
                    int responseCode_credit = con_credit.getResponseCode();
                    System.out.println("GET Response Code :: " + responseCode_credit);
                    if (responseCode_credit == HttpURLConnection.HTTP_OK) {
                        BufferedReader in_credit = new BufferedReader(
                                new InputStreamReader(con_credit.getInputStream()));
                        String inputLine_credit;
                        StringBuffer response_credit = new StringBuffer();
                        while ((inputLine_credit = in_credit.readLine()) != null) {
                            response_credit.append(inputLine_credit);
                        }
                        in_credit.close();
                        QuorumConsensusLogger.debug("response from service " + response_credit.toString());
                        resJsonData_credit = new JSONObject(response_credit.toString());
                        int level_credit = resJsonData_credit.getInt("level");
                        creditsRequired = (int) Math.pow(2, (2 + level_credit));
                        QuorumConsensusLogger.debug("credits required " + creditsRequired);

                    } else
                        QuorumConsensusLogger.debug("GET request not worked");

                    // Level 1 Verification: Verify hash of n objects
                    JSONArray qstArray = qstObject.getJSONArray("qstArray");
                    JSONArray creditsArray = qstObject.getJSONArray("credits");

                    boolean flag = true;
                    for (int i = 0; i < creditsRequired; i++) {
                        QuorumConsensusLogger.debug("Credit object: " + creditsArray.getJSONObject(i).toString());
                        QuorumConsensusLogger.debug(
                                "Credit Hash: " + calculateHash(creditsArray.getJSONObject(i).toString(), "SHA3-256"));
                        String reHash = calculateHash(qstArray.getJSONObject(i).getString("credits"), "SHA3-256");
                        if (!reHash.equals(qstArray.getJSONObject(i).getString("creditHash"))) {
                            QuorumConsensusLogger.debug("Recalculation " + reHash + " - "
                                    + qstArray.getJSONObject(i).getString("creditHash"));
                            flag = false;
                        }
                    }
                    if (flag) {

                        boolean verifySigns = true;
                        for (int i = 0; i < creditsRequired; i++) {
                            if (!Authenticate.verifySignature(creditsArray.getJSONObject(i).toString()))
                                verifySigns = false;
                        }
                        if (verifySigns) {
                            HashSet hashSet = new HashSet();
                            long startTime = System.currentTimeMillis();
                            for (int i = 0; i < creditsArray.length(); i++) {
                                String sign = creditsArray.getJSONObject(i).getString("signature");
                                String signHash = calculateHash(sign, "SHA3-256");
                                hashSet.add(signHash);
                            }
                            long endTime = System.currentTimeMillis();
                            QuorumConsensusLogger.debug("Total Time for HashSet: " + (endTime - startTime));
                            if (hashSet.size() == qstArray.length() * 15) {
                                QuorumConsensusLogger.debug("Mining Verified");
                                out.println("Verified");
                            } else {
                                QuorumConsensusLogger
                                        .debug("HashSet: " + hashSet.size() + " QST Size " + qstArray.length());
                                QuorumConsensusLogger.debug("Mining Not Verified: Duplicates Found");
                                out.println("440");
                                socket.close();
                                serverSocket.close();
                                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                            }
                        } else {
                            out.println("441");
                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                        }
                    } else {
                        out.println("442");
                        socket.close();
                        serverSocket.close();
                        executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    }
                } else if (operation.equals("NFT")) {
                    String getRecData = null;
                    try {
                        getRecData = in.readLine();
                    } catch (SocketException e) {
                        QuorumConsensusLogger.debug("Sender Input Stream Null - Ping Check / Receiver Details");
                        socket.close();
                        serverSocket.close();
                        executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    }

                    String quorumHash = null, nftQuorumHash = null;
                    JSONObject detailsToVerify = new JSONObject();
                    if (getRecData != null) {
                        if (getRecData.contains("ping check")) {
                            QuorumConsensusLogger.debug("Ping check from sender: " + getRecData);
                            out.println("pong response");
                        } else {

                            QuorumConsensusLogger.debug("Received Details from initiator: " + getRecData);
                            readSenderData = new JSONObject(getRecData);
                            senderPrivatePos = readSenderData.getString("sign");
                            senderDidIpfsHash = readSenderData.getString("senderDID");
                            transactionID = readSenderData.getString("Tid");
                            verifySenderHash = readSenderData.getString("Hash");
                            receiverDID = readSenderData.getString("RID");

                            syncDataTable(senderDidIpfsHash, null);

                            senderPID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", senderDidIpfsHash);
                            String senderWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash",
                                    senderDidIpfsHash);

                            nodeData(senderDidIpfsHash, senderWidIpfsHash, ipfs);
                            String nftSellerDID=receiverDID;
                            quorumHash = calculateHash(verifySenderHash.concat(nftSellerDID), "SHA3-256");

                            QuorumConsensusLogger.debug("details that quorum use to put RBT Signature");
                            QuorumConsensusLogger.debug("1: " + verifySenderHash);
                            QuorumConsensusLogger.debug("2: " + nftSellerDID);
                            QuorumConsensusLogger.debug("Quorum hash: " + quorumHash);

                            detailsToVerify.put("did", senderDidIpfsHash);
                            detailsToVerify.put("hash", verifySenderHash);
                            detailsToVerify.put("signature", senderPrivatePos);

                            /*
                             * writeToFile(LOGGER_PATH + "tempverifysenderhash", verifySenderHash, false);
                             * String verifySenderIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH +
                             * "tempverifysenderhash", ipfs);
                             * deleteFile(LOGGER_PATH + "tempverifysenderhash");
                             */
                        }
                    }

                    String nftDetails = null;
                    try {
                        nftDetails = in.readLine();
                    } catch (SocketException e) {
                        QuorumConsensusLogger.debug("Sender Input Stream Null - Ping Check / Receiver Details");
                        socket.close();
                        serverSocket.close();
                        executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    }
                    if (nftDetails != null) {
                        JSONObject nftDetailsObject = new JSONObject(nftDetails);
                        String nftSaleContractIpfsHash = nftDetailsObject.getString("saleContractIpfsHash");

                        String saleContractContent = get(nftSaleContractIpfsHash, ipfs);
                        JSONObject saleContractObject = new JSONObject(saleContractContent);

                        String saleSignature = saleContractObject.getString("sign");

                        JSONObject data1 = new JSONObject();
                        data1.put("sellerDID",
                                nftDetailsObject.getJSONObject("nftTokenDetails").getString("sellerDid"));
                        data1.put("nftToken", nftDetailsObject.getJSONObject("nftTokenDetails").getString("nftToken"));
                        data1.put("rbtAmount", nftDetailsObject.getDouble("tokenAmount"));

                        PublicKey pubKey = getPubKeyFromStr(
                                get(nftDetailsObject.getString("sellerPubKeyIpfsHash"), ipfs));

                        QuorumConsensusLogger.debug("Recreated String to verify nft Signture " + data1.toString());
                        QuorumConsensusLogger
                                .debug("Contents of SaleContract fetched from ipfs " + saleContractObject.toString());
                        QuorumConsensusLogger.debug("Signature contained inside the saleContract" + saleSignature);
                        QuorumConsensusLogger.debug(
                                "seller Public Key ipfs hash" + nftDetailsObject.getString("sellerPubKeyIpfsHash"));

                        if (verifySignature(data1.toString(), pubKey, saleSignature)) {
                            QuorumConsensusLogger.debug("Quorom Authenticated NFT sale contract");
                            out.println("Contract_verified");

                        } else {
                            QuorumConsensusLogger.debug("NFT Sale Authentication Failure - Quorum");
                            out.println("NFT_Sale_Auth_Failed");
                        }

                        String verifyNftSenderHash = nftDetailsObject.getString("nftHash");

                        if (verifySignature(data1.toString(), pubKey, saleSignature)) {
                            QuorumConsensusLogger.debug("Quorom Authenticated Sender NFT Signature");

                            nftQuorumHash = calculateHash(
                                    verifyNftSenderHash.concat(nftDetailsObject.getString("nftBuyerDid")), "SHA3-256");
                            String QuorumNFTSignature = getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                    nftQuorumHash);
                            out.println(QuorumNFTSignature);

                            String nftCreditSignatures = null;
                            try {
                                nftCreditSignatures = in.readLine();
                            } catch (SocketException e) {
                                QuorumConsensusLogger.debug("Sender Input Stream Null - Credits");
                                socket.close();
                                serverSocket.close();
                                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                            }
                            QuorumConsensusLogger.debug("nft credit Signature " + nftCreditSignatures);

                            if (!nftCreditSignatures.equals("null")) { // commented as per test for multiple consensus
                                                                       // threads

                                FileWriter shareWriter = new FileWriter(new File(LOGGER_PATH + "mycredit.txt"), true);
                                shareWriter.write(nftCreditSignatures);
                                shareWriter.close();
                                File readCredit = new File(LOGGER_PATH + "mycredit.txt");
                                String credit = add(readCredit.toString(), ipfs);

                                File creditFile = new File(
                                        WALLET_DATA_PATH.concat("/Credits/").concat(credit).concat(".json"));
                                if (!creditFile.exists())
                                    creditFile.createNewFile();
                                writeToFile(creditFile.toString(), nftCreditSignatures, false);

                                QuorumConsensusLogger.debug("Credit object: " + credit);
                                QuorumConsensusLogger.debug("Credit Hash: " + calculateHash(credit, "SHA3-256"));
                                JSONObject storeDetailsQuorum = new JSONObject();
                                storeDetailsQuorum.put("tid", transactionID);
                                storeDetailsQuorum.put("consensusID", verifySenderHash);
                                storeDetailsQuorum.put("sign", senderPrivatePos);
                                storeDetailsQuorum.put("credits", credit);
                                storeDetailsQuorum.put("creditHash", calculateHash(credit, "SHA3-256"));
                                storeDetailsQuorum.put("senderdid",
                                        nftDetailsObject.getJSONObject("nftTokenDetails").getString("sellerDid"));
                                storeDetailsQuorum.put("Date", Functions.getCurrentUtcTime());
                                storeDetailsQuorum.put("recdid",
                                        nftDetailsObject.getJSONObject("rbtTokenDetails").getString("sender"));
                                JSONArray data = new JSONArray();
                                data.put(storeDetailsQuorum);
                                QuorumConsensusLogger.debug("Quorum Share: " + credit);
                                updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json", data.toString());
                                deleteFile(LOGGER_PATH + "mycredit.txt");
                                writeToFile(LOGGER_PATH + "consenusIDhash", verifySenderHash, false);
                                String consenusIDhash = IPFSNetwork.add(LOGGER_PATH + "consenusIDhash", ipfs);
                                deleteFile(LOGGER_PATH + "consenusIDhash");
                                QuorumConsensusLogger.debug("added consensus ID " + consenusIDhash);
                            }

                        } else {
                            QuorumConsensusLogger.debug("NFT Sender signature Authentication Failure - Quorum");
                            out.println("NFT_Auth_Failed");
                        }

                        if (Authenticate.verifySignature(detailsToVerify.toString())) {
                            QuorumConsensusLogger.debug("Quorum Authenticated Sender");

                            QuorumConsensusLogger.debug("ConsensusID pass");
                            String QuorumSignature = getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                    quorumHash);
                            out.println(QuorumSignature);

                            String creditSignatures = null;
                            try {
                                creditSignatures = in.readLine();
                            } catch (SocketException e) {
                                QuorumConsensusLogger.debug("Sender Input Stream Null - Credits");
                                socket.close();
                                serverSocket.close();
                                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                            }
                            QuorumConsensusLogger.debug("credit Signature " + creditSignatures);

                            if (!creditSignatures.equals("null")) { // commented as per test for multiple consensus
                                                                    // threads

                                FileWriter shareWriter = new FileWriter(new File(LOGGER_PATH + "mycredit.txt"), true);
                                shareWriter.write(creditSignatures);
                                shareWriter.close();
                                File readCredit = new File(LOGGER_PATH + "mycredit.txt");
                                String credit = add(readCredit.toString(), ipfs);

                                File creditFile = new File(
                                        WALLET_DATA_PATH.concat("/Credits/").concat(credit).concat(".json"));
                                if (!creditFile.exists())
                                    creditFile.createNewFile();
                                writeToFile(creditFile.toString(), creditSignatures, false);

                                QuorumConsensusLogger.debug("Credit object: " + credit);
                                QuorumConsensusLogger.debug("Credit Hash: " + calculateHash(credit, "SHA3-256"));
                                JSONObject storeDetailsQuorum = new JSONObject();
                                storeDetailsQuorum.put("tid", transactionID);
                                storeDetailsQuorum.put("consensusID", verifySenderHash);
                                storeDetailsQuorum.put("sign", senderPrivatePos);
                                storeDetailsQuorum.put("credits", credit);
                                storeDetailsQuorum.put("creditHash", calculateHash(credit, "SHA3-256"));
                                storeDetailsQuorum.put("senderdid", senderDidIpfsHash);
                                storeDetailsQuorum.put("Date", Functions.getCurrentUtcTime());
                                storeDetailsQuorum.put("recdid", receiverDID);
                                JSONArray data = new JSONArray();
                                data.put(storeDetailsQuorum);
                                QuorumConsensusLogger.debug("Quorum Share: " + credit);
                                updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json", data.toString());
                                deleteFile(LOGGER_PATH + "mycredit.txt");
                                writeToFile(LOGGER_PATH + "consenusIDhash", verifySenderHash, false);
                                String consenusIDhash = IPFSNetwork.add(LOGGER_PATH + "consenusIDhash", ipfs);
                                deleteFile(LOGGER_PATH + "consenusIDhash");
                                QuorumConsensusLogger.debug("added consensus ID " + consenusIDhash);
                            }
                        } else {
                            QuorumConsensusLogger.debug("RBT Sender signature Authentication Failure - Quorum");
                            out.println("RBT_Auth_Failed");
                        }

                    }

                } //section for normal RBT transfer
                else{
                    String getRecData = null;
                    try {
                        getRecData = in.readLine();
                    } catch (SocketException e) {
                        QuorumConsensusLogger.debug("Sender Input Stream Null - Ping Check / Receiver Details");
                        socket.close();
                        serverSocket.close();
                        executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    }

                    if (getRecData != null) {
                        if (getRecData.contains("ping check")) {
                            QuorumConsensusLogger.debug("Ping check from sender: " + getRecData);
                            out.println("pong response");
                        } else {

                            QuorumConsensusLogger.debug("Received Details from initiator: " + getRecData);
                            readSenderData = new JSONObject(getRecData);
                            senderPrivatePos = readSenderData.getString("sign");
                            senderDidIpfsHash = readSenderData.getString("senderDID");
                            transactionID = readSenderData.getString("Tid");
                            verifySenderHash = readSenderData.getString("Hash");
                            receiverDID = readSenderData.getString("RID");

                            syncDataTable(senderDidIpfsHash, null);

                            senderPID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", senderDidIpfsHash);
                            String senderWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash",
                                    senderDidIpfsHash);

                            nodeData(senderDidIpfsHash, senderWidIpfsHash, ipfs);
                            String quorumHash = calculateHash(verifySenderHash.concat(receiverDID), "SHA3-256");

                            QuorumConsensusLogger.debug("1: " + verifySenderHash);
                            QuorumConsensusLogger.debug("2: " + receiverDID);
                            QuorumConsensusLogger.debug("Quorum hash: " + quorumHash);

                            JSONObject detailsToVerify = new JSONObject();
                            detailsToVerify.put("did", senderDidIpfsHash);
                            detailsToVerify.put("hash", verifySenderHash);
                            detailsToVerify.put("signature", senderPrivatePos);

                            writeToFile(LOGGER_PATH + "tempverifysenderhash", verifySenderHash, false);
                            String verifySenderIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH + "tempverifysenderhash",
                                    ipfs);
                            deleteFile(LOGGER_PATH + "tempverifysenderhash");
                            if (Authenticate.verifySignature(detailsToVerify.toString())) {
                                QuorumConsensusLogger.debug("Quorum Authenticated Sender");

                                QuorumConsensusLogger.debug("ConsensusID pass");
                                String QuorumSignature = getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                        quorumHash);
                                out.println(QuorumSignature);

                                String creditSignatures = null;
                                try {
                                    creditSignatures = in.readLine();
                                } catch (SocketException e) {
                                    QuorumConsensusLogger.debug("Sender Input Stream Null - Credits");
                                    socket.close();
                                    serverSocket.close();
                                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                                }
                                QuorumConsensusLogger.debug("credit Signature " + creditSignatures);

                                if (!creditSignatures.equals("null")) { // commented as per test for multiple consensus
                                                                        // threads

                                    FileWriter shareWriter = new FileWriter(new File(LOGGER_PATH + "mycredit.txt"),
                                            true);
                                    shareWriter.write(creditSignatures);
                                    shareWriter.close();
                                    File readCredit = new File(LOGGER_PATH + "mycredit.txt");
                                    String credit = add(readCredit.toString(), ipfs);

                                    File creditFile = new File(
                                            WALLET_DATA_PATH.concat("/Credits/").concat(credit).concat(".json"));
                                    if (!creditFile.exists())
                                        creditFile.createNewFile();
                                    writeToFile(creditFile.toString(), creditSignatures, false);

                                    QuorumConsensusLogger.debug("Credit object: " + credit);
                                    QuorumConsensusLogger.debug("Credit Hash: " + calculateHash(credit, "SHA3-256"));
                                    JSONObject storeDetailsQuorum = new JSONObject();
                                    storeDetailsQuorum.put("tid", transactionID);
                                    storeDetailsQuorum.put("consensusID", verifySenderHash);
                                    storeDetailsQuorum.put("sign", senderPrivatePos);
                                    storeDetailsQuorum.put("credits", credit);
                                    storeDetailsQuorum.put("creditHash", calculateHash(credit, "SHA3-256"));
                                    storeDetailsQuorum.put("senderdid", senderDidIpfsHash);
                                    storeDetailsQuorum.put("Date", Functions.getCurrentUtcTime());
                                    storeDetailsQuorum.put("recdid", receiverDID);
                                    JSONArray data = new JSONArray();
                                    data.put(storeDetailsQuorum);
                                    QuorumConsensusLogger.debug("Quorum Share: " + credit);
                                    updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json",
                                            data.toString());
                                    deleteFile(LOGGER_PATH + "mycredit.txt");
                                    writeToFile(LOGGER_PATH + "consenusIDhash", verifySenderHash, false);
                                    String consenusIDhash = IPFSNetwork.add(LOGGER_PATH + "consenusIDhash", ipfs);
                                    deleteFile(LOGGER_PATH + "consenusIDhash");
                                    QuorumConsensusLogger.debug("added consensus ID " + consenusIDhash);
                                }

                            } else {
                                QuorumConsensusLogger.debug("Sender Authentication Failure - Quorum");
                                out.println("Auth_Failed");
                            }

                        }
                    }

                }
                

            } catch (IOException e) {
                QuorumConsensusLogger.error("IOException Occurred", e);
            } catch (JSONException e) {
                QuorumConsensusLogger.error("JSONException Occurred", e);
            } catch (NullPointerException e) {
                QuorumConsensusLogger.error("NullPointer Exception Occurred ", e);
            } catch (ParseException e) {
                QuorumConsensusLogger.error("ParseException Occurred ", e);
            } finally {
                try {
                    socket.close();
                    serverSocket.close();
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                } catch (IOException e) {
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    QuorumConsensusLogger.error("IOException Occurred", e);
                }

            }
            
        }
        
    }
}
