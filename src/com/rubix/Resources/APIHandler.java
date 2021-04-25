package com.rubix.Resources;

import com.rubix.TokenTransfer.ProofCredits;
import com.rubix.TokenTransfer.TokenSender;
import io.ipfs.api.IPFS;
import io.ipfs.api.Peer;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.executeIPFSCommands;

public class APIHandler {
    private static final Logger APILogger = Logger.getLogger(APIHandler.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);


    /**
     * Initiates a transfer between two nodes
     * @param data Data specific to token transfer
     * @return Message from the sender with transaction details
     * @throws JSONException handles JSON Exceptions
     * @throws NoSuchAlgorithmException handles Invalid Algorithms Exceptions
     * @throws IOException handles IO Exceptions
     */



    public static JSONObject send(String data) throws Exception {
        Functions.pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        networkInfo();
        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String senDID = getValues(DATA_PATH + "DID.json", "didHash", "peerid", senderPeerID);


        JSONObject dataObject = new JSONObject(data);
        String recDID = dataObject.getString("recDID");
        String comments = dataObject.getString("comments");
        JSONArray tokens = dataObject.getJSONArray("tokens");
        JSONArray tokenHeader = dataObject.getJSONArray("tokenHeader");
        int amount = dataObject.getInt("amount");


        JSONObject sendMessage = new JSONObject();
        if (recDID.length() != 46) {
            sendMessage.put("did", senDID);
            sendMessage.put("tid", "null");
            sendMessage.put("status", "Failed");
            sendMessage.put("message", "Invalid Receiver Did Entered");
            return sendMessage;
        }

        if (tokens.length() < 1) {
            sendMessage.put("did", senDID);
            sendMessage.put("tid", "null");
            sendMessage.put("status", "Failed");
            sendMessage.put("message", "Invalid amount");
            return sendMessage;
        }

        JSONObject detailsObject = new JSONObject();
        detailsObject.put("tokens", tokens);
        detailsObject.put("receiverDidIpfsHash", recDID);
        detailsObject.put("comment", comments);
        detailsObject.put("pvt", DATA_PATH + senDID + "/PrivateShare.png");
        detailsObject.put("tokenHeader", tokenHeader);
        detailsObject.put("amount", amount);
        sendMessage =  TokenSender.Send(detailsObject.toString(), ipfs, SEND_PORT);
        APILogger.info(sendMessage);
        return sendMessage;
    }


    public static JSONObject create() throws Exception {
        Functions.pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        networkInfo();
        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String senDID = getValues(DATA_PATH + "DID.json", "didHash", "peerid", senderPeerID);

        JSONObject sendMessage = new JSONObject();
        JSONObject detailsObject = new JSONObject();
        detailsObject.put("receiverDidIpfsHash", senDID);
        detailsObject.put("pvt", DATA_PATH + senDID + "/PrivateShare.png");
        sendMessage =  ProofCredits.create(detailsObject.toString(), ipfs);
        APILogger.info(sendMessage);
        return sendMessage;
    }


    /**
     * A call to get details of a transaction given its ID
     * @param txnId
     * @return Transaction Details
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray transactionDetails(String txnId) throws JSONException {
        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONObject countResult = new JSONObject();
        JSONArray resultArray = new JSONArray();
        if (transactionHistory.length() == 0){
            countResult.put("Message", "No transactions found");
            resultArray.put(countResult);
            return resultArray;
        }

        JSONArray transArray = new JSONArray(transactionHistory);
        JSONObject obj = new JSONObject();
        for (int i = 0; i < transArray.length(); i++) {
            obj = transArray.getJSONObject(i);
            if (obj.get("txn").equals(txnId))
                resultArray.put(obj);
        }
        APILogger.info("Transaction Details for : " + obj.toString());
        return resultArray;
    }

    /**
     * A call to get the account information
     * @return Detailed explanation of the account information of the user
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray accountInformation() throws JSONException {
        int txnAsSender = 0, txnAsReceiver = 0;

        JSONObject objectParser;
        JSONArray resultArray = new JSONArray();
        JSONObject accountDetails = new JSONObject();
        File fileCheck1 = new File(WALLET_DATA_PATH + "TransactionHistory.json");
        if (!fileCheck1.exists()) {
            accountDetails.put("message", "Transaction History file not found");
            resultArray.put(accountDetails);
            return resultArray;
        }

        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray transArray = new JSONArray(transactionHistory);

        if(!(transArray.length() == 0)){
            for (int i = 0; i < transArray.length(); i++) {
                objectParser = transArray.getJSONObject(i);
                if (objectParser.get("role").equals("Sender"))
                    txnAsSender++;
                else txnAsReceiver++;
            }
        }

        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String did = getValues(DATA_PATH + "DID.json", "didHash", "peerid", senderPeerID);
        String wid = getValues(DATA_PATH + "DID.json", "walletHash", "peerid", senderPeerID);
        accountDetails.put("did", did);
        accountDetails.put("wid", wid);
        accountDetails.put("senderTxn", txnAsSender);
        accountDetails.put("receiverTxn", txnAsReceiver);

        resultArray.put(accountDetails);
        return resultArray;
    }

    /**
     * A call to sync all the nodes in the network
     * @return Message if failed or succeeded
     * @throws IOException
     */
    public static String networkInfo() throws IOException, JSONException {
        StringBuilder result = new StringBuilder();
        JSONArray resultArray = new JSONArray();
        JSONObject jsonObject = new JSONObject();
        int syncFlag = 0;
        URL url = new URL(SYNC_IP + "/get");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;

        while ((line = rd.readLine()) != null) {
            result.append(line);
            syncFlag = 1;
        }

        rd.close();

        writeToFile(DATA_PATH + "DataTable.json", result.toString(), false);
        if (syncFlag == 1) {
            jsonObject.put("message", "Synced Successfully!");
        } else {
            jsonObject.put("message", "Not synced! Try again after sometime.");
        }
        resultArray.put(jsonObject);
        return resultArray.toString();
    }

    /**
     * A call to get list transactions between two mentioned dates
     * @param s Start Date
     * @param e End Date
     * @return List of transactions
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray transactionsByDate(String s, String e) throws JSONException {
        JSONArray resultArray = new JSONArray();
        LocalDate start = LocalDate.parse(s);
        LocalDate end = LocalDate.parse(e);
        JSONObject countResult = new JSONObject();
        ArrayList<LocalDate> totalDates = new ArrayList<>();
        while (!start.isAfter(end)) {
            totalDates.add(start);
            start = start.plusDays(1);
        }


        File fileCheck1 = new File(WALLET_DATA_PATH + "TransactionHistory.json");
        if (!fileCheck1.exists()) {
            countResult.put("Message", "File not found");
            resultArray.put(countResult);
            return resultArray;
        }
        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");


        JSONArray transArray = new JSONArray(transactionHistory);

        if (transArray.length() == 0){
            countResult.put("Message", "No Transactions made yet");
            resultArray.put(countResult);
            return resultArray;
        }


        int count = 0;
        JSONObject Obj;
        String temp;
        while (count < totalDates.size()) {
            temp = totalDates.get(count).toString();
            for (int i = 0; i < transArray.length(); i++) {
                Obj = transArray.getJSONObject(i);
                if (temp.equals(Obj.get("Date")))
                    resultArray.put(Obj);
            }
            count++;
        }
        return resultArray;
    }

    /**
     * A call to get list of last n transactions
     * @param n Count
     * @return List of transactions
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray transactionsByCount(int n) throws JSONException {

        JSONObject countResult = new JSONObject();
        JSONArray resultArray = new JSONArray();

        String path = WALLET_DATA_PATH + "TransactionHistory.json";
        File transFile = new File(path);
        if (!transFile.exists()) {
            transFile.delete();
            countResult.put("Message", "File not found");
            resultArray.put(countResult);
            return resultArray;
        }
        String transactionHistory = readFile(path);
        JSONArray transArray = new JSONArray(transactionHistory);
        if (transArray.length() == 0){
            countResult.put("Message", "No transactions made yet");
            resultArray.put(countResult);
            return resultArray;
        }

        if (n >= transArray.length()) {
            for (int i = transArray.length()-1; i>=0; i--)
                resultArray.put(transArray.get(i));
            return resultArray;
        }

        for( int i = 1; i <= n; i++)
            resultArray.put(transArray.getJSONObject(transArray.length() - i));

        return resultArray;
    }

    /**
     * A call to get list transactions within a range
     * @param start start index
     * @param end end index
     * @return List of transactions
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray transactionsByRange(int start, int end) throws JSONException {
        JSONObject countResult = new JSONObject();
        JSONArray resultArray = new JSONArray();
        if (start <= 0 || end <= 0) {
            countResult.put("Message", "Count can't be null or negative");
            resultArray.put(countResult);
            return resultArray;
        }

        File transactionFile = new File(WALLET_DATA_PATH + "TransactionHistory.json");
        if (!transactionFile.exists()) {
            resultArray.put(countResult);
            return resultArray;
        }

        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray transArray = new JSONArray(transactionHistory);
        if (transArray.length() == 0){
            resultArray.put(countResult);
            return resultArray;
        }


        for(int i = start; i < end; i++){
            JSONObject object = transArray.getJSONObject(i);
            resultArray.put(object);
        }

        return resultArray;
    }

    /**
     * A call to close all open IPFS streams
     */
    public static void closeStreams(){
        executeIPFSCommands("ipfs p2p close --all");
    }

    /**
     * A call to get list transactions with the mentioned comment
     * @param comment Comment
     * @return List of transactions
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray transactionsByComment(String comment) throws JSONException {

        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray transArray = new JSONArray(transactionHistory);
        JSONObject obj;
        JSONArray resultArray = new JSONArray();
        for (int i = 0; i < transArray.length(); i++) {
            obj = transArray.getJSONObject(i);
            if (obj.get("comment").equals(comment))
                resultArray.put(obj);
        }
        if(resultArray.length() < 1){
            JSONObject returnObject = new JSONObject();
            returnObject.put("Message", "No transactions found with the comment " + comment);
            resultArray.put(returnObject);
        }

        return resultArray;
    }


    /**
     * A call to get list transactions made by the user with the input Did
     * @param did DID of the contact
     * @return List of transactions committed with the user DID
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray transactionsByDID(String did) throws JSONException {
        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray transArray = new JSONArray(transactionHistory);
        JSONArray resultArray = new JSONArray();
        for (int i = 0; i < transArray.length(); i++) {
            JSONObject didObject = transArray.getJSONObject(i);
            if (didObject.get("senderDID").equals(did) || didObject.get("receiverDID").equals(did))
               resultArray.put(didObject);
        }

        return resultArray;
    }


    public static int onlinePeersCount() throws JSONException, IOException, InterruptedException {
        JSONArray peersArray = peersOnlineStatus();
        int count = 0;
        for (int i = 0; i < peersArray.length(); i++){
            if(peersArray.getJSONObject(i).getString("onlineStatus").contains("online"))
                count++;
        }
        return count;
    }


    public static ArrayList swarmPeersList() throws IOException, InterruptedException {
        String OS = getOsName();
        String[] command = new String[3];
        if(OS.contains("Mac") || OS.contains("Linux")){
            command[0] = "bash";
            command[1] = "-c";
        }
        else if(OS.contains("Windows")){
            command[0] = "cmd.exe";
            command[1] = "/c";
        }
        command[2] = "export PATH=/usr/local/bin:$PATH && ipfs swarm peers";

        Process P = Runtime.getRuntime().exec(command);
        BufferedReader br = new BufferedReader(new InputStreamReader(P.getInputStream()));

        ArrayList peersArray = new ArrayList();
        String line;
        while((line = br.readLine()) != null) {
            peersArray.add(line);
        }
        if (!OS.contains("Windows"))
            P.waitFor();
        br.close();
        P.destroy();

        ArrayList peersIdentities = new ArrayList();
        if(peersArray.size() != 0){
            List<Peer> k = ipfs.swarm.peers();
            for(int i = 0; i < k.size(); i++)
                peersIdentities.add(k.get(i).toString().substring(0, 46));

            return peersIdentities;
        }
        return peersArray;
    }
    /**
     * A call to get the online/offline status of your contacts
     * @return List indicating online status of each DID contact
     * @throws JSONException handles JSON Exceptions
     * @throws IOException handles IO Exceptions
     */
    public static JSONArray peersOnlineStatus() throws JSONException, IOException, InterruptedException {
        ArrayList peersArray = swarmPeersList();
        String dataTable = readFile(DATA_PATH + "DataTable.json");
        JSONArray dataArray = new JSONArray(dataTable);
        JSONArray onlinePeers = new JSONArray();

        for(int i = 0; i < dataArray.length(); i++){
            JSONObject peerObject = dataArray.getJSONObject(i);
            String peerID = peerObject.getString("peerid");
            if(peersArray.contains(peerID)){
                JSONObject onlinePeersObject = new JSONObject();
                onlinePeersObject.put("did", getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID));
                onlinePeersObject.put("onlineStatus", "online");
                onlinePeers.put(onlinePeersObject);
            }
            else{
                JSONObject onlinePeersObject = new JSONObject();
                onlinePeersObject.put("did", getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID));
                onlinePeersObject.put("onlineStatus", "offline");
                onlinePeers.put(onlinePeersObject);
            }

        }

        return onlinePeers;
    }

    /**
     * A call to list out all contacts in the user wallet
     * @return A list of user wallet contacts
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray contacts() throws JSONException {
        String dataTable = readFile(DATA_PATH + "DataTable.json");
        JSONArray dataArray = new JSONArray(dataTable);
        JSONArray didArray = new JSONArray();
        for (int i = 0; i < dataArray.length(); i++){
            didArray.put(dataArray.getJSONObject(i).getString("didHash"));
        }
        return didArray;
    }

    /**
     * A call to list out number of transactions made per day
     * @return List of transactions committed on every date
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray txnPerDay() throws JSONException {
        String dataTable = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray dataArray = new JSONArray(dataTable);
        HashSet<String> dateSet = new HashSet<>();
        for(int i = 0; i < dataArray.length(); i++)
            dateSet.add(dataArray.getJSONObject(i).getString("Date"));

        JSONObject datesTxn = new JSONObject();
        Iterator<String> dateIterator = dateSet.iterator();
        while (dateIterator.hasNext()){
            String date = dateIterator.next();
            int count = 0;
            for(int i = 0; i < dataArray.length(); i++){
                JSONObject object = dataArray.getJSONObject(i);

                if(date.equals(object.getString("Date"))){
                    count++;
                }
            }
            datesTxn.put(date, count);

        }

        return new JSONArray().put(datesTxn);
    }
}
