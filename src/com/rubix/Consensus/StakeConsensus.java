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
    private static volatile boolean STAKE_SUCCESS = false;
    public static volatile JSONObject stakeDetails = new JSONObject();
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
                            StakeConsensusLogger.debug("Mined Token Details sent for validation...");

                            try {
                                qResponse[j] = qIn[j].readLine();
                            } catch (SocketException e) {
                                StakeConsensusLogger
                                        .debug("Mined Token Details validation failed. Received null response");
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                            }
                            if (qResponse[j].length() > 3) {

                                StakeConsensusLogger
                                        .debug("Mined Token Details validated. Received staked token details..");
                                Boolean ownerCheck = true;
                                String stakerDID = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid",
                                        quorumPID[j]);
                                JSONArray stakeTokenArray = new JSONArray(qResponse[j]);
                                String stakeTokenHash = stakeTokenArray.getString(0);
                                JSONArray stakeTC = stakeTokenArray.getJSONArray(1);
                                String positionsArray = stakeTokenArray.getString(2);

                                // ! check ownership of stakeTC from Token Receiver logic

                                if (stakeTC.length() > 0 && stakeTokenHash != null && positionsArray != null) {
                                    JSONObject lastObject = stakeTC.getJSONObject(stakeTC.length() - 1);
                                    StakeConsensusLogger.debug("Last Object = " + lastObject);
                                    if (lastObject.has("owner")) {
                                        StakeConsensusLogger.debug("Checking ownership");
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
                                        StakeConsensusLogger.debug("ownerIdentityHash: " + ownerRecalculated);

                                        if (!owner.equals(ownerRecalculated)) {
                                            ownerCheck = false;
                                            StakeConsensusLogger.debug("Ownership Check Failed");
                                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                                        }
                                    }
                                } else {
                                    StakeConsensusLogger.debug("insufficient stake token height details");
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                                }

                                if (ownerCheck && !STAKE_SUCCESS) {
                                    StakeConsensusLogger.debug("Ownership Check Success for: PID" + quorumPID[j]);
                                    STAKE_SUCCESS = true;
                                    qOut[j].println("alpha-stake-token-verified");
                                    StakeConsensusLogger.debug("Waiting for stake signatures");

                                    try {
                                        qResponse[j] = qIn[j].readLine();
                                        StakeConsensusLogger
                                                .debug("Received stake token signatures: " + qResponse[j]);
                                    } catch (SocketException e) {
                                        StakeConsensusLogger
                                                .debug("Mined Token Details validation failed. Received null response");
                                    }

                                    if (!qResponse[j].contains("44")) {

                                        // ! add mine signs to tokenchain
                                        StakeConsensusLogger.debug("Adding mine signatures");
                                        JSONObject mineSigns = new JSONObject(qResponse[j]);

                                        stakeDetails = mineSigns;

                                        String quorumDID = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid",
                                                quorumPID[j]);

                                        // ! validate signatures
                                        StakeConsensusLogger.debug("Validating Signatures");
                                        JSONObject mineIDSign = new JSONObject();
                                        mineIDSign.put("did", stakeDetails.getString(quorumDID));
                                        mineIDSign.put("hash", stakeDetails.getString(MINE_ID));
                                        mineIDSign.put("signature", stakeDetails.getString(MINE_ID_SIGN));
                                        boolean mineSignCheck = Authenticate.verifySignature(mineIDSign.toString());

                                        ArrayList ownersArray = new ArrayList();
                                        ownersArray = IPFSNetwork.dhtOwnerCheck(MINE_ID);

                                        if (ownersArray.contains(STAKED_QUORUM_DID)) {
                                            StakeConsensusLogger.debug("Staking pin check passed: " + stakeDetails
                                                    .getString(STAKED_QUORUM_DID));
                                        } else {
                                            StakeConsensusLogger.debug("Staking pin check failed: " + stakeDetails
                                                    .getString(STAKED_QUORUM_DID));
                                        }

                                        if (mineSignCheck) {
                                            StakeConsensusLogger.debug(
                                                    "%%%%%%%%||||||||||||########--Staking Complete! Sending Credits--###########|||||||||||||%%%%%%%%%%%");

                                            qOut[j].println("staking-completed");
                                        }

                                    }
                                } else {
                                    StakeConsensusLogger.debug("Ownership Check Failed");
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                                }

                            } else if (qResponse[j].equals("444")) {
                                StakeConsensusLogger.debug("Quorum could not verify mined token. Skipping...");
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                            } else if (qResponse[j].equals("445")) {
                                StakeConsensusLogger.debug("Insufficient quorum member balance. Skipping...");
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                            } else if (qResponse[j].equals("446")) {
                                StakeConsensusLogger
                                        .debug("Token files picked by quorum to stake is corroupted. Skipping...");
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                            } else if (qResponse[j].equals("447")) {
                                StakeConsensusLogger.debug("alpha-stake-token-not-verified. Skipping...");
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                            } else {
                                StakeConsensusLogger.debug("Unexpected response from staker: " + qResponse[j]);
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumPID[j]);
                            }

                        }

                    } catch (Exception e) {
                        StakeConsensusLogger.error("Error in quorum consensus thread: " + e);
                    }
                });
                quorumThreads[j].start();
            }
            do {

            } while (!STAKE_SUCCESS);

        } catch (Exception e) {
            StakeConsensusLogger.error("Error in getStakeConsensus: " + e);
        }
    }

}
