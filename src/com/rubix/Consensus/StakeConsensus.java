package com.rubix.Consensus;

import static com.rubix.Constants.MiningConstants.MINE_ID;
import static com.rubix.Constants.MiningConstants.MINE_ID_SIGN;
import static com.rubix.Constants.MiningConstants.STAKED_QUORUM_DID;
import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.calculateHash;
import static com.rubix.Resources.Functions.getValues;
import static com.rubix.Resources.Functions.nodeData;
import static com.rubix.Resources.Functions.syncDataTable;
import static com.rubix.Resources.IPFSNetwork.forward;
import static com.rubix.Resources.IPFSNetwork.swarmConnectP2P;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Resources.IPFSNetwork;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class StakeConsensus {
    public static Logger StakeConsensusLogger = Logger.getLogger(StakeConsensus.class);
    private static int socketTimeOut = 1800000;
    public static volatile int STAKE_LOCKED = 0;
    public static volatile int STAKE_SUCCESS = 0;
    public static volatile int STAKE_FAILED = 0;
    public static volatile JSONArray stakeDetails = new JSONArray();
    // MINE_ID
    // QST_HEIGHT
    // STAKED_QUORUM_DID
    // STAKED_TOKEN
    // STAKED_TOKEN_SIGN
    // MINE_ID_SIGN
    // MINING_TID_SIGN
    // MINED_RBT_SIGN
    // same object will be added to tokenchains of staked and mined token

    public static void getStakeConsensus(JSONArray signedAphaQuorumArray, JSONObject data, IPFS ipfs, int PORT,
            String operation) {

        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String[] qResponse = new String[signedAphaQuorumArray.length()];
        Socket[] qSocket = new Socket[signedAphaQuorumArray.length()];
        PrintStream[] qOut = new PrintStream[signedAphaQuorumArray.length()];
        BufferedReader[] qIn = new BufferedReader[signedAphaQuorumArray.length()];
        String[] quorumPID = new String[signedAphaQuorumArray.length()];

        StakeConsensusLogger.debug("Initiating Staking with " + signedAphaQuorumArray.length() + " Alpha Quorums: "
                + signedAphaQuorumArray);

        try {

            for (int j = 0; j < signedAphaQuorumArray.length(); j++)
                quorumPID[j] = signedAphaQuorumArray.getString(j);

            Thread[] quorumThreads = new Thread[signedAphaQuorumArray.length()];
            for (int i = 0; i < signedAphaQuorumArray.length(); i++) {
                int j = i;
                quorumThreads[i] = new Thread(() -> {
                    try {
                        StakeConsensusLogger.debug("Connecting to Quorum: " + quorumPID[j]);

                        swarmConnectP2P(quorumPID[j], ipfs);
                        syncDataTable(null, quorumPID[j]);
                        String quorumDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid",
                                quorumPID[j]);
                        String quorumWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid",
                                quorumPID[j]);
                        nodeData(quorumDidIpfsHash, quorumWidIpfsHash, ipfs);
                        String appName = quorumPID[j].concat("alpha");
                        StakeConsensusLogger.debug("quourm ID " + quorumPID[j] + " appname " + appName);
                        forward(appName, PORT + j, quorumPID[j]);
                        StakeConsensusLogger.debug(
                                "Connected to " + quorumPID[j] + " on port " + (PORT + j) + "with AppName" + appName);
                        qSocket[j] = new Socket("127.0.0.1", PORT + j);
                        qSocket[j].setSoTimeout(socketTimeOut);
                        qIn[j] = new BufferedReader(new InputStreamReader(qSocket[j].getInputStream()));
                        qOut[j] = new PrintStream(qSocket[j].getOutputStream());

                        qOut[j].println(operation);
                        if (operation.equals("alpha-stake-token")) {

                            qOut[j].println(data.toString());

                            String stakerDID = getValues(DATA_PATH + "DataTable.json", "didHash",
                                    "peerid",
                                    quorumPID[j]);

                            StakeConsensusLogger.debug("Mined Token Details sent for validation...DID: " + stakerDID);

                            try {
                                qResponse[j] = qIn[j].readLine();

                                if (qResponse[j] != null && qResponse[j].equals("tokenToStake")) {

                                    try {
                                        qResponse[j] = qIn[j].readLine();

                                        if (qResponse[j] != null) {
                                            /****** start */

                                            StakeConsensusLogger
                                                    .debug("Mined Token Details validated. Received staked token details from DID: "
                                                            + stakerDID);
                                            Boolean ownerCheck = true;

                                            JSONArray stakeTokenArray = new JSONArray(qResponse[j]);
                                            String stakeTokenHash = stakeTokenArray.getString(0);
                                            JSONArray stakeTC = stakeTokenArray.getJSONArray(1);
                                            String positionsArray = stakeTokenArray.getString(2);

                                            // ! check ownership of stakeTC from Token Receiver logic

                                            if (stakeTC.length() > 0 && stakeTokenHash != null
                                                    && positionsArray != null) {

                                                JSONObject lastObject = stakeTC.getJSONObject(stakeTC.length() - 1);
                                                StakeConsensusLogger.debug("Last Object = " + lastObject);
                                                if (lastObject.has("owner")) {
                                                    StakeConsensusLogger
                                                            .debug("Checking ownership of " + stakeTokenHash
                                                                    + " from DID "
                                                                    + lastObject.getString("owner")
                                                                    + " for positions send: "
                                                                    + positionsArray);
                                                    String owner = lastObject.getString("owner");
                                                    String tokens = stakeTokenHash;
                                                    String hashString = tokens.concat(stakerDID);
                                                    String hashForPositions = calculateHash(hashString, "SHA3-256");
                                                    String ownerIdentity = hashForPositions.concat(positionsArray);
                                                    String ownerRecalculated = calculateHash(ownerIdentity, "SHA3-256");

                                                    StakeConsensusLogger.debug("Ownership Here Sender Calculation");
                                                    StakeConsensusLogger.debug("tokens: " + tokens);
                                                    StakeConsensusLogger.debug("hashString: " + hashString);
                                                    StakeConsensusLogger.debug("hashForPositions: " + hashForPositions);
                                                    StakeConsensusLogger.debug("p1: " + positionsArray);
                                                    StakeConsensusLogger.debug("ownerIdentity: " + ownerIdentity);
                                                    StakeConsensusLogger
                                                            .debug("ownerIdentityHash: " + ownerRecalculated);

                                                    if (!owner.equals(ownerRecalculated)) {
                                                        ownerCheck = false;
                                                        // STAKE_FAILED++;
                                                        StakeConsensusLogger.debug(
                                                                "Ownership Check Failed for index " + j + " with DID: "
                                                                        + owner);
                                                        IPFSNetwork.executeIPFSCommands(
                                                                "ipfs p2p close -t /p2p/" + quorumPID[j]);
                                                    }

                                                }
                                            } else {
                                            	ownerCheck = false;
                                               // STAKE_FAILED++;
                                                StakeConsensusLogger
                                                        .debug("insufficient stake token height details from DID: "
                                                                + stakerDID);
                                                IPFSNetwork
                                                        .executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                                            }

                                            if (ownerCheck && !(STAKE_LOCKED > 2)) {
                                                StakeConsensusLogger
                                                        .debug("Ownership Check Success for Peer: " + quorumPID[j]);
                                                STAKE_LOCKED++;
                                                qOut[j].println("alpha-stake-token-verified");
                                                StakeConsensusLogger
                                                        .debug("Staking locked with for Peer: " + quorumPID[j]);
                                                StakeConsensusLogger.debug("Waiting for stake signatures");

                                                try {
                                                    qResponse[j] = qIn[j].readLine();
                                                    StakeConsensusLogger
                                                            .debug("Received stake token signatures: " + qResponse[j]);

                                                    if (qResponse[j] != null && qResponse[j].equals("447")) {
                                                        STAKE_FAILED++;
                                                        StakeConsensusLogger.debug("Token to stake not verified");
                                                        IPFSNetwork.executeIPFSCommands(
                                                                "ipfs p2p close -t /p2p/" + quorumPID[j]);
                                                    } else if (qResponse[j] != null) {
                                                        // ! add mine signs to tokenchain
                                                        StakeConsensusLogger.debug("Adding mine signatures");
                                                        JSONObject mineSigns = new JSONObject(qResponse[j]);

                                                        if (mineSigns.length() > 0) {

                                                            // stakeDetails = mineSigns;

                                                            String quorumDID = getValues(DATA_PATH + "DataTable.json",
                                                                    "didHash", "peerid",
                                                                    quorumPID[j]);

                                                            // ! validate signatures
                                                            StakeConsensusLogger
                                                                    .debug("Validating Signatures from DID: "
                                                                            + stakerDID);
                                                            JSONObject mineIDSign = new JSONObject();
                                                            mineIDSign.put("did", quorumDID);
                                                            mineIDSign.put("hash", mineSigns.getString(MINE_ID));
                                                            mineIDSign.put("signature",
                                                                    mineSigns.getString(MINE_ID_SIGN));

                                                            ArrayList ownersArray = new ArrayList();
                                                            ownersArray = IPFSNetwork.dhtOwnerCheck(MINE_ID);

                                                            if (ownersArray.contains(STAKED_QUORUM_DID)) {
                                                                StakeConsensusLogger
                                                                        .debug("Staking pin check passed: " + mineSigns
                                                                                .getString(STAKED_QUORUM_DID));
                                                            } else {
                                                                IPFSNetwork.executeIPFSCommands(
                                                                        "ipfs p2p close -t /p2p/" + quorumPID[j]);
                                                                StakeConsensusLogger.debug(
                                                                        "Staking pin check failed for DID: " + mineSigns
                                                                                .getString(STAKED_QUORUM_DID));
                                                            }

                                                            boolean mineSignCheck = Authenticate
                                                                    .verifySignature(mineIDSign.toString());

                                                            if (mineSignCheck) {
                                                                StakeConsensusLogger.debug(
                                                                        "########--Staking Complete " + STAKE_SUCCESS
                                                                                + "/5 !--########");

                                                                // qOut[j].println("200");
                                                                StakeConsensusLogger.debug(
                                                                        "Staking completed for Peer: " + quorumPID[j]);
                                                                stakeDetails.put(mineSigns);
                                                                STAKE_SUCCESS++;
                                                            }else {
                                                            	STAKE_FAILED++;
                                                            }
                                                        } else {
                                                            STAKE_FAILED++;
                                                            StakeConsensusLogger.debug(
                                                                    "Stake signatures not received from DID: "
                                                                            + stakerDID + " Quorum response: "
                                                                            + qResponse[j]);
                                                            IPFSNetwork.executeIPFSCommands(
                                                                    "ipfs p2p close -t /p2p/" + quorumPID[j]);
                                                        }
                                                    }

                                                } catch (SocketException e) {
                                                    STAKE_FAILED++;
                                                    StakeConsensusLogger
                                                            .debug("Mined Token Details validation failed for DID: "
                                                                    + stakerDID + " Received null response");
                                                }

                                            } else {
                                                STAKE_FAILED++;
                                                StakeConsensusLogger.debug("Ownership Check Failed");
                                                IPFSNetwork
                                                        .executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                                            }

                                            /** End */
                                        }

                                    } catch (SocketException e) {
                                        STAKE_FAILED++;
                                        StakeConsensusLogger
                                                .debug("Mined Token Details validation failed. Received null response");
                                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                                    }

                                } else if (qResponse[j] != null && qResponse[j].equals("446")) {
                                    STAKE_FAILED++;
                                    StakeConsensusLogger
                                            .debug("No token is available to stake is corroupted. Skipping...DID: "
                                                    + stakerDID);
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                                } else if (qResponse[j].equals("445")) {
                                    STAKE_FAILED++;
                                    StakeConsensusLogger.debug("Insufficient quorum member balance. Skipping...DID: "
                                            + stakerDID);
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                                } else if (qResponse[j].equals("444")) {
                                    STAKE_FAILED++;
                                    StakeConsensusLogger.debug("Quorum could not verify mined token. Skipping...DID: "
                                            + stakerDID);
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                                } else {
                                    STAKE_FAILED++;
                                    StakeConsensusLogger.debug("Unexpected response from staker: " + qResponse[j]);
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                                }

                            } catch (SocketException e) {
                                STAKE_FAILED++;
                                StakeConsensusLogger
                                        .debug("Mined Token Details validation failed for DID: " + stakerDID
                                                + " Received null response");
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                            }

                        }

                    } catch (Exception e) {
                        STAKE_FAILED++;
                        StakeConsensusLogger.error("Error in quorum consensus thread: " + e);
                    }
                    StakeConsensusLogger.debug("*******STAKE_SUCCESS********* " + STAKE_SUCCESS
                            + " **********STAKE_FAILED*********** " + STAKE_FAILED);
                });
                quorumThreads[j].start();

                // do {
                // } while (STAKE_SUCCESS < 2 || STAKE_FAILED < 3);

            }

            do {
            } while (STAKE_SUCCESS < 3 && STAKE_FAILED < 3);

        } catch (Exception e) {
            StakeConsensusLogger.error("Error in getStakeConsensus: " + e);
        }

    }

}
