package com.rubix.Consensus;

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
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;
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
            	 File creditsFolder = new File(Functions.WALLET_DATA_PATH.concat("/Credits"));
                 if (!creditsFolder.exists())
                   creditsFolder.mkdirs(); 
                peerID = getPeerID(DATA_PATH + "DID.json");
                String didHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID);
                appName = peerID.concat(role);

                listen(appName, port);

                QuorumConsensusLogger.debug("Quorum Listening on " + port + " appname "+appName);
                 serverSocket = new ServerSocket(port);
                 socket = serverSocket.accept();

               // BufferedReader dataReq = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
               // PrintStream dataResp = new PrintStream(socket.getOutputStream());
                PrintStream out = new PrintStream(socket.getOutputStream());

                JSONObject readSenderData;
                String getData;
                String qstReq;

                //? check for incoming request for QST

                getData = in.readLine();
                
                if(getData != null) {
                	if (getData.contains("ping check")) {
                        QuorumConsensusLogger.debug("Ping check from sender: " + getData);
                        out.println("pong response");
                    }else {

                        QuorumConsensusLogger.debug("Received Details from initiator: " + getData);
                        readSenderData = new JSONObject(getData);
                        senderPrivatePos = readSenderData.getString("sign");
                        senderDidIpfsHash = readSenderData.getString("senderDID");
                        transactionID = readSenderData.getString("Tid");
                        verifySenderHash = readSenderData.getString("Hash");
                        receiverDID = readSenderData.getString("RID");
                        
                        syncDataTable(senderDidIpfsHash, null);
                        
                        senderPID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", senderDidIpfsHash);
                        String senderWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash", senderDidIpfsHash);

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
                        String verifySenderIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH + "tempverifysenderhash", ipfs);
                        deleteFile(LOGGER_PATH + "tempverifysenderhash");

//                        QuorumConsensusLogger.debug("Checking providers for: " + verifySenderHash);
//                        ArrayList dhtOwnersList = dhtOwnerCheck(verifySenderHash);
//                        QuorumConsensusLogger.debug("Providers: " + dhtOwnersList);
//                        boolean consensusIDcheck = false;
//                        if(dhtOwnersList.size() <= 2 && dhtOwnersList.contains(senderPID))
//                            consensusIDcheck = true;

                        if (Authenticate.verifySignature(detailsToVerify.toString())) {
                            QuorumConsensusLogger.debug("Quorum Authenticated Sender");

                                QuorumConsensusLogger.debug("ConsensusID pass");
                                String QuorumSignature = getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png", quorumHash);
                                out.println(QuorumSignature);
                                
                                String creditSignatures = in.readLine();
                                QuorumConsensusLogger.debug("credit Signature " + creditSignatures);

                                if (!creditSignatures.equals("null")) { //commented as per test for multiple consensus threads

                                    FileWriter shareWriter = new FileWriter(new File(LOGGER_PATH + "mycredit.txt"), true);
                                    shareWriter.write(creditSignatures);
                                    shareWriter.close();
                                    File readCredit = new File(LOGGER_PATH + "mycredit.txt");
                                    String credit = add(readCredit.toString(), ipfs);
                                    pin(credit, ipfs);
                                    
                                    File creditFile = new File(WALLET_DATA_PATH.concat("/Credits/").concat(credit).concat(".json"));
                                    if (!creditFile.exists())
                                      creditFile.createNewFile(); 
                                    writeToFile(creditFile.toString(), creditSignatures, false);
                                    
                                    JSONObject storeDetailsQuorum = new JSONObject();
                                    storeDetailsQuorum.put("tid", transactionID);
                                    storeDetailsQuorum.put("consensusID", verifySenderHash);
                                    storeDetailsQuorum.put("sign", senderPrivatePos);
                                    storeDetailsQuorum.put("credits", credit);
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
								} /*
									 * else { JSONObject storeDetailsQuorum = new JSONObject();
									 * storeDetailsQuorum.put("tid", transactionID);
									 * storeDetailsQuorum.put("consensusID", verifySenderHash);
									 * storeDetailsQuorum.put("sign", senderPrivatePos);
									 * storeDetailsQuorum.put("credits", ""); storeDetailsQuorum.put("senderdid",
									 * senderDidIpfsHash); storeDetailsQuorum.put("Date",
									 * Functions.getCurrentUtcTime()); storeDetailsQuorum.put("recdid",
									 * receiverDID); JSONArray data = new JSONArray(); data.put(storeDetailsQuorum);
									 * updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json",
									 * data.toString()); }
									 */

                        } else {
                            QuorumConsensusLogger.debug("Sender Authentication Failure - Quorum");
                            out.println("Auth_Failed");
                        }
                    
                    }
                }else {
                	QuorumConsensusLogger.debug("Sender - "+didHash+" Connection is disconnected!"+ getData);
                    out.println(getData);
                }
               
            } catch (IOException e) {
                QuorumConsensusLogger.error("IOException Occurred", e);
            } catch (JSONException e) {
                QuorumConsensusLogger.error("JSONException Occurred", e);
            } catch (NullPointerException e) {
                QuorumConsensusLogger.error("NullPointer Exception Occurred ",e);
            }catch (ParseException e) {
            	 QuorumConsensusLogger.error("ParseException Occurred ",e);
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
