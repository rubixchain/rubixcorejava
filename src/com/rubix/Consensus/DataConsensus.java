package com.rubix.Consensus;


import static com.rubix.Constants.ConsensusConstants.*;
import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.IPFS_PORT;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.WALLET_DATA_PATH;
import static com.rubix.Resources.Functions.calculateHash;
import static com.rubix.Resources.Functions.deleteFile;
import static com.rubix.Resources.Functions.getPeerID;
import static com.rubix.Resources.Functions.getSignFromShares;
import static com.rubix.Resources.Functions.getValues;
import static com.rubix.Resources.Functions.nodeData;
import static com.rubix.Resources.Functions.syncDataTable;
import static com.rubix.Resources.Functions.updateJSON;
import static com.rubix.Resources.Functions.writeToFile;
import static com.rubix.Resources.IPFSNetwork.add;
import static com.rubix.Resources.IPFSNetwork.listen;
import static com.rubix.Resources.IPFSNetwork.pin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Datum.Dependency;
import com.rubix.Resources.IPFSNetwork;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class DataConsensus implements Runnable {

    public static Logger DataConsensusLogger = Logger.getLogger(DataConsensus.class);

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
    int round;

    public DataConsensus(String role, int port) {
        this.role = role;
        this.port = port;
        this.ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    }

    @Override
    public void run() {
        while (true) {
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
            String peerID, transactionID, verifySenderHash, receiverDID, appName, senderPrivatePos,
            senderDidIpfsHash = "", senderPID = "", ownerHash = "", initHash = "";
            ServerSocket serverSocket = null;            
            Socket socket = null;
            try {

                peerID = getPeerID(DATA_PATH + "DID.json");
                String didHash = Dependency.getDIDfromPID(peerID, Dependency.dataTableHashMap()); //getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID);
                appName = peerID.concat(role);

                listen(appName, port);

                DataConsensusLogger.debug("Quorum Listening on " + port + " appname " + appName);
                serverSocket = new ServerSocket(port);
                socket = serverSocket.accept();

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintStream out = new PrintStream(socket.getOutputStream());

                Thread validate = new ValidationHandler(socket, in, out, ipfs, peerID, didHash, appName);
                validate.start();

            } catch (IOException e) {
                DataConsensusLogger.error("IOException Occurred", e);
            } catch (NullPointerException e) {
                DataConsensusLogger.error("NullPointer Exception Occurred ", e);
            }
        }

    }
}

class ValidationHandler extends Thread {

    public static Logger ValidationHandler = Logger.getLogger(ValidationHandler.class);

    final Socket socket;
    final BufferedReader in;
    final PrintStream out;
    final IPFS ipfs;
    final String peerID;
    final String didHash;
    final String appName;

    public ValidationHandler(Socket socket, BufferedReader in, PrintStream out, IPFS ipfs, String peerID,
            String didHash,
            String appName) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.ipfs = ipfs;
        this.peerID = peerID;
        this.didHash = didHash;
        this.appName = appName;
        this.start();
    }

    boolean integrityCheck = true;
    String temp = "";
    String transactionID = "";
    String verifySenderHash = "";
    String blockHash = "";
    String senderPrivatePos = "";
    String senderDidIpfsHash = "";
    String senderPID = "";
    String getData = "";
    String receiverDID = "";

    JSONObject readSenderData;

    public void run() {

        while (true) {

            try {

                String getData;

                // TODO: check if initiator is sending ping check to see if it is alive
                getData = in.readLine();
                if (getData.contains("ping check")) {
                    ValidationHandler.debug("Ping check from sender: " + getData);
                    out.println("pong response");
                } else {
                    ValidationHandler.debug("Received Details from initiator: " + getData);
                    readSenderData = new JSONObject(getData);
                    senderPrivatePos = readSenderData.getString("sign");
                    senderDidIpfsHash = readSenderData.getString("senderDID");
                    transactionID = readSenderData.getString("Tid");
                    verifySenderHash = readSenderData.getString("Hash");

                    syncDataTable(senderDidIpfsHash, null);

                    senderPID = Dependency.getPIDfromDID(senderDidIpfsHash, Dependency.dataTableHashMap());//getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", senderDidIpfsHash);
                    String senderWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash",
                            senderDidIpfsHash);

                    nodeData(senderDidIpfsHash, senderWidIpfsHash, ipfs);

                    JSONObject detailsToVerify = new JSONObject();
                    detailsToVerify.put("did", senderDidIpfsHash);
                    detailsToVerify.put("hash", verifySenderHash);
                    detailsToVerify.put("signature", senderPrivatePos);

                    writeToFile(LOGGER_PATH + "tempverifysenderhash", verifySenderHash, false);
                    String verifySenderIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH + "tempverifysenderhash", ipfs);
                    deleteFile(LOGGER_PATH + "tempverifysenderhash");

                    // DataConsensusLogger.debug("Checking providers for: " + verifySenderHash);
                    // ArrayList dhtOwnersList = dhtOwnerCheck(verifySenderHash);
                    // DataConsensusLogger.debug("Providers: " + dhtOwnersList);
                    // boolean consensusIDcheck = false;
                    // if(dhtOwnersList.size() <= 2 && dhtOwnersList.contains(senderPID))
                    // consensusIDcheck = true;

                    // if for data, primary or secondary token condition starts here

                    if (readSenderData.getString(TRANS_TYPE) == DATA) {

                        blockHash = readSenderData.getString("blockHash");
                        String quorumHash = calculateHash(verifySenderHash.concat(blockHash), "SHA3-256");

                        if (Authenticate.verifySignature(detailsToVerify.toString())) {
                            ValidationHandler.debug("Quorum Authenticated Sender");

                            ValidationHandler.debug("ConsensusID pass");
                            String QuorumSignature = getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                    quorumHash);

                            out.println(QuorumSignature);
                            String creditval;
                            creditval = in.readLine();
                            ValidationHandler.debug("credit value " + creditval);

                            if (!creditval.equals("null")) {

                                // ? quorum pinning blockHash and files from sender
                                IPFSNetwork.pin(blockHash, ipfs);

                                String blockHashData = IPFSNetwork.get(blockHash, ipfs);
                                JSONObject blockDataObject = new JSONObject(blockHashData);
                                JSONArray blockArray = new JSONArray(blockDataObject.getJSONObject("metadata"));

                                for (int i = 0; i < blockArray.length(); i++) {
                                    JSONObject blockObject = blockArray.getJSONObject(i);
                                    String blockFileHash = blockObject.getString("file_uid");
                                    IPFSNetwork.pin(blockFileHash, ipfs);
                                }

                                FileWriter shareWriter = new FileWriter(new File(LOGGER_PATH + "mycredit.txt"), true);
                                shareWriter.write(creditval);
                                shareWriter.close();
                                File readCredit = new File(LOGGER_PATH + "mycredit.txt");
                                String credit = add(readCredit.toString(), ipfs);
                                pin(credit, ipfs);
                                // adding credit to credit mapping
                                JSONArray CreditBody = new JSONArray(creditval);
                                // JSONObject creditMappingObject = new JSONObject();
                                JSONArray creditMappingArray = new JSONArray();

                                for (int i = 0; i < CreditBody.length(); i++) {
                                    JSONObject creditMappingObject = new JSONObject();
                                    JSONObject object = CreditBody.getJSONObject(i);
                                    String key = object.getString("did");
                                    String sign = object.getString("sign");
                                    String creditHash = calculateHash(sign, "SHA3-256");

                                    creditMappingObject.put("did", key);
                                    creditMappingObject.put("sign", sign);
                                    creditMappingObject.put("hash", creditHash);
                                    creditMappingObject.put("tid", transactionID);

                                    creditMappingArray.put(creditMappingObject);

                                }
                                writeToFile(WALLET_DATA_PATH + "CreditMapping.json", creditMappingArray.toString(),
                                        false);

                                JSONObject storeDetailsQuorum = new JSONObject();
                                storeDetailsQuorum.put("tid", transactionID);
                                storeDetailsQuorum.put("consensusID", verifySenderHash);
                                storeDetailsQuorum.put("sign", senderPrivatePos);
                                storeDetailsQuorum.put("credits", credit);
                                storeDetailsQuorum.put("senderdid", senderDidIpfsHash);
                                storeDetailsQuorum.put("blockHash", blockHash);
                                JSONArray data = new JSONArray();
                                data.put(storeDetailsQuorum);
                                ValidationHandler.debug("Quorum Share: " + credit);
                                updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json", data.toString());
                                deleteFile(LOGGER_PATH + "mycredit.txt");
                                writeToFile(LOGGER_PATH + "consenusIDhash", verifySenderHash, false);
                                String consenusIDhash = IPFSNetwork.add(LOGGER_PATH + "consenusIDhash", ipfs);
                                deleteFile(LOGGER_PATH + "consenusIDhash");
                                ValidationHandler.debug("added consensus ID " + consenusIDhash);

                            } else {
                                JSONObject storeDetailsQuorum = new JSONObject();
                                storeDetailsQuorum.put("tid", transactionID);
                                storeDetailsQuorum.put("consensusID", verifySenderHash);
                                storeDetailsQuorum.put("sign", senderPrivatePos);
                                storeDetailsQuorum.put("credits", "");
                                storeDetailsQuorum.put("senderdid", senderDidIpfsHash);
                                storeDetailsQuorum.put("blockHash", blockHash);
                                JSONArray data = new JSONArray();
                                data.put(storeDetailsQuorum);
                                updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json", data.toString());
                            }

                        } else {
                            ValidationHandler.debug("Sender Authentication Failure - Quorum");
                            out.println("Auth_Failed");
                        }
                    }

                    if (readSenderData.getString(TRANS_TYPE) == PRIMARY) {

                        receiverDID = readSenderData.getString("RID");
                        String quorumHash = calculateHash(verifySenderHash.concat(receiverDID), "SHA3-256");

                        if (Authenticate.verifySignature(detailsToVerify.toString())) {
                            ValidationHandler.debug("Quorum Authenticated Sender");

                            ValidationHandler.debug("ConsensusID pass");
                            String QuorumSignature = getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                    quorumHash);
                            out.println(QuorumSignature);
                            String creditval;
                            creditval = in.readLine();
                            ValidationHandler.debug("credit value " + creditval);

                            if (!creditval.equals("null")) { // commented as per test for multiple consensus threads

                                FileWriter shareWriter = new FileWriter(new File(LOGGER_PATH + "mycredit.txt"), true);
                                shareWriter.write(creditval);
                                shareWriter.close();
                                File readCredit = new File(LOGGER_PATH + "mycredit.txt");
                                String credit = add(readCredit.toString(), ipfs);
                                pin(credit, ipfs);
                                // adding credit to credit mapping
                                JSONArray CreditBody = new JSONArray(creditval);
                                // JSONObject creditMappingObject = new JSONObject();
                                JSONArray creditMappingArray = new JSONArray();

                                for (int i = 0; i < CreditBody.length(); i++) {
                                    JSONObject creditMappingObject = new JSONObject();
                                    JSONObject object = CreditBody.getJSONObject(i);
                                    String key = object.getString("did");
                                    String sign = object.getString("sign");
                                    String creditHash = calculateHash(sign, "SHA3-256");

                                    creditMappingObject.put("did", key);
                                    creditMappingObject.put("sign", sign);
                                    creditMappingObject.put("hash", creditHash);
                                    creditMappingObject.put("tid", transactionID);

                                    creditMappingArray.put(creditMappingObject);

                                }
                                writeToFile(WALLET_DATA_PATH + "CreditMapping.json", creditMappingArray.toString(),
                                        false);

                                JSONObject storeDetailsQuorum = new JSONObject();
                                storeDetailsQuorum.put("tid", transactionID);
                                storeDetailsQuorum.put("consensusID", verifySenderHash);
                                storeDetailsQuorum.put("sign", senderPrivatePos);
                                storeDetailsQuorum.put("credits", credit);
                                storeDetailsQuorum.put("senderdid", senderDidIpfsHash);
                                storeDetailsQuorum.put("recdid", receiverDID);
                                JSONArray data = new JSONArray();
                                data.put(storeDetailsQuorum);
                                ValidationHandler.debug("Quorum Share: " + credit);
                                updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json", data.toString());
                                deleteFile(LOGGER_PATH + "mycredit.txt");
                                writeToFile(LOGGER_PATH + "consenusIDhash", verifySenderHash, false);
                                String consenusIDhash = IPFSNetwork.add(LOGGER_PATH + "consenusIDhash", ipfs);
                                deleteFile(LOGGER_PATH + "consenusIDhash");
                                ValidationHandler.debug("added consensus ID " + consenusIDhash);

                            } else {
                                JSONObject storeDetailsQuorum = new JSONObject();
                                storeDetailsQuorum.put("tid", transactionID);
                                storeDetailsQuorum.put("consensusID", verifySenderHash);
                                storeDetailsQuorum.put("sign", senderPrivatePos);
                                storeDetailsQuorum.put("credits", "");
                                storeDetailsQuorum.put("senderdid", senderDidIpfsHash);
                                storeDetailsQuorum.put("recdid", receiverDID);
                                JSONArray data = new JSONArray();
                                data.put(storeDetailsQuorum);
                                updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json", data.toString());
                            }

                        } else {
                            ValidationHandler.debug("Sender Authentication Failure - Quorum");
                            out.println("Auth_Failed");
                        }
                    }
                }
            } catch (Exception e) {
                ValidationHandler.debug("Exception in Quorum Consensus: " + e);
            }

        }
    }
}