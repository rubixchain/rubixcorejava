package com.rubix.Consensus;

import static com.rubix.Constants.ConsensusConstants.INIT_HASH;
import static com.rubix.Constants.MiningConstants.MINED_RBT;
import static com.rubix.Constants.MiningConstants.MINED_RBT_SIGN;
import static com.rubix.Constants.MiningConstants.MINE_ID;
import static com.rubix.Constants.MiningConstants.MINE_TID;
import static com.rubix.Constants.MiningConstants.MINING_TID_SIGN;
import static com.rubix.Constants.MiningConstants.STAKED_QUORUM_DID;
import static com.rubix.Constants.MiningConstants.STAKED_TOKEN;
import static com.rubix.Constants.MiningConstants.STAKED_TOKEN_SIGN;
import static com.rubix.Constants.MiningConstants.STAKE_DATA;
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
import static com.rubix.Resources.Functions.strToIntArray;
import static com.rubix.Resources.Functions.syncDataTable;
import static com.rubix.Resources.Functions.updateJSON;
import static com.rubix.Resources.Functions.writeToFile;
import static com.rubix.Resources.IPFSNetwork.add;
import static com.rubix.Resources.IPFSNetwork.executeIPFSCommands;
import static com.rubix.Resources.IPFSNetwork.listen;
import static com.rubix.Resources.IPFSNetwork.pin;

import java.awt.image.BufferedImage;
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
import java.util.Random;

import javax.imageio.ImageIO;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.AuthenticateNode.PropImage;
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
                        // ! token hash just mined
                        response = in.readLine();
                    } catch (SocketException e) {
                        QuorumConsensusLogger.debug("Sender Input Stream Null - New Credits Details");
                        socket.close();
                        serverSocket.close();
                        executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    }

                    JSONObject genesisBlock = new JSONObject(response);
                    QuorumConsensusLogger.debug("Validating new token details: " + genesisBlock);
                    System.out.println(genesisBlock);

                    boolean LEVEL_VALID = false;
                    boolean MINE_CREDIT_VALID = false;

                    // ! check token is in same level
                    String TokenContent = genesisBlock.getString("tokenContent");
                    String tokenLevel = TokenContent.substring(0, 3);
                    int tokenLevelInt = Integer.parseInt(tokenLevel);
                    int tokenLevelValue = (int) Math.pow(2, tokenLevelInt + 2);

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
                        // QuorumConsensusLogger.debug("response from service " +
                        // response_credit.toString());
                        JSONObject resJsonData_credit = new JSONObject(response_credit.toString());
                        int level_credit = resJsonData_credit.getInt("level");
                        int creditsRequired = (int) Math.pow(2, (2 + level_credit));

                        if (level_credit == tokenLevelInt) {

                            QuorumConsensusLogger.debug("Validated level of newly minted token");
                            LEVEL_VALID = true;
                        } else {
                            QuorumConsensusLogger.debug("Invalid level of newly minted token");
                            LEVEL_VALID = false;
                        }

                    } else
                        QuorumConsensusLogger.debug("GET request not worked");

                    // ! validate mined token hash and ownership
                    if (genesisBlock.has("quorumSignatures")) {

                        try {
                            int randomNumber = new Random().nextInt(15);
                            JSONObject genesisSignatures = genesisBlock.getJSONObject("quorumSignContent");
                            JSONArray keys = genesisSignatures.names();
                            String signer = keys.getString(randomNumber);
                            String signature = genesisSignatures.getString(signer);

                            JSONObject VerificationPick = new JSONObject();
                            VerificationPick.put("signature", signature);
                            VerificationPick.put("did", signer);
                            VerificationPick.put("hash", genesisSignatures.getString("tid"));

                            if (Authenticate.verifySignature(VerificationPick.toString())) {

                                QuorumConsensusLogger.debug("Validated signature of newly minted token");
                                MINE_CREDIT_VALID = true;
                            } else {
                                QuorumConsensusLogger.debug("Signature not verified");
                                MINE_CREDIT_VALID = false;
                            }
                        } catch (Exception e) {
                            QuorumConsensusLogger
                                    .debug("Mined Token - Quorum Signature Hash not found. Skipping... Exception: "
                                            + e.getMessage());
                            MINE_CREDIT_VALID = false;
                        }

                    }

                    if (LEVEL_VALID && MINE_CREDIT_VALID) {

                        QuorumConsensusLogger.debug("Sending staking token details...");
                        JSONArray tokenToStake = new JSONArray();

                        String bankFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
                        JSONArray bankArray = new JSONArray(bankFile);

                        if (bankArray.length() != 0) {

                            File tokenFile;
                            File tokenchainFile;
                            JSONObject bankObject = new JSONObject();
                            String stakedTokenHash = "";
                            boolean tokenAvailableToStake = false;
                            JSONArray stakedTokenChainArray = new JSONArray();

                            // for loop to check bankArray for token
                            for (int i = 0; i < bankArray.length(); i++) {

                                bankObject = bankArray.getJSONObject(i);
                                stakedTokenHash = bankObject.getString("tokenHash");

                                tokenFile = new File(TOKENS_PATH + stakedTokenHash);
                                tokenchainFile = new File(TOKENCHAIN_PATH + stakedTokenHash + ".json");

                                if (tokenFile.exists() && tokenchainFile.exists()) {

                                    String tokenChain = readFile(TOKENCHAIN_PATH + stakedTokenHash + ".json");
                                    stakedTokenChainArray = new JSONArray(tokenChain);

                                    // get last object of tokenchainarray
                                    JSONObject lastTokenChainObject = stakedTokenChainArray
                                            .getJSONObject(stakedTokenChainArray.length() - 1);

                                    if (!lastTokenChainObject.has(MINE_ID) && !tokenAvailableToStake
                                            && stakedTokenChainArray.length() > tokenLevelValue) {

                                        QuorumConsensusLogger.debug("Staking 1 RBT for incoming mining transaction...");
                                        tokenToStake.put(stakedTokenHash);
                                        tokenToStake.put(stakedTokenChainArray);

                                        bankArray.remove(i);
                                        bankArray.put(bankObject);
                                        writeToFile(PAYMENTS_PATH.concat("BNK00.json"), bankArray.toString(), false);

                                        tokenAvailableToStake = true;

                                        break;
                                    }
                                }
                            }

                            if (tokenAvailableToStake) {

                                QuorumConsensusLogger.debug("Token and TokenChain files found");

                                String hashString = stakedTokenHash.concat(senderDidIpfsHash);
                                String hashForPositions = calculateHash(hashString, "SHA3-256");
                                BufferedImage privateShare = ImageIO
                                        .read(new File(
                                                DATA_PATH.concat(didHash).concat("/PrivateShare.png")));
                                String firstPrivate = PropImage.img2bin(privateShare);
                                int[] privateIntegerArray1 = strToIntArray(firstPrivate);
                                String privateBinary = Functions.intArrayToStr(privateIntegerArray1);
                                String positions = "";
                                for (int j = 0; j < privateIntegerArray1.length; j += 49152) {
                                    positions += privateBinary.charAt(j);
                                }
                                tokenToStake.put(positions);
                                // ! token which will be staked
                                out.println(tokenToStake);

                                try {
                                    response = in.readLine();
                                    QuorumConsensusLogger
                                            .debug("Staking response after verifying staked token: " + response);
                                } catch (SocketException e) {
                                    QuorumConsensusLogger
                                            .debug("Sender Input Stream Null - Stake Token Validation by Miner Failed");
                                    socket.close();
                                    serverSocket.close();
                                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                                }

                                if (response.equals("alpha-stake-token-verified")) {

                                    JSONObject stakingSigns = new JSONObject();
                                    // staker DID
                                    stakingSigns.put(STAKED_QUORUM_DID, didHash);
                                    // staked token and sign from staker
                                    stakingSigns.put(STAKED_TOKEN, stakedTokenHash);
                                    stakingSigns.put(
                                            STAKED_TOKEN_SIGN, getSignFromShares(DATA_PATH + didHash +
                                                    "/PrivateShare.png",
                                                    stakedTokenHash));
                                    // tid and sign from staker
                                    stakingSigns.put(MINE_TID, genesisBlock.getString("tid"));
                                    stakingSigns.put(
                                            MINING_TID_SIGN,
                                            getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                                    genesisBlock.getString("tid")));
                                    // mined token and sign from staker
                                    stakingSigns.put(MINED_RBT, genesisBlock.getString("tokenHash"));
                                    stakingSigns.put(
                                            MINED_RBT_SIGN, getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png",
                                                    genesisBlock.getString("tokenHash")));

                                    genesisBlock.put(STAKE_DATA, stakingSigns);

                                    FileWriter shareWriter = new FileWriter(new File(LOGGER_PATH + "stake.txt"),
                                            true);
                                    shareWriter.write(genesisBlock.toString());
                                    shareWriter.close();
                                    File readStake = new File(LOGGER_PATH + "stake.txt");
                                    String mineID = add(readStake.toString(), ipfs);
                                    pin(mineID, ipfs);

                                    File stakeFile = new File(
                                            WALLET_DATA_PATH.concat("/Stake/").concat(mineID).concat(".json"));
                                    if (!stakeFile.exists())
                                        stakeFile.createNewFile();
                                    writeToFile(stakeFile.toString(), genesisBlock.toString(), false);

                                    deleteFile(LOGGER_PATH + "stake.txt");

                                    // mine ID
                                    stakingSigns.put(MINE_ID, mineID);
                                    stakingSigns.put("sender", genesisBlock.getString("sender"));

                                    QuorumConsensusLogger.debug("Token Staked Successfully. MINE ID: " +
                                            mineID);

                                    out.println(stakingSigns.toString());
                                    stakedTokenChainArray.put(stakingSigns);
                                    writeToFile(TOKENCHAIN_PATH + stakedTokenHash + ".json",
                                            stakedTokenChainArray.toString(),
                                            false);

                                    QuorumConsensusLogger.debug("Staking Completed!");
                                    out.println("200");
                                    socket.close();
                                    serverSocket.close();
                                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);

                                } else {
                                    QuorumConsensusLogger.debug("Token to stake not verified");
                                    out.println("447");
                                    socket.close();
                                    serverSocket.close();
                                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                                }
                            } else {
                                QuorumConsensusLogger.debug("Token to stake not found");
                                out.println("446");
                                socket.close();
                                serverSocket.close();
                                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                            }

                        } else {
                            QuorumConsensusLogger.debug("Token Staking Failed: Insufficient Balance to Stake!");
                            out.println("445");
                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                        }

                    } else {
                        QuorumConsensusLogger.debug("Incorrect stake details");
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

                    if (qstObject.getString(INIT_HASH).equals(initHash())) {

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
                            // QuorumConsensusLogger.debug("response from service " +
                            // response_credit.toString());
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
                        // if qstArray has any duplicate object
                        for (int i = 0; i < qstArray.length(); i++) {
                            for (int j = i + 1; j < qstArray.length(); j++) {
                                if (qstArray.getJSONObject(i).getString("credits")
                                        .equals(qstArray.getJSONObject(j).getString("credits"))) {
                                    flag = false;
                                    break;
                                }
                            }
                        }
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
                        // initHash = readSenderData.getString(INIT_HASH);

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
