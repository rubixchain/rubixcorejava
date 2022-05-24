package com.rubix.NFT;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.rubix.NFTResources.EnableNft.*;
import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;

import io.ipfs.api.IPFS;

public class BurnSender {

    private static final Logger burnSender = Logger.getLogger(NftBuyer.class);
    private static final String USER_AGENT = "Mozilla/5.0";
    public static BufferedReader serverInput;
    private static PrintStream output;
    private static BufferedReader input;
    private static Socket burnSenderSocket;
    private static boolean burnSenderMutex = false;
    public static String burnNodePeerID;

    public static void send(String data, IPFS ipfs, int port) {

        nftPathSet();
        JSONArray quorumArray;
        JSONObject APIResponse = new JSONObject();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONArray alphaQuorum = new JSONArray();
        JSONArray betaQuorum = new JSONArray();
        JSONArray gammaQuorum = new JSONArray();

        try {
            JSONObject detailsObject = new JSONObject(data);

            String burnSenderDid= detailsObject.getString("DID");

            String burnDid= detailsObject.getString("burnDid");
            String burnPeerId=getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", burnDid);

            boolean sanityCheck = sanityCheck("BurnNode", burnPeerId, ipfs, port + 10);
            if (!sanityCheck) {
                APIResponse.put("did", burnSenderDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", sanityMessage);
                burnSender.warn(sanityMessage);

                return APIResponse;
            }

            if (burnSenderMutex) {
                APIResponse.put("did", burnSenderDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender busy. Try again later");
                burnSender.warn("Sender busy");

                return APIResponse;
            }

            burnSender.debug("Swarm connecting to " + burnPeerId);
            swarmConnectP2P(burnPeerId, ipfs);
            burnSender.debug("Swarm connected");
            String sellerWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash",
                    "didHash", burnDid);
            nodeData(burnDid, sellerWidIpfsHash, ipfs);
            forward(burnPeerId + "NFT", port, burnPeerId);
            burnSender.debug("Forwarded to " + burnPeerId + " on " + port);
            burnSenderSocket = new Socket("127.0.0.1", port);
            input = new BufferedReader(new InputStreamReader(burnSenderSocket.getInputStream()));
            output = new PrintStream(burnSenderSocket.getOutputStream());

            
        } catch (Exception e) {
            //TODO: handle exception
        }
    }
    
}
