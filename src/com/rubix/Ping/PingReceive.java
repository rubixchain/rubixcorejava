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

public class PingReceive {
    public static Logger PingReceiverLogger = Logger.getLogger(PingReceive.class);

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
    public static String receive(String userType,int port) throws JSONException {
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
            PingReceiverLogger.debug("Ping "+userType+" Listening on " + port + " appname " + appName);

            sk = ss.accept();
            PingReceiverLogger.debug("Data Incoming...");
            BufferedReader input = new BufferedReader(new InputStreamReader(sk.getInputStream()));
            PrintStream output = new PrintStream(sk.getOutputStream());

            int height = 0;
            String pingRequest;
            try {
                pingRequest = input.readLine();
            } catch (SocketException e) {
            	PingReceiverLogger.warn("Sender Stream Null - PingCheck");
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
            PingReceiverLogger.debug("Ping Request Received: " + pingRequest);
            if (pingRequest != null) {
            	if(pingRequest.contains("PingCheck")) {
            		PingReceiverLogger.debug(userType + " Sending version "+currentVersion);
                    output.println(currentVersion);
                    PingReceiverLogger.debug(userType + " Sent version to pingSender"+currentVersion);
                    APIResponse.put("status", "Success");
                    APIResponse.put("message", "Pong Sent to Sender with Check Sum");
                    PingReceiverLogger.info(userType + " Pong Sent "+currentVersion);

                }
            		else
            	if(pingRequest.contains("Get-TokenChain-Height")) {
            			String tokenHash;
                        try {
                            tokenHash = input.readLine();
                        } catch (SocketException e) {
                        	PingReceiverLogger.warn("Sender Stream Null - tokenHash");
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
                        	PingReceiverLogger.info("Token chain height requested for: " + tokenHash);
                            File tokenChainFile = new File(TOKENCHAIN_PATH.concat(tokenHash).concat(".json"));
                            if(!tokenChainFile.exists()) {
                            	PingReceiverLogger.info("Token chain file not found");
                                height = 0;
                            }
                            else{
                                String tokenChain = readFile(TOKENCHAIN_PATH.concat(tokenHash).concat(".json"));
                                JSONArray chainArray = new JSONArray(tokenChain);
                                height = chainArray.length()-1;
                                PingReceiverLogger.info("Chain height: " + height);
                            }
                        }
            			
            		}
                else{
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Request Failed");
                    PingReceiverLogger.info(userType+ " Request Failed");
                }

                APIResponse.put("status", "Success");
                APIResponse.put("message", "Pong Sent");
                PingReceiverLogger.info(userType + " Pong Sent");

            }
            else {
            	PingReceiverLogger.info("Pong Not Sent ");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Pong Failed");
                PingReceiverLogger.info(userType + " Pong Failed");
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
