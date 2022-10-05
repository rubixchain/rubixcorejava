package com.rubix.BulkStaking;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import static com.rubix.Resources.Functions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketException;
import org.apache.log4j.PropertyConfigurator;

import io.ipfs.api.IPFS;

public class BulkStakeConsensus {

    public static Logger BulkStakeConsensusLogger = Logger.getLogger(BulkStakeConsensus.class);

        public static JSONArray alphaReply, betaReply, gammaReply;

    
    public static JSONArray start(String data, IPFS ipfs, int PORT, int index, String role, JSONArray quorumPeersObject, int alphaSize, int quorumSize, String operation) throws JSONException {
        
        String[] qResponse = new String[QUORUM_COUNT];
        Socket[] qSocket = new Socket[QUORUM_COUNT];
        PrintStream[] qOut = new PrintStream[QUORUM_COUNT];
        BufferedReader[] qIn = new BufferedReader[QUORUM_COUNT];
        String[] quorumID = new String[QUORUM_COUNT];
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        JSONObject dataObject = new JSONObject(data);
        String hash = dataObject.getString("hash");
        JSONArray details = dataObject.getJSONArray("details");

        int[] quorumResponse;
        quorumResponse[index] = 0;
        BulkStakeConsensusLogger.debug("quorum peer role " + role + " length " + quorumPeersObject.length());
        JSONArray tokenDetails;
        try {
            tokenDetails = new JSONArray(details.toString());
            JSONObject detailsToken = tokenDetails.getJSONObject(0);
            JSONObject sharesToken = tokenDetails.getJSONObject(1);
        }catch
    }

}
