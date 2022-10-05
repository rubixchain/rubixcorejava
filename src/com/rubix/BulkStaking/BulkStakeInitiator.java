package com.rubix.BulkStaking;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import static com.rubix.Resources.Functions.*;

import java.io.IOException;
import java.util.ArrayList;

import io.ipfs.api.IPFS;


public class BulkStakeInitiator {

    public static Logger BulkStakeInitiatorLogger = Logger.getLogger(BulkStakeInitiator.class);
    private static int alphaSize;
    private static ArrayList alphaPeersList;
    private static ArrayList betaPeersList;
    private static ArrayList gammaPeersList;
    public static JSONArray alphaReply, betaReply, gammaReply;
    public static String operation = "Bulk Staking";

    

    public static JSONObject bulkStake(String data, IPFS ipfs) throws JSONException, IOException{

        JSONArray alphaQuorum = new JSONArray();
        JSONArray betaQuorum = new JSONArray();
        JSONArray gammaQuorum = new JSONArray();
        JSONObject ipData = new JSONObject(data);
        int bulkStakeAmount = ipData.getInt("bulkStakeAmount");
        String pvtShare = ipData.getString("pvt");
        String DID = ipData.getString("receiverDidIpfsHash");

        JSONObject APIResponse = new JSONObject();

        //Checking token balance
        pathSet();
        String bank = readFile(PAYMENTS_PATH.concat("BNK00.json"));
        JSONArray bankArray = new JSONArray(bank);

        if(bankArray.length()<bulkStakeAmount){
/* write exiting code (API response) */
        }


        //Collecting tokens
        ArrayList<String> tokensToStake = new ArrayList<String>();
        for(int i =0; i<bulkStakeAmount; i++){
            tokensToStake.add(bankArray.getString(i));
        }


        //Generating Stake ID
        ArrayList<String> STAKE_IDs = new ArrayList<String>();

        for(int i =0; i<tokensToStake.size(); i++){

            String forHashing = (tokensToStake.get(i)).concat(DID);
            String stakeID = calculateHash( forHashing,"SHA3-256");
            STAKE_IDs.add(stakeID);

        }


        /*GETTING QUORUM DATA READY*/
        JSONArray quorumArray;
        
        quorumArray = getQuorum(DID, DID, tokensToStake.size());
        if (quorumArray.length() == 1) {
            APIResponse.put("did", DID);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Type 1 mining not allowed. Please use subnet quorum to mine.");
            BulkStakeInitiatorLogger.warn("Quorum Members not available for type 1 mining");
            return APIResponse;
        }        

        // Sanity Check                 
        int alphaCheck = 0, betaCheck = 0, gammaCheck = 0;
        JSONArray sanityFailedQuorum = new JSONArray();
        for (int i = 0; i < quorumArray.length(); i++) {
            String quorumPeerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
                    quorumArray.getString(i));
            boolean quorumSanityCheck = sanityCheck("Quorum", quorumPeerID, ipfs, SEND_PORT + 11);

//Port number to be used above ?
            
            if (!quorumSanityCheck) {
                sanityFailedQuorum.put(quorumPeerID);
                if (i <= 6)
                    alphaCheck++;
                if (i >= 7 && i <= 13)
                    betaCheck++;
                if (i >= 14 && i <= 20)
                    gammaCheck++;
            }
        }

        if (alphaCheck > 2 || betaCheck > 2 || gammaCheck > 2) {
            APIResponse.put("did", DID);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            String message = "Quorum: ".concat(sanityFailedQuorum.toString()).concat(" ");
            APIResponse.put("message", message.concat(sanityMessage));
            BulkStakeInitiatorLogger.warn("Quorum: ".concat(message.concat(sanityMessage)));
            return APIResponse;
        }//Sanity check ends.

        
        QuorumSwarmConnect(quorumArray, ipfs);

        alphaSize = quorumArray.length() - 14;


        for (int i = 0; i < alphaSize; i++)
            alphaQuorum.put(quorumArray.getString(i));

        for (int i = 0; i < 7; i++) {
            betaQuorum.put(quorumArray.getString(alphaSize + i));
            gammaQuorum.put(quorumArray.getString(alphaSize + 7 + i));
        }

        alphaPeersList = QuorumCheck(alphaQuorum, alphaSize);
        betaPeersList = QuorumCheck(betaQuorum, 7);
        gammaPeersList = QuorumCheck(gammaQuorum, 7);



        if (alphaPeersList.size() < minQuorum(alphaSize) || betaPeersList.size() < 5
                || gammaPeersList.size() < 5) {
            updateQuorum(quorumArray, null, false, 1);
            APIResponse.put("did", DID);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Quorum Members not available");
            BulkStakeInitiatorLogger.warn("Quorum Members not available");
            return APIResponse;
        }
        return APIResponse;


        JSONObject dataObject = new JSONObject();
        //dataObject.put("tid", tid);
        //dataObject.put("receiverDID", DID);
        dataObject.put("nodesDID", DID);
        dataObject.put("nodesPvtShare", pvtShare);
        dataObject.put("tokensToStake", tokensToStake);
        

//All tokens to be verified by all quorums? Or distribute work among them?


        Thread alphaThread = new Thread(() -> {
            try {
                alphaReply = BulkStakeConsensus.start(dataObject.toString(), ipfs, PORT, 0, "alpha", alphaPeersList, alphaSize,
                        alphaSize, operation);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        Thread betaThread = new Thread(() -> {
            try {
                betaReply = BulkStakeConsensus.start(dataObject.toString(), ipfs, PORT + 100, 1, "beta", betaPeersList,
                        alphaSize, 7, operation);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        Thread gammaThread = new Thread(() -> {
            try {
                gammaReply = BulkStakeConsensus.start(dataObject.toString(), ipfs, PORT + 107, 2, "gamma", gammaPeersList,
                        alphaSize, 7, operation);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });



        








        /* Write the chain to the location with the updated genesis block */


        /* String tokenChainString = readFile(TOKENCHAIN_PATH + tokensToStake.get(i) + ".json");
        JSONArray tokenChain = new JSONArray(tokenChainString);

        JSONObject genesisObj = new JSONObject();
        genesisObj = tokenChain.getJSONObject(0);
        genesisObj.put("stakeID", STAKE_ID);

        JSONArray newChain = new JSONArray();
        newChain.put(genesisObj);

        for(int j = 1; j < tokenChain.length(); j++){
            JSONObject temp = tokenChain.getJSONObject(j);
            newChain.put(temp);
        } */


    }

}