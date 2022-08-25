package com.rubix.Datum;

import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.IPFS_PORT;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.getValues;
import static com.rubix.Resources.Functions.initHash;
import static com.rubix.Resources.Functions.nodeData;
import static com.rubix.Resources.IPFSNetwork.executeIPFSCommands;
import static com.rubix.Resources.IPFSNetwork.forward;
import static com.rubix.Resources.IPFSNetwork.repo;
import static com.rubix.Resources.IPFSNetwork.swarmConnectP2P;

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

import com.rubix.Ping.PingCheck;

import io.ipfs.api.IPFS;

public class DataTokenPin {
	
	 private static final Logger DataTokenPinLogger = Logger.getLogger(DataTokenPin.class);
	    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
	    public static BufferedReader serverInput;

	    private static int socketTimeOut = 120000;
	    public static String currentVersion = initHash();
	
	public static JSONObject Ping(String peerID, int port, String cid) throws IOException, JSONException {
        repo(ipfs);
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        port = port +1;
        DataTokenPinLogger.debug("Incoming PID is "+peerID);

        JSONObject APIResponse = new JSONObject();
        
        String receiverWidIpfsHash = Dependency.getWIDfromPID(peerID, Dependency.widDataTableHashMap());
        		//getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid", peerID);
        DataTokenPinLogger.debug("receiverWidIpfsHash "+receiverWidIpfsHash+" is of pid "+peerID);

        		//
        
        String receiverDidIpfsHash = Dependency.getDIDfromPID(peerID, Dependency.dataTableHashMap());
        		//getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID);
        
        
        if (!receiverWidIpfsHash.equals("")) {
            nodeData(receiverDidIpfsHash, receiverWidIpfsHash, ipfs);
        } else {
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Receiver WID null");
            DataTokenPinLogger.warn("Receiver WID null");
            return APIResponse;
        }

        DataTokenPinLogger.debug("Sender IPFS forwarding to DID: " + receiverDidIpfsHash + " PeerID: " + peerID);
        String appName = peerID.concat("Ping");
        forward(appName, port, peerID);
        DataTokenPinLogger.debug("Forwarded to " + appName + " on " + port);
        Socket senderSocket = new Socket("127.0.0.1", port);
        senderSocket.setSoTimeout(socketTimeOut);
        BufferedReader input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
        PrintStream output = new PrintStream(senderSocket.getOutputStream());

        output.println("CID-to-pin");
        DataTokenPinLogger.debug("CID-to-pin request");

        String pongResponse = cid;
		/*
		 * try { pongResponse = input.readLine();
		 * DataTokenPinLogger.debug("Pong received is "+pongResponse); } catch
		 * (SocketException e) { DataTokenPinLogger.warn("Receiver " +
		 * receiverDidIpfsHash + " is unable to Respond! - Ping Check");
		 * executeIPFSCommands(" ipfs p2p close -t /p2p/" + peerID); output.close();
		 * input.close(); senderSocket.close(); APIResponse.put("status", "Failed");
		 * APIResponse.put("message", "Receiver " + receiverDidIpfsHash +
		 * "is unable to respond! - Sender Auth");
		 * 
		 * return APIResponse; }
		 */
        DataTokenPinLogger.debug("Sending CID to "+appName);
        if (pongResponse != null) {
        	output.println(cid);
            
        } else {
        	executeIPFSCommands(" ipfs p2p close -t /p2p/" + peerID);
        	DataTokenPinLogger.info("Pong response not received");
        	DataTokenPinLogger.info("Pong response now received with error is "+pongResponse);
            output.close();
            input.close();
            senderSocket.close();
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "cid response not received");

        }
        return APIResponse;
    }

}
