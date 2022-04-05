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

public class VerifyStakedToken {
    private static final Logger PingSenderLogger = Logger.getLogger(VerifyStakedToken.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);

    public static boolean Contact(String pid, int port, String token) throws IOException, JSONException {
        repo(ipfs);
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONObject APIResponse = new JSONObject();

        String appName = pid.concat("Ping");
        forward(appName, port, pid);
        PingSenderLogger.debug("Forwarded to " + appName + " on " + port + " for collecting credits");
        Socket senderSocket = new Socket("127.0.0.1", port);

        BufferedReader input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
        PrintStream output = new PrintStream(senderSocket.getOutputStream());

        output.println("Get-TokenChain-Height");
        output.println(token);
        String heightResponse;
        try {
            heightResponse = input.readLine();
        } catch (SocketException e) {
            PingSenderLogger.warn("Quorum " + pid + " is unable to Respond! - Credits Ping");
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + pid);
            output.close();
            input.close();
            senderSocket.close();
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Quorum " + pid + "is unable to respond! - Credits Ping");

            return false;
        }


        int height = 0;
        if (heightResponse == null) {
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + pid);
            PingSenderLogger.info("TokenChain height not received");
            output.close();
            input.close();
            senderSocket.close();
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "TokenChain height not received");

        }else {
            PingSenderLogger.info("TokenChain height received from " + pid);
            PingSenderLogger.info("TokenChain height: " + heightResponse);
            executeIPFSCommands(" ipfs p2p close -t /p2p/" + pid);
            output.close();
            input.close();
            senderSocket.close();
            APIResponse.put("status", "Success");
            APIResponse.put("message", Integer.parseInt(heightResponse));
            height = Integer.parseInt(heightResponse);

        }

        return height >= 46;
    }
}