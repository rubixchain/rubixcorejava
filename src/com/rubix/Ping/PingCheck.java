package com.rubix.Ping;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.executeIPFSCommands;
import static com.rubix.Resources.IPFSNetwork.forward;
import static com.rubix.Resources.IPFSNetwork.repo;
import static com.rubix.Resources.IPFSNetwork.swarmConnectP2P;

import io.ipfs.api.IPFS;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONException;
import org.json.JSONObject;



public class PingCheck {
    private static final Logger PingSenderLogger = Logger.getLogger(PingCheck.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    public static BufferedReader serverInput;

    private static int socketTimeOut = 120000;
    public static String currentVersion = initHash();

    public static JSONObject Ping(String peerID, int port) throws IOException, JSONException {
        repo(ipfs);
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        PingSenderLogger.debug("Current version in PingSender is "+currentVersion);

        JSONObject APIResponse = new JSONObject();
        if (!peerID.equals("")) {
            PingSenderLogger.debug("Swarm connecting to " + peerID);
            swarmConnectP2P(peerID, ipfs);
            PingSenderLogger.debug("Swarm connected");
        } else {
            APIResponse.put("message", "Receiver Peer ID null");
            PingSenderLogger.warn("Receiver Peer ID null");
            return APIResponse;
        }

        String receiverWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid", peerID);
        String receiverDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID);
        if (!receiverWidIpfsHash.equals("")) {
            nodeData(receiverDidIpfsHash, receiverWidIpfsHash, ipfs);
        } else {
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Receiver WID null");
            PingSenderLogger.warn("Receiver WID null");
            return APIResponse;
        }

        PingSenderLogger.debug("Sender IPFS forwarding to DID: " + receiverDidIpfsHash + " PeerID: " + peerID);
        String appName = peerID.concat("Ping");
        forward(appName, port, peerID);
        PingSenderLogger.debug("Forwarded to " + appName + " on " + port);
        Socket senderSocket = new Socket("127.0.0.1", port);
        senderSocket.setSoTimeout(0);
        BufferedReader input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
        PrintStream output = new PrintStream(senderSocket.getOutputStream());

        output.println("PingCheck");
        PingSenderLogger.debug("Sent PingCheck request");

        String pongResponse;
        try {
            pongResponse = input.readLine();
            PingSenderLogger.debug("Pong received is "+pongResponse);
        } catch (SocketException e) {
            PingSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Ping Check");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + peerID);
            output.close();
            input.close();
            senderSocket.close();
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Receiver " + receiverDidIpfsHash + "is unable to respond! - Sender Auth");

            return APIResponse;
        }
        PingSenderLogger.debug("Pong response after pong received "+pongResponse);
        if (pongResponse != null) {
    		PingSenderLogger.debug("Pong response inside pongRespong not null "+pongResponse);
        	if(pongResponse.contains(currentVersion)) {
        		PingSenderLogger.debug("Pong response received is "+ pongResponse);
           	 	PingSenderLogger.info("Ping Successful");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + peerID);
                output.close();
                input.close();
                senderSocket.close();
                APIResponse.put("status", "Success");
                APIResponse.put("message", "Ping Check Success");
        	}else {
        		PingSenderLogger.debug("Receiver is running "+ pongResponse);
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + peerID);
                output.close();
                input.close();
                senderSocket.close();
                APIResponse.put("status", "Failed");
                APIResponse.put("message", getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", receiverDidIpfsHash)+ " not running new version");
        	}
            
        } else {
        	executeIPFSCommands(" ipfs p2p close -t /p2p/" + peerID);
            PingSenderLogger.info("Pong response not received");
            PingSenderLogger.info("Pong response now received with error is "+pongResponse);
            output.close();
            input.close();
            senderSocket.close();
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Pong response not received");

        }
        return APIResponse;
    }
}
