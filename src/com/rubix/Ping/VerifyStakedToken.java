package com.rubix.Ping;

import static com.rubix.Resources.Functions.IPFS_PORT;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.SYNC_IP;
import static com.rubix.Resources.IPFSNetwork.executeIPFSCommands;
import static com.rubix.Resources.IPFSNetwork.forward;
import static com.rubix.Resources.IPFSNetwork.repo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONException;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class VerifyStakedToken {
    private static final Logger PingSenderLogger = Logger.getLogger(VerifyStakedToken.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);

    public static boolean Contact(String pid, int port, String tokenHash, String tokenContent)
            throws IOException, JSONException {
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
        output.println(tokenHash);
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

        } else {
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

        String tokenLevel = tokenContent.substring(0, 3);
        int tokenLevelInt = Integer.parseInt(tokenLevel);
        int tokenLevelValue = (int) Math.pow(2, tokenLevelInt + 2);
        int requiredMinedTokenHeight = tokenLevelValue * 4;

        String GET_URL_credit = SYNC_IP + "/getCurrentLevel";
        URL URLobj_credit = new URL(GET_URL_credit);
        HttpURLConnection con_credit = (HttpURLConnection) URLobj_credit.openConnection();
        con_credit.setRequestMethod("GET");
        int responseCode_credit = con_credit.getResponseCode();
        System.out.println("GET Response Code :: " + responseCode_credit);
        if (responseCode_credit == HttpURLConnection.HTTP_OK) {
            BufferedReader in_credit = new BufferedReader(
                    new InputStreamReader(con_credit.getInputStream()));
            String inputLine_credit;
            StringBuffer response_credit = new StringBuffer();
            while ((inputLine_credit = in_credit.readLine()) != null) {
                response_credit.append(inputLine_credit);
            }
            in_credit.close();
            // QuorumConsensusLogger.debug("response from service " +
            // response_credit.toString());
            JSONObject resJsonData_credit = new JSONObject(response_credit.toString());
            int level_credit = resJsonData_credit.getInt("level");

            // ! release staked token if the mined token is from previous level(s)
            if (level_credit > tokenLevelInt) {
                return true;
            }

        } else
            PingSenderLogger.debug("GET request not worked");

        return height > requiredMinedTokenHeight;
    }

}