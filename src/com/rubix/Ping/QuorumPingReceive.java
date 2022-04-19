package com.rubix.Ping;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.executeIPFSCommands;
import static com.rubix.Resources.IPFSNetwork.listen;
import static com.rubix.Resources.IPFSNetwork.repo;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class QuorumPingReceive {
    public static Logger QuorumPingReceiverLogger = Logger.getLogger(QuorumPingReceive.class);

    private static final JSONObject APIResponse = new JSONObject();
    private static final IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    public static String currentVersion = initHash();

    /**
     * Receiver Node: To receive a valid token from an authentic sender
     *
     * @return Transaction Details (JSONObject)
     * @throws IOException   handles IO Exceptions
     * @throws JSONException handles JSON Exceptions
     */
    public static String receive(int port) throws JSONException {
        pathSet();
        ServerSocket ss;
        Socket sk;

        try {
            repo(ipfs);

            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

            String receiverPeerID = getPeerID(DATA_PATH + "DID.json");
            String appName = receiverPeerID.concat("Ping");
            listen(appName, port);
            ss = new ServerSocket(port);
            QuorumPingReceiverLogger.debug("Ping Quorum Listening on " + port + " appname " + appName);

            sk = ss.accept();
            QuorumPingReceiverLogger.debug("Data Incoming...");
            BufferedReader input = new BufferedReader(new InputStreamReader(sk.getInputStream()));
            PrintStream output = new PrintStream(sk.getOutputStream());

            int height = 0;
            String pingRequest;
            try {
                pingRequest = input.readLine();
            } catch (SocketException e) {
                QuorumPingReceiverLogger.warn("Sender Stream Null - PingCheck");
                APIResponse.put("did", "");
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender Stream Null - PingCheck");

                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();

            }
            QuorumPingReceiverLogger.debug("Ping Request Received: " + pingRequest);
            if (pingRequest != null && pingRequest.contains("PingCheck")) {
                output.println("Pong "+currentVersion);
                APIResponse.put("status", "Success");
                APIResponse.put("message", "Pong Sent");
                QuorumPingReceiverLogger.info("Pong Sent");

            }
            else if (pingRequest != null && pingRequest.contains("Get-TokenChain-Height")) {
                String tokenHash;
                try {
                    tokenHash = input.readLine();
                } catch (SocketException e) {
                    QuorumPingReceiverLogger.warn("Sender Stream Null - tokenHash");
                    APIResponse.put("did", "");
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Sender Stream Null - tokenHash");

                    output.close();
                    input.close();
                    sk.close();
                    ss.close();
                    return APIResponse.toString();

                }
                if (tokenHash != null && tokenHash.startsWith("Qm") && tokenHash.length() == 46) {
                    QuorumPingReceiverLogger.info("Token chain height requested for: " + tokenHash);
                    File tokenChainFile = new File(TOKENCHAIN_PATH.concat(tokenHash).concat(".json"));
                    if(!tokenChainFile.exists()) {
                        QuorumPingReceiverLogger.info("Token chain file not found");
                        height = 0;
                    }
                    else{
                        String tokenChain = readFile(TOKENCHAIN_PATH.concat(tokenHash).concat(".json"));
                        JSONArray chainArray = new JSONArray(tokenChain);
                        height = chainArray.length()-1;
                        QuorumPingReceiverLogger.info("Chain height: " + height);
                    }
                }
                else{
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Request Failed");
                    QuorumPingReceiverLogger.info("Request Failed");
                }
                output.println(height);

                APIResponse.put("status", "Success");
                APIResponse.put("message", "Pong Sent");
                QuorumPingReceiverLogger.info("Pong Sent");

            }
            else {
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Pong Failed");
                QuorumPingReceiverLogger.info("Pong Failed");
            }
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + pingRequest);
            output.close();
            input.close();
            sk.close();
            ss.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return APIResponse.toString();
    }
}
