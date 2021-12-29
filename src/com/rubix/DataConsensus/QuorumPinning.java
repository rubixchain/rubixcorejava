package com.rubix.DataConsensus;

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
import static com.rubix.Resources.Functions.updateJSON;
import static com.rubix.Resources.Functions.writeToFile;
import static com.rubix.Resources.IPFSNetwork.add;
import static com.rubix.Resources.IPFSNetwork.listen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Constants.ConsensusConstants;
import com.rubix.Resources.IPFSNetwork;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class QuorumPinning implements Runnable {

    public static Logger QuorumPinningLogger = Logger.getLogger(QuorumPinning.class);

    int port;
    IPFS ipfs;
    String role;
    int round;

    public QuorumPinning(String role, int port) {
        this.role = role;
        this.port = port;
        this.ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    }

    @Override
    public void run() {

        while (true) {
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
            String peerID, appName;
            ServerSocket serverSocket = null;
            Socket socket = null;

            try {

                peerID = getPeerID(DATA_PATH + "DID.json");
                String didHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID);
                appName = peerID.concat(role);

                listen(appName, port);

                QuorumPinningLogger.debug("Quorum Listening on " + port + " appname " + appName);
                serverSocket = new ServerSocket(port);
                socket = serverSocket.accept();

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintStream out = new PrintStream(socket.getOutputStream());

                Thread validate = new SenderHandler(socket, in, out, ipfs, peerID, didHash, appName);
                validate.start();

            } catch (IOException e) {
                QuorumPinningLogger.error("Error in QuorumPinning: " + e.getMessage());
            }

        }
    }
}

class SenderHandler extends Thread {

    public static Logger SenderHandlerLogger = Logger.getLogger(SenderHandler.class);

    final Socket socket;
    final BufferedReader in;
    final PrintStream out;
    final IPFS ipfs;
    final String peerID;
    final String didHash;
    final String appName;

    public SenderHandler(Socket socket, BufferedReader in, PrintStream out, IPFS ipfs, String peerID, String didHash,
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
    String getData;

    JSONObject readSenderData;

    public void run() {

        while (true) {
            try {
                getData = in.readLine();

                if (getData.contains("ping check")) {
                    SenderHandlerLogger.debug("Ping check from sender: " + getData);
                    out.println("pong response");
                } else {
                    SenderHandlerLogger.debug("Received Details from initiator: " + getData);
                    readSenderData = new JSONObject(getData);
                    senderPrivatePos = readSenderData.getString("sign");
                    senderDidIpfsHash = readSenderData.getString("senderDID");
                    transactionID = readSenderData.getString("Tid");
                    verifySenderHash = readSenderData.getString("Hash");
                    blockHash = readSenderData.getString(ConsensusConstants.BLOCK);

                    senderPID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", senderDidIpfsHash);

                    String senderWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash",
                            senderDidIpfsHash);

                    nodeData(senderDidIpfsHash, senderWidIpfsHash, ipfs);
                    String quorumHash = calculateHash(verifySenderHash.concat(blockHash), "SHA3-256");

                    JSONObject detailsToVerify = new JSONObject();
                    detailsToVerify.put("did", senderDidIpfsHash);
                    detailsToVerify.put("hash", verifySenderHash);
                    detailsToVerify.put("signature", senderPrivatePos);

                    if (Authenticate.verifySignature(detailsToVerify.toString())) {
                        SenderHandlerLogger.debug("Quorum Authenticated Sender");

                        IPFSNetwork.pin(blockHash, ipfs);

                        String blockHashData = IPFSNetwork.get(blockHash, ipfs);
                        JSONObject blockDataObject = new JSONObject(blockHashData);
                        JSONArray blockArray = new JSONArray(blockDataObject.getJSONObject("metadata"));

                        for (int i = 0; i < blockArray.length(); i++) {
                            JSONObject blockObject = blockArray.getJSONObject(i);
                            String blockFileHash = blockObject.getString("file_uid");
                            IPFSNetwork.pin(blockFileHash, ipfs);
                        }

                        String QuorumSignature = getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                quorumHash);
                        out.println(QuorumSignature);
                        String creditval;
                        creditval = in.readLine();
                        SenderHandlerLogger.debug("credit value " + creditval);

                        if (!creditval.equals("null")) { // commented as per test for multiple consensus threads

                            FileWriter shareWriter = new FileWriter(new File(LOGGER_PATH + "mycredit.txt"), true);
                            shareWriter.write(creditval);
                            shareWriter.close();
                            File readCredit = new File(LOGGER_PATH + "mycredit.txt");
                            String credit = add(readCredit.toString(), ipfs);

                            // adding credit to credit mapping
                            JSONArray CreditBody = new JSONArray(creditval);
                            JSONObject creditMappingObject = new JSONObject();
                            JSONArray creditMappingArray = new JSONArray();

                            for (int i = 0; i < CreditBody.length(); i++) {
                                JSONObject object = CreditBody.getJSONObject(i);
                                String key = object.getString("did");
                                String sign = object.getString("sign");
                                String creditHash = calculateHash(sign, "SHA3-256");

                                creditMappingObject.put("did", key);
                                creditMappingObject.put("sign", sign);
                                creditMappingObject.put("hash", creditHash);
                                creditMappingObject.put("tid", transactionID);

                                creditMappingArray.put(creditMappingObject);

                                writeToFile(WALLET_DATA_PATH + "CreditMapping.json", creditMappingArray.toString(),
                                        false);

                            }

                            JSONObject storeDetailsQuorum = new JSONObject();
                            storeDetailsQuorum.put("tid", transactionID);
                            storeDetailsQuorum.put("consensusID", verifySenderHash);
                            storeDetailsQuorum.put("sign", senderPrivatePos);
                            storeDetailsQuorum.put("credits", credit);
                            storeDetailsQuorum.put("senderdid", senderDidIpfsHash);
                            storeDetailsQuorum.put("blockHash", blockHash);

                            JSONArray data = new JSONArray();
                            data.put(storeDetailsQuorum);
                            SenderHandlerLogger.debug("Quorum Share: " + credit);
                            updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json", data.toString());
                            deleteFile(LOGGER_PATH + "mycredit.txt");
                            writeToFile(LOGGER_PATH + "consenusIDhash", verifySenderHash, false);
                            String consenusIDhash = IPFSNetwork.add(LOGGER_PATH + "consenusIDhash", ipfs);
                            deleteFile(LOGGER_PATH + "consenusIDhash");
                            SenderHandlerLogger.debug("added consensus ID " + consenusIDhash);
                        }
                    } else {
                        SenderHandlerLogger.debug("Sender Authentication Failure - Quorum");
                        out.println("Auth_Failed");
                    }
                }
            } catch (Exception e) {
                SenderHandlerLogger.error("Exception in SenderHandler: " + e);
            }
        }
    }

}