package com.rubix.TokenTransfer;


import com.rubix.Consensus.InitiatorConsensus;
import com.rubix.Consensus.InitiatorProcedure;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import static com.rubix.Resources.Functions.*;


public class ProofCredits {


    public static Logger ProofCreditsLogger = Logger.getLogger(ProofCredits.class);
    private static ArrayList quorumPeersList;

    public static org.json.JSONObject create(String data, IPFS ipfs) throws IOException, JSONException {

        org.json.JSONObject APIResponse = new org.json.JSONObject();
        org.json.JSONObject detailsObject = new org.json.JSONObject(data);
        String receiverDidIpfsHash = detailsObject.getString("receiverDidIpfsHash");
        String pvt = detailsObject.getString("pvt");
        // getInfo api call to fetch current token, current level and required proof credits for level

        int level = 0,tokenNumber = 0,reqProofCredits = 0, balance;

        String GET_URL = SYNC_IP+"/getInfo";
        URL URLobj = new URL(GET_URL);
        HttpURLConnection con = (HttpURLConnection) URLobj.openConnection();
        con.setRequestMethod("GET");
        int responseCode = con.getResponseCode();
        System.out.println("GET Response Code :: " + responseCode);
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            ProofCreditsLogger.debug("response from service "+response.toString());

            JSONObject resJsonData = new JSONObject(response.toString());
            level = resJsonData.getInt("current_level");
            tokenNumber = resJsonData.getInt("token_num");
            reqProofCredits = resJsonData.getInt("required_credits");
            ProofCreditsLogger.debug("level "+level+" tokenNumber "+tokenNumber+" required proof credits "+reqProofCredits);

        } else
            ProofCreditsLogger.debug("GET request not worked");



        //Reading proofcredits.json
        String jsonFilePath = WALLET_DATA_PATH+"QuorumSignedTransactions.json";
        JSONArray records = new JSONArray(readFile(jsonFilePath));
        balance = records.length();
        JSONArray prooftid = new JSONArray();
        for (int i = 0; i < reqProofCredits; i++) {
            JSONObject temp = records.getJSONObject(i);
            records.remove(i);
            prooftid.put(temp.getString("tid"));
        }

        balance -= prooftid.length();

        ProofCreditsLogger.debug("Current balance of node : " + balance);

        //Check if node can mine token
        if (balance >= reqProofCredits) {
            //Calling Mine token function

            String token = Functions.mineToken(level, tokenNumber);
            String comments = level+String.valueOf(tokenNumber)+prooftid;


            String authSenderByRecHash = calculateHash(token + receiverDidIpfsHash + comments, "SHA3-256");
            String tid = calculateHash(authSenderByRecHash, "SHA3-256");
            JSONArray quorumArray = new JSONArray(readFile(DATA_PATH + "quorumlist.json"));

            quorumPeersList = QuorumCheck(quorumArray, ipfs);


            JSONObject dataObject = new JSONObject();
            dataObject.put("tid", tid);
            dataObject.put("message", comments);
            dataObject.put("receiverDidIpfs", receiverDidIpfsHash);
            dataObject.put("pvt", pvt);
            dataObject.put("senderDidIpfs", receiverDidIpfsHash);
            dataObject.put("token", token);
            dataObject.put("alphaList", quorumPeersList.subList(0, 7));
            dataObject.put("betaList", quorumPeersList.subList(7, 14));
            dataObject.put("gammaList", quorumPeersList.subList(14, 21));

            InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 3);

            if (!(InitiatorConsensus.quorumSignature.length() >= 3 * minQuorum(7)))
            {
            APIResponse.put("did", receiverDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Consensus failed");
            ProofCreditsLogger.debug("consensus failed");
            }
            else
            {
            ProofCreditsLogger.debug("token mined " + token);
            //call updateTxnAPI
            String GET_URL2 = SYNC_IP + "/updateTxn";
            URL URLobj2 = new URL(GET_URL2);
            HttpURLConnection con2 = (HttpURLConnection) URLobj2.openConnection();
            con2.setRequestMethod("GET");
            int responseCode2 = con2.getResponseCode();
            ProofCreditsLogger.debug("GET Response Code :: " + responseCode2);
            if (responseCode == HttpURLConnection.HTTP_OK)
                ProofCreditsLogger.debug("Token Data Updated");
            else
                ProofCreditsLogger.debug("GET request not worked");

            //update proofcredits.json

            //write token, tokenchain, BNK00

                writeToFile("tempToken",token,false);
                String tokenHash = IPFSNetwork.add("tempToken",ipfs);
                writeToFile(TOKENS_PATH+tokenHash,token,false);
                deleteFile("tempToken");
                writeToFile(TOKENCHAIN_PATH+tokenHash+".json","[]",false);
                JSONObject temp = new JSONObject();
                temp.put("tokenHash",tokenHash);
                JSONArray tempArray = new JSONArray();
                tempArray.put(temp);
                updateJSON("add",PAYMENTS_PATH+"BNK00.json",tempArray.toString());

            FileWriter File = new FileWriter(jsonFilePath);
            File.write(records.toString());
            File.close();

            ProofCreditsLogger.debug("Updated balance of node : " + balance);
        }
            APIResponse.put("did", receiverDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Success");
            APIResponse.put("message", comments);
        }
        else {
            APIResponse.put("did", receiverDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Insufficent proofs");
            ProofCreditsLogger.warn("Insufficient proof credits to mine");
            return APIResponse;
        }
        return APIResponse;
    }
}


