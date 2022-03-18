package com.rubix.Consensus;

import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.LOGGER_PATH;
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

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class StakeConsensus {
    public static Logger StakeConsensusLogger = Logger.getLogger(StakeConsensus.class);
    private static int socketTimeOut = 120000;
    public static volatile JSONObject stakeDetails = new JSONObject();
    // mine ID
    // QST_Height
    // staker DID
    // staked token hash
    // sign on staking token
    // sign on txn id
    // sign on mining token
    // same object will be added to tokenchains of staked and mined token

    public static void getStakeConsensus(JSONArray signedAphaQuorumArray, JSONObject data, IPFS ipfs, int PORT,
            String operation) {

        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String[] qResponse = new String[signedAphaQuorumArray.length()];
        Socket[] qSocket = new Socket[signedAphaQuorumArray.length()];
        PrintStream[] qOut = new PrintStream[signedAphaQuorumArray.length()];
        BufferedReader[] qIn = new BufferedReader[signedAphaQuorumArray.length()];
        String[] quorumID = new String[signedAphaQuorumArray.length()];

        try {

            for (int j = 0; j < signedAphaQuorumArray.length(); j++)
                quorumID[j] = signedAphaQuorumArray.getString(j);

            Thread[] quorumThreads = new Thread[signedAphaQuorumArray.length()];
            for (int i = 0; i < signedAphaQuorumArray.length(); i++) {
                int j = i;
                quorumThreads[i] = new Thread(() -> {
                    try {
                        swarmConnectP2P(quorumID[j], ipfs);
                        syncDataTable(null, quorumID[j]);
                        String quorumDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid",
                                quorumID[j]);
                        String quorumWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid",
                                quorumID[j]);
                        nodeData(quorumDidIpfsHash, quorumWidIpfsHash, ipfs);
                        String appName = quorumID[j].concat("alpha");
                        StakeConsensusLogger.debug("quourm ID " + quorumID[j] + " appname " + appName);
                        forward(appName, PORT + j, quorumID[j]);
                        StakeConsensusLogger.debug(
                                "Connected to " + quorumID[j] + "on port " + (PORT + j) + "with AppName" + appName);

                        qSocket[j] = new Socket("127.0.0.1", PORT + j);
                        qSocket[j].setSoTimeout(socketTimeOut);
                        qIn[j] = new BufferedReader(new InputStreamReader(qSocket[j].getInputStream()));
                        qOut[j] = new PrintStream(qSocket[j].getOutputStream());

                        qOut[j].println(operation);
                        if (operation.equals("alpha-stake-token")) {

                            qOut[j].println(data.toString());
                            StakeConsensusLogger.debug("Token Details sent for validation...");

                            try {
                                qIn[j].readLine();
                                StakeConsensusLogger.debug("Token Details validated. Received stake token details..");
                            } catch (SocketException e) {

                            }

                        }

                    } catch (Exception e) {
                        StakeConsensusLogger.error("Error in quorum consensus thread: " + e);
                    }
                });
            }
            ;

        } catch (Exception e) {
            StakeConsensusLogger.error("Error in getStakeConsensus: " + e);
        }
    }

}
