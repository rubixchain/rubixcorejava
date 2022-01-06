package com.rubix.Mining;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.text.ParseException;
import java.util.HashSet;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;

public class MiningQuorum implements Runnable {


    public static Logger MiningQuorumLogger = Logger.getLogger(MiningQuorum.class);


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

    public MiningQuorum(String role, int port) {
        this.role = role;
        this.port = port;
        this.ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    }

    @Override
    public void run() {
        while (true) {
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
            String peerID, transactionID, verifySenderHash, receiverDID, appName, senderPrivatePos, senderDidIpfsHash = "", senderPID = "";
            ServerSocket serverSocket = null;
            Socket socket = null;
            try {

                File creditsFolder = new File(WALLET_DATA_PATH.concat("/Credits"));
                if (!creditsFolder.exists())
                    creditsFolder.mkdirs();

                peerID = getPeerID(DATA_PATH + "DID.json");
                String didHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID);
                appName = peerID.concat(role).concat("mining");

                listen(appName, port);

                MiningQuorumLogger.debug("Quorum Listening on " + port + " appname " + appName);
                serverSocket = new ServerSocket(port);
                socket = serverSocket.accept();

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintStream out = new PrintStream(socket.getOutputStream());

                JSONObject readSenderData;
                String getData = in.readLine();
                if (getData.contains("ping check")) {
                    MiningQuorumLogger.debug("Ping check from sender: " + getData);
                    out.println("pong response");
                } else {

                    //Verify QST Credits
                    JSONObject qstObject = new JSONObject(getData);

                    //Get level of token from advisory node
                    int creditsRequired = 0;
                    JSONObject resJsonData_credit = new JSONObject();
                    String GET_URL_credit = SYNC_IP + "/getlevel";
                    URL URLobj_credit = new URL(GET_URL_credit);
                    HttpURLConnection con_credit = (HttpURLConnection) URLobj_credit.openConnection();
                    con_credit.setRequestMethod("GET");
                    int responseCode_credit = con_credit.getResponseCode();
                    System.out.println("GET Response Code :: " + responseCode_credit);
                    if (responseCode_credit == HttpURLConnection.HTTP_OK) {
                        BufferedReader in_credit = new BufferedReader(new InputStreamReader(con_credit.getInputStream()));
                        String inputLine_credit;
                        StringBuffer response_credit = new StringBuffer();
                        while ((inputLine_credit = in_credit.readLine()) != null) {
                            response_credit.append(inputLine_credit);
                        }
                        in_credit.close();
                        MiningQuorumLogger.debug("response from service " + response_credit.toString());
                        resJsonData_credit = new JSONObject(response_credit.toString());
                        int level_credit = resJsonData_credit.getInt("level");
                        creditsRequired = (int) Math.pow(2, (2 + level_credit));
                        MiningQuorumLogger.debug("credits required " + creditsRequired);

                    } else
                        MiningQuorumLogger.debug("GET request not worked");



                    //Level 1 Verification: Verify hash of n objects
                    JSONArray creditsArray = qstObject.getJSONArray("credits");
                    JSONArray qstArray = qstObject.getJSONArray("qstArray");

                    boolean flag = true;
                    for (int i = 0; i < creditsRequired; i++) {
                        String reHash = calculateHash(creditsArray.getJSONObject(i).toString(), "SHA3-256");
                        if (!reHash.equals(qstArray.getJSONObject(i).getString("creditHash")))
                            flag = false;
                    }
                    if(flag){

                        boolean verifySigns = true;
                        for (int i = 0; i < creditsRequired; i++) {
                            if (!Authenticate.verifySignature(creditsArray.getJSONObject(i).toString()))
                                verifySigns = false;
                        }
                        if(verifySigns){
                            HashSet hashSet = new HashSet();
                            long startTime = System.currentTimeMillis();
                            for (int i = 0; i < creditsArray.length(); i++) {
                                String sign = creditsArray.getJSONObject(i).getString("signature");
                                String signHash = calculateHash(sign, "SHA3-256");
                                hashSet.add(signHash);
                            }
                            long endTime = System.currentTimeMillis();
                            MiningQuorumLogger.debug("Total Time for HashSet: " + (endTime - startTime));
                            if (hashSet.size() == qstArray.length()*15) {
                                MiningQuorumLogger.debug("Mining Verified");
                                out.println("Verified");
                            }else {
                                MiningQuorumLogger.debug("HashSet: " + hashSet.size() + " QST Size " + qstArray.length());
                                MiningQuorumLogger.debug("Mining Not Verified: Duplicates Found");
                                out.println("440");
                                socket.close();
                                serverSocket.close();
                                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                            }
                        }else {
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

                    getData = in.readLine();
                    MiningQuorumLogger.debug("Received Details from initiator: " + getData);
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

                    JSONObject detailsToVerify = new JSONObject();
                    detailsToVerify.put("did", senderDidIpfsHash);
                    detailsToVerify.put("hash", verifySenderHash);
                    detailsToVerify.put("signature", senderPrivatePos);

                    writeToFile(LOGGER_PATH + "tempverifysenderhash", verifySenderHash, false);
                    String verifySenderIPFSHash = IPFSNetwork.addHashOnly(LOGGER_PATH + "tempverifysenderhash", ipfs);
                    deleteFile(LOGGER_PATH + "tempverifysenderhash");

                    if (Authenticate.verifySignature(detailsToVerify.toString())) {
                        MiningQuorumLogger.debug("Quorum Authenticated Sender");

                        MiningQuorumLogger.debug("ConsensusID pass");
                        String QuorumSignature = getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png", quorumHash);
                        out.println(QuorumSignature);
                        String creditSignatures = in.readLine();
                        MiningQuorumLogger.debug("credit value " + creditSignatures);

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
                            storeDetailsQuorum.put("creditHash", calculateHash(credit, "SHA3-256"));
                            storeDetailsQuorum.put("senderdid", senderDidIpfsHash);
                            storeDetailsQuorum.put("Date", getCurrentUtcTime());
                            storeDetailsQuorum.put("recdid", receiverDID);
                            JSONArray data = new JSONArray();
                            data.put(storeDetailsQuorum);
                            MiningQuorumLogger.debug("Quorum Share: " + credit);
                            updateJSON("add", WALLET_DATA_PATH + "QuorumSignedTransactions.json", data.toString());
                            deleteFile(LOGGER_PATH + "mycredit.txt");
                            writeToFile(LOGGER_PATH + "consenusIDhash", verifySenderHash, false);
                            String consenusIDhash = IPFSNetwork.add(LOGGER_PATH + "consenusIDhash", ipfs);
                            deleteFile(LOGGER_PATH + "consenusIDhash");
                            MiningQuorumLogger.debug("added consensus ID " + consenusIDhash);
                        }

                    } else {
                        MiningQuorumLogger.debug("Sender Authentication Failure - Quorum");
                        out.println("Auth_Failed");
                    }
                }
            } catch (IOException e) {
                MiningQuorumLogger.error("IOException Occurred", e);
            } catch (JSONException e) {
                MiningQuorumLogger.error("JSONException Occurred", e);
            } catch (NullPointerException e) {
                MiningQuorumLogger.error("NullPointer Exception Occurred ", e);
            } catch (ParseException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                    serverSocket.close();
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                } catch (IOException e) {
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    MiningQuorumLogger.error("IOException Occurred", e);
                }

            }
        }

    }
}
