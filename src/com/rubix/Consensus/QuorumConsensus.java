package com.rubix.Consensus;

import static com.rubix.Constants.ConsensusConstants.INIT_HASH;
import static com.rubix.Constants.MiningConstants.MINED_RBT;
import static com.rubix.Constants.MiningConstants.MINED_RBT_SIGN;
import static com.rubix.Constants.MiningConstants.MINE_ID;
import static com.rubix.Constants.MiningConstants.MINE_ID_SIGN;
import static com.rubix.Constants.MiningConstants.MINE_TID;
import static com.rubix.Constants.MiningConstants.MINING_TID_SIGN;
import static com.rubix.Constants.MiningConstants.STAKED_TOKEN_SIGN;
import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.IPFS_PORT;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.PAYMENTS_PATH;
import static com.rubix.Resources.Functions.SYNC_IP;
import static com.rubix.Resources.Functions.TOKENCHAIN_PATH;
import static com.rubix.Resources.Functions.TOKENS_PATH;
import static com.rubix.Resources.Functions.WALLET_DATA_PATH;
import static com.rubix.Resources.Functions.calculateHash;
import static com.rubix.Resources.Functions.deleteFile;
import static com.rubix.Resources.Functions.getPeerID;
import static com.rubix.Resources.Functions.getSignFromShares;
import static com.rubix.Resources.Functions.getValues;
import static com.rubix.Resources.Functions.initHash;
import static com.rubix.Resources.Functions.nodeData;
import static com.rubix.Resources.Functions.readFile;
import static com.rubix.Resources.Functions.syncDataTable;
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
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.text.ParseException;
import java.util.HashSet;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

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
        while (true) {
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
            String peerID, transactionID, verifySenderHash, receiverDID, appName, senderPrivatePos,
                    senderDidIpfsHash = "", senderPID = "", ownerHash = "", initHash = "";
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

                // ? staking logic starts here

                if (operation.equals("alpha-stake-token")) {

                    QuorumConsensusLogger.debug("Staking 1 RBT for incoming mining transaction...");
                    String response = null;

                    try {

                        // ! token hash to be mined
                        response = in.readLine();
                    } catch (SocketException e) {
                        QuorumConsensusLogger.debug("Sender Input Stream Null - New Credits Details");
                        socket.close();
                        serverSocket.close();
                        executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    }

                    // ! check token is in same level and not already mined
                    if (response != null) {

                        // valid mined token hash

                        QuorumConsensusLogger.debug("Sending staking token details...");
                        String mineID = null;
                        JSONArray tokenToStake = new JSONArray();

                        String bankFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
                        JSONArray bankArray = new JSONArray(bankFile);

                        // pick last object from bank array
                        JSONObject bankObject = bankArray.getJSONObject(0);
                        String tokenHash = bankObject.getString("tokenHash");
                        tokenToStake.put(tokenHash);

                        File tokenFile = new File(TOKENS_PATH + tokenHash);
                        File tokenchainFile = new File(TOKENCHAIN_PATH + tokenHash + ".json");

                        if (tokenFile.exists() && tokenchainFile.exists()) {

                            String tokenChain = readFile(TOKENCHAIN_PATH + tokenHash + ".json");
                            tokenToStake.put(tokenChain);

                            // ! token which will be staked
                            out.println(tokenToStake);
                        } else {
                            QuorumConsensusLogger.debug("Token Staking Failed: Token to stake not found");
                            out.println("445");
                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                        }

                        // ! mine details to sign
                        JSONObject mineDetToSign = new JSONObject();

                        try {
                            String mineData = in.readLine();
                            // convert mineData to JSONObject
                            mineDetToSign = new JSONObject(mineData);
                        } catch (SocketException e) {
                            QuorumConsensusLogger.debug("Sender Input Stream Null - Stake ID details (to be signed)");
                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                        }

                        if (mineDetToSign.length() == 3 && mineDetToSign.has(
                                MINE_ID) && mineDetToSign.has(
                                        MINED_RBT)
                                && mineDetToSign.has(MINE_TID)) {
                            JSONObject stakingSigns = new JSONObject();

                            QuorumConsensusLogger.debug("Mine ID: " + mineDetToSign.getString(MINE_ID));

                            stakingSigns.put(
                                    STAKED_TOKEN_SIGN, getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                            tokenHash));
                            stakingSigns.put(
                                    MINING_TID_SIGN, getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                            mineDetToSign.getString(MINE_TID)));
                            stakingSigns.put(
                                    MINED_RBT_SIGN, getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                            mineDetToSign.getString(MINED_RBT)));
                            stakingSigns.put(MINE_ID_SIGN, getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                    mineDetToSign.getString(MINE_ID)));

                            out.println(stakingSigns.toString());
                        } else {
                            QuorumConsensusLogger.debug("Incorrect stake details");
                            out.println("445");
                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                        }

                        // ! receive credits equal to credits required to mine token

                        String credits = null;
                        try {
                            credits = in.readLine();
                            // convert mineData to JSONObject
                            System.out.println("credits received " + credits);
                        } catch (SocketException e) {
                            QuorumConsensusLogger.debug("Sender Input Stream Null - Adding Credits Failed");
                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                        }

                        JSONArray creditArray = new JSONArray(credits);

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

                        if ((creditArray.length() != creditsRequired) && (creditsRequired != 0)) {

                            QuorumConsensusLogger.debug("Credits not received");
                            out.println("446");
                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                        } else {
                            QuorumConsensusLogger.debug("Credits received");
                            out.println("200");
                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);

                            // ! store the credits

                        }

                    } else {
                        out.println("444");
                        socket.close();
                        serverSocket.close();
                        executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    }
                }

                // ? staking logic ends here

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

                    if (qstObject.getString(INIT_HASH) == initHash()) {

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
                                    "Credit Hash: "
                                            + calculateHash(creditsArray.getJSONObject(i).toString(), "SHA3-256"));
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
                    } else {
                        out.println("443");
                        socket.close();
                        serverSocket.close();
                        executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    }
                } else
                    QuorumConsensusLogger.debug("Old Credits Mining / Whole RBT Token Transfer");

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
                        initHash = readSenderData.getString(INIT_HASH);

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
                            QuorumConsensusLogger.debug("Sender Authentication Failure - Quorum");
                            out.println("Auth_Failed");
                        }

                    }
                } else {
                    QuorumConsensusLogger.debug("Quorum - " + didHash + " is unable to respond!" + getRecData);
                    out.println(getRecData);
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
