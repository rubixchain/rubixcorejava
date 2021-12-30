package com.rubix.Consensus;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.AuthenticateNode.PropImage;
import com.rubix.LevelDb.DataBase;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.Functions.deleteFile;
import static com.rubix.Resources.IPFSNetwork.*;

public class QuorumConsensus implements Runnable {


    public static Logger QuorumConsensusLogger = Logger.getLogger(QuorumConsensus.class);


    /**
     * This method is used to run a thread for Quorum Members
     * <p>This involves <ol> <li>Verify sender signature</li>
     * <li>Signing the transaction</li>
     * <li>Receiving share from sender</li></ol>
     */


    int port;
    IPFS ipfs;
    String role;
    int round;

    public QuorumConsensus(String role,int port){
        this.role = role;
        this.port = port;
        this.ipfs=new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    }

    @Override
    public void run() {
        while (true) {
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
            boolean integrityCheck=true;
            String temp, peerID, transactionID, verifySenderHash, receiverDID, receiverPID, appName, senderPrivatePos, senderDidIpfsHash="", senderPID = "";
            ServerSocket serverSocket = null;
            Socket socket = null;
            try {

                peerID = getPeerID(DATA_PATH + "DID.json");
                String didHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID);
                appName = peerID.concat(role);

                listen(appName, port);

                QuorumConsensusLogger.debug("Quorum Listening on " + port + " appname "+appName);
                 serverSocket = new ServerSocket(port);
                 socket = serverSocket.accept();

                BufferedReader dataReq = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintStream dataResp = new PrintStream(socket.getOutputStream());
                PrintStream out = new PrintStream(socket.getOutputStream());

                JSONObject readSenderData;
                String getData;
                String qstReq;

                //? check for incoming request for QST

                qstReq = dataReq.readLine();
                if (qstReq.contains("qstcmrequest")) {

                    QuorumConsensusLogger.debug("Sender reqesting QuorumSignedTransactions and CreditMapping.json: " + qstReq);

                    File creditsMapping = new File(WALLET_DATA_PATH + "CreditMapping.json");
                    if(!creditsMapping.exists()) {
                        QuorumConsensusLogger.debug("File doesn't exist");
                        creditsMapping.createNewFile();
                        writeToFile(creditsMapping.toString(), "[]", false);
                    }
                    //JSONArray qstContent = new JSONArray(readFile(WALLET_DATA_PATH + "QuorumSignedTransactions.json"));
                    JSONArray qstContent = new JSONArray(DataBase.sortedQstData());
                    JSONObject qstObjectSend = new JSONObject();
                    if(qstContent.length() > 0)
                    {
                        JSONObject tempJsonObject = qstContent.getJSONObject(qstContent.length() - 1);
                        qstObjectSend.put("senderdid", tempJsonObject.get("senderdid"));
                        qstObjectSend.put("credits", tempJsonObject.get("credits"));
                        qstObjectSend.put("sign", tempJsonObject.get("sign"));
                        qstObjectSend.put("tid", tempJsonObject.get("tid"));
                        qstObjectSend.put("minestatus", tempJsonObject.get("minestatus"));
                        qstObjectSend.put("consensusID", tempJsonObject.get("consensusID"));
                    }
                        

                    String cmFileHash = IPFSNetwork.add(WALLET_DATA_PATH + "CreditMapping.json", ipfs);

                    JSONObject qResponse = new JSONObject();
                    qResponse.put("QuorumSignedTransactions", qstObjectSend.toString());
                    qResponse.put("CreditMapping", cmFileHash);

                    dataResp.println(qResponse.toString());
                }


                //TODO: if the incoming request contains the keyword "request", push the QST to IPFS and send the two hashes back to the sender.

                //? This is where quorum fetched the data send from initiatorConsensus (Line 148)

                getData = in.readLine();
                if (getData.contains("ping check")) {
                    QuorumConsensusLogger.debug("Ping check from sender: " + getData);
                    out.println("pong response");
                }
                else {
                    QuorumConsensusLogger.debug("Received Details from initiator: " + getData);
                    readSenderData = new JSONObject(getData);
                    senderPrivatePos = readSenderData.getString("sign");
                    senderDidIpfsHash = readSenderData.getString("senderDID");
                    transactionID = readSenderData.getString("Tid");
                    verifySenderHash = readSenderData.getString("Hash");
                    receiverDID = readSenderData.getString("RID");

                    senderPID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", senderDidIpfsHash);
                    receiverPID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", receiverDID);

                    String senderWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash", senderDidIpfsHash);

                    nodeData(senderDidIpfsHash, senderWidIpfsHash, ipfs);
                    String quorumHash = calculateHash(verifySenderHash.concat(receiverDID), "SHA3-256");

                    JSONObject detailsToVerify = new JSONObject();
                    detailsToVerify.put("did", senderDidIpfsHash);
                    detailsToVerify.put("hash", verifySenderHash);
                    detailsToVerify.put("signature", senderPrivatePos);


                    QuorumConsensusLogger.debug("Checking providers for: " + verifySenderHash);
                    ArrayList dhtOwnersList = dhtOwnerCheck(verifySenderHash);
                    QuorumConsensusLogger.debug("Providers: " + dhtOwnersList);
                    boolean consensusIDcheck = false;
                    if(dhtOwnersList.size() == 2 && dhtOwnersList.contains(senderPID) && dhtOwnersList.contains(receiverPID))
                        consensusIDcheck = true;


//                    writeToFile(LOGGER_PATH + "tempverifysenderhash", verifySenderHash, false);
//                    String verifySenderIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH + "tempverifysenderhash", ipfs);
//                    deleteFile(LOGGER_PATH + "tempverifysenderhash");

                    if (Authenticate.verifySignature(detailsToVerify.toString()) && consensusIDcheck) {
                        QuorumConsensusLogger.debug("Quorum Authenticated Sender");
                        String QuorumSignature = getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png", quorumHash);
                        out.println(QuorumSignature);
                        String creditval;
                        creditval = in.readLine();
                        QuorumConsensusLogger.debug("credit value " + creditval);

                        if (!creditval.equals("null")) { //commented as per test for multiple consensus threads

                            FileWriter shareWriter = new FileWriter(new File(LOGGER_PATH + "mycredit.txt"), true);
                            shareWriter.write(creditval);
                            shareWriter.close();
                            File readCredit = new File(LOGGER_PATH + "mycredit.txt");
                            String credit = add(readCredit.toString(), ipfs);

                            // adding credit to credit mapping
                            JSONArray CreditBody = new JSONArray(creditval);
                            JSONObject creditMappingObject = new JSONObject();
                            JSONArray creditMappingArray = new JSONArray();

                            for(int i = 0; i < CreditBody.length(); i++){
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
                            //get the qst data size
                            JSONArray qstData= new JSONArray(DataBase.sortedQstData());
                            int qstDatalength= qstData.length();

                            JSONObject storeDetailsQuorum = new JSONObject();
                            JSONObject storedQuorumSign= new JSONObject();
                            storeDetailsQuorum.put("tid", transactionID);
                            storeDetailsQuorum.put("consensusID", verifySenderHash);
                            storeDetailsQuorum.put("credits", credit);
                            storeDetailsQuorum.put("senderdid", senderDidIpfsHash);
                            storeDetailsQuorum.put("recdid", receiverDID);
                            storeDetailsQuorum.put("serialNoQst", qstDatalength++);

                            storedQuorumSign.put("sign", senderPrivatePos);
                            storedQuorumSign.put("serialNoQsign", qstDatalength++);

                            DataBase.putDataQuorumSignTxn(transactionID, storeDetailsQuorum.toString());
                            DataBase.putDataQuorumSign(transactionID, storedQuorumSign.toString());

                            /* JSONArray data = new JSONArray();
                            data.put(storeDetailsQuorum);
                            QuorumConsensusLogger.debug("Quorum Share: " + credit);
                            updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json", data.toString()); */
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
            } catch (IOException e) {
                QuorumConsensusLogger.error("IOException Occurred", e);
            } catch (JSONException e) {
                QuorumConsensusLogger.error("JSONException Occurred", e);
            } catch (NullPointerException | InterruptedException e) {
                QuorumConsensusLogger.error("NullPointer Exception Occurred ",e);
            }

            finally{
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
