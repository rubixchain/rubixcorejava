package com.rubix.Ping;

import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketException;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;

public class GetCredits {
    private static final Logger PingSenderLogger = Logger.getLogger(GetCredits.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);

    public static JSONObject Contact(String did, int port) throws IOException, JSONException {
        repo(ipfs);
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        String peerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", did);

        JSONObject APIResponse = new JSONObject();

        String appName = peerID.concat("Ping");
        forward(appName, port, peerID);
        PingSenderLogger.debug("Forwarded to " + appName + " on " + port + " for collecting credits");
        Socket senderSocket = new Socket("127.0.0.1", port);

        BufferedReader input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
        PrintStream output = new PrintStream(senderSocket.getOutputStream());

        output.println("Get-Credits");
//        PingSenderLogger.debug("Sent Credits request");

        String pongResponse;
        try {
            pongResponse = input.readLine();
        } catch (SocketException e) {
            PingSenderLogger.warn("Quorum " + did + " is unable to Respond! - Credits Ping");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + peerID);
            output.close();
            input.close();
            senderSocket.close();
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Quorum " + did + "is unable to respond! - Credits Ping");

            return APIResponse;
        }


        if (pongResponse == null) {
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + peerID);
//            PingSenderLogger.info("Credits response not received");
            output.close();
            input.close();
            senderSocket.close();
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Credits response not received");

        }else {
            PingSenderLogger.info("Credits received from " + did);
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + peerID);
            output.close();
            input.close();
            senderSocket.close();
            APIResponse.put("status", "Success");
            APIResponse.put("message", Integer.parseInt(pongResponse));

        }
        return APIResponse;
    }
}
