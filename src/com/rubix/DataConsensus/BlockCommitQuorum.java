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
import static com.rubix.Resources.Functions.readFile;
import static com.rubix.Resources.Functions.updateJSON;
import static com.rubix.Resources.Functions.writeToFile;
import static com.rubix.Resources.IPFSNetwork.add;
import static com.rubix.Resources.IPFSNetwork.executeIPFSCommands;
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
import org.json.JSONException;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class BlockCommitQuorum implements Runnable {

    public static Logger BlockCommitQuorumLogger = Logger.getLogger(BlockCommitQuorum.class);

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

    public BlockCommitQuorum(String role, int port) {
        this.role = role;
        this.port = port;
        this.ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    }

    @Override
    public void run() {
        while (true) {
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
            boolean integrityCheck = true;
            String temp, peerID, transactionID, verifySenderHash, blockHash, receiverPID, appName, senderPrivatePos,
                    senderDidIpfsHash = "", senderPID = "";
            ServerSocket serverSocket = null;
            Socket socket = null;
            try {

                peerID = getPeerID(DATA_PATH + "DID.json");
                String didHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID);
                appName = peerID.concat(role);

                listen(appName, port);

                BlockCommitQuorumLogger.debug("Quorum Listening on " + port + " appname " + appName);
                serverSocket = new ServerSocket(port);
                socket = serverSocket.accept();

                BufferedReader dataReq = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintStream dataResp = new PrintStream(socket.getOutputStream());
                PrintStream out = new PrintStream(socket.getOutputStream());

                JSONObject readSenderData;
                String getData;
                String qstReq;

                // ? check for incoming request for QST

                qstReq = dataReq.readLine();
                if (qstReq.contains("qstcmrequest")) {

                    BlockCommitQuorumLogger
                            .debug("Sender reqesting QuorumSignedTransactions.json and CreditMapping.json: " + qstReq);

                    File creditsMapping = new File(WALLET_DATA_PATH + "CreditMapping.json");
                    if (!creditsMapping.exists()) {
                        BlockCommitQuorumLogger.debug("File doesn't exist");
                        creditsMapping.createNewFile();
                        writeToFile(creditsMapping.toString(), "[]", false);
                    }
                    JSONArray qstContent = new JSONArray(readFile(WALLET_DATA_PATH + "QuorumSignedTransactions.json"));
                    JSONObject qstObjectSend = new JSONObject();
                    if (qstContent.length() > 0)
                        qstObjectSend = qstContent.getJSONObject(qstContent.length() - 1);

                    String cmFileHash = IPFSNetwork.add(WALLET_DATA_PATH + "CreditMapping.json", ipfs);

                    JSONObject qResponse = new JSONObject();
                    qResponse.put("QuorumSignedTransactions", qstObjectSend.toString());
                    qResponse.put("CreditMapping", cmFileHash);

                    dataResp.println(qResponse.toString());
                }

                // TODO: if the incoming request contains the keyword "request", push the QST to
                // IPFS and send the two hashes back to the sender.

                // ? This is where quorum fetched the data send from initiatorConsensus (Line
                // 148)

                getData = in.readLine();
                if (getData.contains("ping check")) {
                    BlockCommitQuorumLogger.debug("Ping check from sender: " + getData);
                    out.println("pong response");
                } else {
                    BlockCommitQuorumLogger.debug("Received Details from initiator: " + getData);
                    readSenderData = new JSONObject(getData);
                    senderPrivatePos = readSenderData.getString("sign");
                    senderDidIpfsHash = readSenderData.getString("senderDID");
                    transactionID = readSenderData.getString("Tid");
                    verifySenderHash = readSenderData.getString("Hash");
                    blockHash = readSenderData.getString(ConsensusConstants.BLOCK);

                    senderPID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", senderDidIpfsHash);
                    // receiverPID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", blockHash);

                    String senderWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash",
                            senderDidIpfsHash);

                    nodeData(senderDidIpfsHash, senderWidIpfsHash, ipfs);
                    String quorumHash = calculateHash(verifySenderHash.concat(blockHash), "SHA3-256");

                    JSONObject detailsToVerify = new JSONObject();
                    detailsToVerify.put("did", senderDidIpfsHash);
                    detailsToVerify.put("hash", verifySenderHash);
                    detailsToVerify.put("signature", senderPrivatePos);

                    // BlockCommitQuorumLogger.debug("Checking providers for: " + verifySenderHash);
                    // ArrayList dhtOwnersList = dhtOwnerCheck(verifySenderHash);
                    // BlockCommitQuorumLogger.debug("Providers: " + dhtOwnersList);
                    // boolean consensusIDcheck = true;
                    // if (dhtOwnersList.size() == 2 && dhtOwnersList.contains(senderPID)
                    //         && dhtOwnersList.contains(receiverPID))
                    //     consensusIDcheck = true;

                    // writeToFile(LOGGER_PATH + "tempverifysenderhash", verifySenderHash, false);
                    // String verifySenderIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH +
                    // "tempverifysenderhash", ipfs);
                    // deleteFile(LOGGER_PATH + "tempverifysenderhash");

                    //TODO: check minimum balance of sender is one RBT

                    //TODO: verifying the block hash, fetching block hash and pinning files in block file.
                    if (Authenticate.verifySignature(detailsToVerify.toString())) {
                        BlockCommitQuorumLogger.debug("Quorum Authenticated Sender");

                        IPFSNetwork.pin(blockHash, ipfs);

                        pinBlockFiles(blockHash, ipfs);
                        String QuorumSignature = getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                quorumHash);
                        out.println(QuorumSignature);
                        String creditval;
                        creditval = in.readLine();
                        BlockCommitQuorumLogger.debug("credit value " + creditval);

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

                                writeToFile(WALLET_DATA_PATH + "CreditMapping.json", creditMappingArray.toString(), false);

                            }

                            JSONObject storeDetailsQuorum = new JSONObject();
                            storeDetailsQuorum.put("tid", transactionID);
                            storeDetailsQuorum.put("consensusID", verifySenderHash);
                            storeDetailsQuorum.put("sign", senderPrivatePos);
                            storeDetailsQuorum.put("credits", credit);
                            storeDetailsQuorum.put("senderdid", senderDidIpfsHash);

                            //? During mining if QST file has blockHash instead of recieverID, then credit is counted as 2 for that transaction.
                            storeDetailsQuorum.put("blockHash", blockHash);

                            JSONArray data = new JSONArray();
                            data.put(storeDetailsQuorum);
                            BlockCommitQuorumLogger.debug("Quorum Share: " + credit);
                            updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json", data.toString());
                            deleteFile(LOGGER_PATH + "mycredit.txt");
                            writeToFile(LOGGER_PATH + "consenusIDhash", verifySenderHash, false);
                            String consenusIDhash = IPFSNetwork.add(LOGGER_PATH + "consenusIDhash", ipfs);
                            deleteFile(LOGGER_PATH + "consenusIDhash");
                            BlockCommitQuorumLogger.debug("added consensus ID " + consenusIDhash);
                        }
                    } else {
                        BlockCommitQuorumLogger.debug("Sender Authentication Failure - Quorum");
                        out.println("Auth_Failed");
                    }
                }
            } catch (IOException e) {
                BlockCommitQuorumLogger.error("IOException Occurred", e);
            } catch (JSONException e) {
                BlockCommitQuorumLogger.error("JSONException Occurred", e);
            } catch (NullPointerException e) {
                BlockCommitQuorumLogger.error("NullPointer Exception Occurred ", e);
            }

            finally {
                try {
                    socket.close();
                    serverSocket.close();
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                } catch (IOException e) {
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    BlockCommitQuorumLogger.error("IOException Occurred", e);
                }

            }
        }

    }

    private void pinBlockFiles(String blockHash, IPFS ipfs) throws JSONException {
        String blockHashData = IPFSNetwork.get(blockHash, ipfs);
        JSONObject blockDataObject = new JSONObject(blockHashData);
        JSONArray blockArray = new JSONArray(blockDataObject.getJSONObject("metadata"));
        
        for (int i = 0; i < blockArray.length(); i++) {
            JSONObject blockObject = blockArray.getJSONObject(i);
            String blockFileHash = blockObject.getString("file_uid");
            IPFSNetwork.pin(blockFileHash, ipfs);
        }
        
    }
}
