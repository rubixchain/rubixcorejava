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
import java.util.Iterator;
import java.util.List;
import static com.rubix.Resources.Functions.*;


public class ProofCredits {


    public static Logger ProofCreditsLogger = Logger.getLogger(ProofCredits.class);
    private static ArrayList quorumPeersList;

    public static JSONObject create(String data, IPFS ipfs) throws IOException, JSONException {

        JSONObject APIResponse = new JSONObject();
        JSONObject detailsObject = new JSONObject(data);
        String receiverDidIpfsHash = detailsObject.getString("receiverDidIpfsHash");
        String pvt = detailsObject.getString("pvt");
        int creditUsed=100;

        // getInfo api call to fetch current token, current level and required proof credits for level


//
//        int level = 0,tokenNumber = 0,availableCredits = 0, balance;
//        long starttime = System.currentTimeMillis();
//        String GET_URL = SYNC_IP+"/getInfo";
//        URL URLobj = new URL(GET_URL);
//        HttpURLConnection con = (HttpURLConnection) URLobj.openConnection();
//        con.setRequestMethod("GET");
//        int responseCode = con.getResponseCode();
//        System.out.println("GET Response Code :: " + responseCode);
//        if (responseCode == HttpURLConnection.HTTP_OK) {
//            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
//            String inputLine;
//            StringBuffer response = new StringBuffer();
//            while ((inputLine = in.readLine()) != null) {
//                response.append(inputLine);
//            }
//            in.close();
//            ProofCreditsLogger.debug("response from service "+response.toString());
//
//            JSONObject resJsonData = new JSONObject(response.toString());
//            level = resJsonData.getInt("current_level");
//            tokenNumber = resJsonData.getInt("token_num");
//            availableCredits = resJsonData.getInt("required_credits");
//            ProofCreditsLogger.debug("level "+level+" tokenNumber "+tokenNumber+" required proof credits "+availableCredits);
//
//        } else
//            ProofCreditsLogger.debug("GET request not worked");
//
//
//
//        //Reading proofcredits.json
//        String jsonFilePath = WALLET_DATA_PATH+"QuorumSignedTransactions.json";
//        JSONArray records = new JSONArray(readFile(jsonFilePath));
//        balance = records.length();
//        JSONArray prooftid = new JSONArray();
//        for (int i = 0; i < availableCredits; i++) {
//            JSONObject temp = records.getJSONObject(i);
//            if(temp.getBoolean("minestatus")==false) {
//                prooftid.put(temp.getString("tid"));
//                records.getJSONObject(i).put("minestatus",true);
//            }
//        }
//
//        balance -= prooftid.length();
//
//        ProofCreditsLogger.debug("Current balance of node : " + balance);
//
//        //Check if node can mine token
//        if (balance >= availableCredits) {
//            //Calling Mine token function
//
//            String token = Functions.mineToken(level, tokenNumber);
//
//
//            String comments = level+String.valueOf(tokenNumber)+prooftid;
//
//
//            String authSenderByRecHash = calculateHash(token + receiverDidIpfsHash + comments, "SHA3-256");
//            String tid = calculateHash(authSenderByRecHash, "SHA3-256");
//            JSONArray quorumArray = new JSONArray(readFile(DATA_PATH + "quorumlist.json"));
//
//            quorumPeersList = QuorumCheck(quorumArray, ipfs);
//
//
//            JSONObject dataObject = new JSONObject();
//            dataObject.put("tid", tid);
//            dataObject.put("message", comments);
//            dataObject.put("receiverDidIpfs", receiverDidIpfsHash);
//            dataObject.put("pvt", pvt);
//            dataObject.put("senderDidIpfs", receiverDidIpfsHash);
//            dataObject.put("token", token);
//            dataObject.put("alphaList", quorumPeersList.subList(0, 7));
//            dataObject.put("betaList", quorumPeersList.subList(7, 14));
//            dataObject.put("gammaList", quorumPeersList.subList(14, 21));
//
//            InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 3);
//
//            if (!(InitiatorConsensus.quorumSignature.length() >= 3 * minQuorum(7)))
//            {
//            APIResponse.put("did", receiverDidIpfsHash);
//            APIResponse.put("tid", "null");
//            APIResponse.put("status", "Failed");
//            APIResponse.put("message", "Consensus failed");
//            ProofCreditsLogger.debug("consensus failed");
//            }
//            else
//            {
//            ProofCreditsLogger.debug("token mined " + token);
//            //call updateTxnAPI
//            String GET_URL2 = SYNC_IP + "/updateTxn";
//            URL URLobj2 = new URL(GET_URL2);
//            HttpURLConnection con2 = (HttpURLConnection) URLobj2.openConnection();
//            con2.setRequestMethod("GET");
//            int responseCode2 = con2.getResponseCode();
//            ProofCreditsLogger.debug("GET Response Code :: " + responseCode2);
//            if (responseCode == HttpURLConnection.HTTP_OK)
//                ProofCreditsLogger.debug("Token Data Updated");
//            else
//                ProofCreditsLogger.debug("GET request not worked");
//
//            //update proofcredits.json
//
//            //write token, tokenchain, BNK00
//
//                writeToFile("tempToken",token,false);
//                String tokenHash = IPFSNetwork.add("tempToken",ipfs);
//                writeToFile(TOKENS_PATH+tokenHash,token,false);
//                deleteFile("tempToken");
//                writeToFile(TOKENCHAIN_PATH+tokenHash+".json","[]",false);
//                JSONObject temp = new JSONObject();
//                temp.put("tokenHash",tokenHash);
//                JSONArray tempArray = new JSONArray();
//                tempArray.put(temp);
//                updateJSON("add",PAYMENTS_PATH+"BNK00.json",tempArray.toString());
//
//            FileWriter File = new FileWriter(jsonFilePath);
//            File.write(records.toString());
//            File.close();
//
//            ProofCreditsLogger.debug("Updated balance of node : " + balance);
//                long endtime = System.currentTimeMillis();
//                Iterator<String> keys = InitiatorConsensus.quorumSignature.keys();
//                JSONArray signedQuorumList = new JSONArray();
//                while(keys.hasNext())
//                    signedQuorumList.put(keys.next());
//                APIResponse.put("did", receiverDidIpfsHash);
//                APIResponse.put("tid", tid);
//                APIResponse.put("token",token);
//                APIResponse.put("quorumlist",signedQuorumList);
//                APIResponse.put("time",endtime-starttime);
//                APIResponse.put("status", "Success");
//                APIResponse.put("message", level+tokenNumber);
//             }
//
//        }
//        else {
//            APIResponse.put("did", receiverDidIpfsHash);
//            APIResponse.put("tid", "null");
//            APIResponse.put("status", "Failed");
//            APIResponse.put("message", "Insufficent proofs");
//            ProofCreditsLogger.warn("Insufficient proof credits to mine");
//            return APIResponse;
//        }
//        return APIResponse;

//======================================================================================================================






        int level = 0,tokenNumber = 0,availableCredits = 0, balance=0;
        long starttime = System.currentTimeMillis();
        JSONArray resJsonData = new JSONArray();

        //Reading proofcredits.json
        String jsonFilePath = WALLET_DATA_PATH+"QuorumSignedTransactions.json";
        JSONArray records = new JSONArray(readFile(jsonFilePath));
        balance = records.length();
        JSONArray prooftid = new JSONArray();
        for (int i = 0; i < balance; i++) {
            JSONObject temp = records.getJSONObject(i);
            if(temp.getBoolean("minestatus")==false) {
                availableCredits++;
               //prooftid.put(temp.getString("tid"));
                // records.getJSONObject(i).put("minestatus",true);
            }
        }


        if (availableCredits>=creditUsed) {

            //String GET_URL = SYNC_IP+"/getInfo?count="+availableCredits;

            String GET_URL = SYNC_IP + "/minetoken";
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
                ProofCreditsLogger.debug("response from service " + response.toString());

                //JSONObject responseJSON=new JSONObject(response.toString());
                //resJsonData= responseJSON.getJSONArray("data");
                //creditUsed = responseJSON.getInt("credits");

                resJsonData = new JSONArray(response.toString());

            } else
                ProofCreditsLogger.debug("GET request not worked");


            //Check if node can mine token
            if (resJsonData.length() > 0) {
                //Calling Mine token function
                JSONArray token = new JSONArray();

                for (int i = 0; i < resJsonData.length(); i++)
                    token.put(Functions.mineToken(resJsonData.getJSONObject(i).getInt("level"), resJsonData.getJSONObject(i).getInt("token")));

                String comments = resJsonData.toString() + prooftid;


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
                dataObject.put("token", token.toString());
                dataObject.put("alphaList", quorumPeersList.subList(0, 7));
                dataObject.put("betaList", quorumPeersList.subList(7, 14));
                dataObject.put("gammaList", quorumPeersList.subList(14, 21));

                InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 3);

                if (!(InitiatorConsensus.quorumSignature.length() >= 3 * minQuorum(7))) {
                    APIResponse.put("did", receiverDidIpfsHash);
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Consensus failed");
                    ProofCreditsLogger.debug("consensus failed");
                } else {
                    ProofCreditsLogger.debug("token mined " + token);

                    int counter = 0;
                    for (int i = 0; i < balance; i++) {
                        JSONObject temp = records.getJSONObject(i);
                        if (temp.getBoolean("minestatus") == false && (counter < creditUsed)) {
                            prooftid.put(temp.getString("tid"));
                            records.getJSONObject(i).put("minestatus", true);
                            counter++;
                        }
                    }


                    for (int i = 0; i < token.length(); i++) {
                        writeToFile("tempToken", token.getString(i), false);
                        String tokenHash = IPFSNetwork.add("tempToken", ipfs);
                        writeToFile(TOKENS_PATH + tokenHash, token.getString(i), false);
                        deleteFile("tempToken");
                        writeToFile(TOKENCHAIN_PATH + tokenHash + ".json", "[]", false);
                        JSONObject temp = new JSONObject();
                        temp.put("tokenHash", tokenHash);
                        JSONArray tempArray = new JSONArray();
                        tempArray.put(temp);
                        updateJSON("add", PAYMENTS_PATH + "BNK00.json", tempArray.toString());
                    }


                        writeToFile(jsonFilePath,records.toString(),false);
//                    FileWriter File = new FileWriter(jsonFilePath);
//                    File.write(records.toString());
//                    File.close();

                    ProofCreditsLogger.debug("Updated balance of node : " + (balance - creditUsed));
                    long endtime = System.currentTimeMillis();
                    Iterator<String> keys = InitiatorConsensus.quorumSignature.keys();
                    JSONArray signedQuorumList = new JSONArray();
                    while (keys.hasNext())
                        signedQuorumList.put(keys.next());
                    APIResponse.put("did", receiverDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("token", token);
                    APIResponse.put("creditsused", creditUsed);
                    APIResponse.put("quorumlist", signedQuorumList);
                    APIResponse.put("time", endtime - starttime);
                    APIResponse.put("status", "Success");
                    APIResponse.put("message", token.length()+" tokens mined");
                }

            } else {
                APIResponse.put("did", receiverDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "error from mine service");
                ProofCreditsLogger.warn("error from mine service");
                return APIResponse;
            }
        }
        else
        {
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


