package com.rubix.Resources;

import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.IPFS_PORT;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.SEND_PORT;
import static com.rubix.Resources.Functions.SYNC_IP;
import static com.rubix.Resources.Functions.WALLET_DATA_PATH;
import static com.rubix.Resources.Functions.getOsName;
import static com.rubix.Resources.Functions.getPeerID;
import static com.rubix.Resources.Functions.getValues;
import static com.rubix.Resources.Functions.nodeData;
import static com.rubix.Resources.Functions.readFile;
import static com.rubix.Resources.Functions.writeToFile;
import static com.rubix.Resources.IPFSNetwork.add;
import static com.rubix.Resources.IPFSNetwork.executeIPFSCommands;
import static com.rubix.Resources.IPFSNetwork.pin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import com.rubix.Denominations.SendTokenParts;
import com.rubix.Mining.ProofCredits;
import com.rubix.TokenTransfer.TokenSender;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.ipfs.api.IPFS;
import io.ipfs.api.Peer;

public class APIHandler {
    private static final Logger APILogger = Logger.getLogger(APIHandler.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    private static final Logger eventLogger = Logger.getLogger("eventLogger");

    /**
     * Initiates a transfer between two nodes
     * 
     * @param data Data specific to token transfer
     * @return Message from the sender with transaction details
     * @throws JSONException            handles JSON Exceptions
     * @throws NoSuchAlgorithmException handles Invalid Algorithms Exceptions
     * @throws IOException              handles IO Exceptions
     */

    public static JSONObject send(String data) throws Exception {
        Functions.pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String senDID = getValues(DATA_PATH + "DID.json", "didHash", "peerid", senderPeerID);

        JSONObject dataObject = new JSONObject(data);
        String recDID = dataObject.getString("receiverDidIpfsHash");

        String dataTableData = readFile(DATA_PATH + "DataTable.json");
        boolean isObjectValid = false;
        JSONArray dataTable = new JSONArray(dataTableData);
        // check value matches any of the data in the data table
        for (int i = 0; i < dataTable.length(); i++) {
            JSONObject dataTableObject = dataTable.getJSONObject(i);
            if (dataTableObject.getString("didHash").equals(recDID)) {
                isObjectValid = true;
            }
        }
        if (!isObjectValid)
            networkInfo();

        // String comments = dataObject.getString("comments");
        JSONArray tokens = dataObject.getJSONArray("tokens");

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

        dataObject.put("pvt", DATA_PATH + senDID + "/PrivateShare.png");
        sendMessage = TokenSender.Send(dataObject.toString(), ipfs, SEND_PORT);

        APILogger.info(sendMessage);
        return sendMessage;
    }

    /**
     * A call to send tokens in parts
     * 
     * @param data Data specific to token transfer
     * @return Message from the sender with transaction details
     * @throws Exception throws Exception
     */
    public static JSONObject sendParts(String data) throws Exception {
        Functions.pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        APILogger.debug("Sending Parts");
        Functions.pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String senDID = getValues(DATA_PATH + "DID.json", "didHash", "peerid", senderPeerID);

        JSONObject dataObject = new JSONObject(data);
        String recDID = dataObject.getString("receiverDidIpfsHash");

        String dataTableData = readFile(DATA_PATH + "DataTable.json");
        boolean isObjectValid = false;
        JSONArray dataTable = new JSONArray(dataTableData);
        // check value matches any of the data in the data table
        for (int i = 0; i < dataTable.length(); i++) {
            JSONObject dataTableObject = dataTable.getJSONObject(i);
            if (dataTableObject.getString("didHash").equals(recDID)) {
                isObjectValid = true;
            }
        }
        if (!isObjectValid)
            networkInfo();

        JSONObject sendMessage = new JSONObject();
        if (recDID.length() != 46) {
            sendMessage.put("did", senDID);
            sendMessage.put("tid", "null");
            sendMessage.put("status", "Failed");
            sendMessage.put("message", "Invalid Receiver Did Entered");
            return sendMessage;
        }
        dataObject.put("pvt", DATA_PATH + senDID + "/PrivateShare.png");
        sendMessage = SendTokenParts.Send(dataObject.toString(), ipfs, 9999);

        APILogger.info(sendMessage);
        return sendMessage;
    }

    /**
     * An API call to mine tokens
     * 
     * @param type Type of quorum Selection
     * @return JSONObject with status message
     * @throws Exception throws Exception
     */
    public static JSONObject create(int type) throws Exception {
        Functions.pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String senDID = getValues(DATA_PATH + "DID.json", "didHash", "peerid", senderPeerID);

        String dataTableData = readFile(DATA_PATH + "DataTable.json");
        boolean isObjectValid = false;
        JSONArray dataTable = new JSONArray(dataTableData);
        // check value matches any of the data in the data table
        for (int i = 0; i < dataTable.length(); i++) {
            JSONObject dataTableObject = dataTable.getJSONObject(i);
            if (dataTableObject.getString("didHash").equals(senDID)) {
                isObjectValid = true;
            }
        }
        if (!isObjectValid)
            networkInfo();

        JSONObject sendMessage = new JSONObject();
        JSONObject detailsObject = new JSONObject();
        detailsObject.put("receiverDidIpfsHash", senDID);
        detailsObject.put("pvt", DATA_PATH + senDID + "/PrivateShare.png");
        detailsObject.put("type", type);
        sendMessage = ProofCredits.create(detailsObject.toString(), ipfs);
        APILogger.info(sendMessage);
        return sendMessage;
    }

    /**
     * A call to get details of a transaction given its ID
     * 
     * @param txnId
     * @return Transaction Details
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray transactionDetails(String txnId) throws JSONException {
        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONObject countResult = new JSONObject();
        JSONArray resultArray = new JSONArray();
        if (transactionHistory.length() == 0) {
            countResult.put("Message", "No transactions found");
            resultArray.put(countResult);
            return resultArray;
        }

        JSONArray transArray = new JSONArray(transactionHistory);
        JSONObject obj = new JSONObject();
        for (int i = 0; i < transArray.length(); i++) {
            obj = transArray.getJSONObject(i);
            if (obj.get("txn").equals(txnId)) {
                obj.remove("essentialShare");

                if (obj.has("amount"))
                    obj.put("amount", obj.getDouble("amount"));
                else
                    obj.put("amount", obj.getJSONArray("tokens").length());

                resultArray.put(obj);
            }

        }
        APILogger.info("Transaction Details for : " + obj.toString());
        return resultArray;
    }

    /**
     * A call to get the account information
     * 
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

        if (!(transArray.length() == 0)) {
            for (int i = 0; i < transArray.length(); i++) {
                objectParser = transArray.getJSONObject(i);
                if (objectParser.get("role").equals("Sender"))
                    txnAsSender++;
                else
                    txnAsReceiver++;
            }
        }

        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String did = getValues(DATA_PATH + "DID.json", "didHash", "peerid", senderPeerID);
        String wid = getValues(DATA_PATH + "DID.json", "walletHash", "peerid", senderPeerID);
        accountDetails.put("did", did);
        accountDetails.put("wid", wid);
        accountDetails.put("senderTxn", txnAsSender);
        accountDetails.put("receiverTxn", txnAsReceiver);
        accountDetails.put("totalTxn", txnAsSender + txnAsReceiver);

        resultArray.put(accountDetails);
        return resultArray;
    }

    /**
     * A method to add and host your DID ans Public share to ipfs
     */
    public static void addPublicData() {
        String peerID = getPeerID(DATA_PATH + "DID.json");
        String didHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID);
        String walletHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid", peerID);

        add(DATA_PATH.concat(didHash).concat("/DID.png"), ipfs);
        pin(didHash, ipfs);

        add(DATA_PATH.concat(didHash).concat("/PublicShare.png"), ipfs);
        pin(walletHash, ipfs);

    }

    /**
     * A call to sync all the nodes in the network
     * 
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
     * 
     * @param s Start Date
     * @param e End Date
     * @return List of transactions
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray transactionsByDate(String s, String e) throws JSONException, ParseException {
        JSONArray resultArray = new JSONArray();
        String strDateFormat = "yyyy-MMM-dd HH:mm:ss"; // Date format is Specified
        SimpleDateFormat objSDF = new SimpleDateFormat(strDateFormat);
        Date date1 = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy").parse(s);
        String startDateString = objSDF.format(date1);
        Date date2 = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy").parse(e);
        String endDateString = objSDF.format(date2);
        JSONObject countResult = new JSONObject();
        Date startDate = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss").parse(startDateString);
        Date endDate = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss").parse(endDateString);
        APILogger.debug("start date is " + startDate);
        APILogger.debug("end date is " + endDate);
        File fileCheck1 = new File(WALLET_DATA_PATH + "TransactionHistory.json");
        if (!fileCheck1.exists()) {
            countResult.put("Message", "File not found");
            resultArray.put(countResult);
            return resultArray;
        }
        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray transArray = new JSONArray(transactionHistory);
        if (transArray.length() == 0) {
            countResult.put("Message", "No Transactions made yet");
            resultArray.put(countResult);
            return resultArray;
        }
        JSONObject obj = new JSONObject();
        for (int i = 0; i < transArray.length(); i++) {
            obj = transArray.getJSONObject(i);

            String dateFromTxnHistoryString = obj.get("Date").toString();
            Date dateTH = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy").parse(dateFromTxnHistoryString);
            String dateTHS = objSDF.format(dateTH);
            Calendar c = Calendar.getInstance();
            c.setTime(objSDF.parse(dateTHS));
            dateTH = c.getTime();
            APILogger.debug("dateFromTxnHistory " + dateTH);
            if (dateTH.after(startDate) && dateTH.before(endDate)) {
                obj.remove("essentialShare");

                if (obj.has("amount"))
                    obj.put("amount", obj.getDouble("amount"));
                else
                    obj.put("amount", obj.getJSONArray("tokens").length());

                resultArray.put(obj);
            }

        }
        return resultArray;
    }

    /**
     * A call to get list of last n transactions
     * 
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
        JSONObject obj = new JSONObject();
        if (transArray.length() == 0) {
            countResult.put("Message", "No transactions made yet");
            resultArray.put(countResult);
            return resultArray;
        }

        if (n >= transArray.length()) {
            for (int i = transArray.length() - 1; i >= 0; i--) {
                obj = transArray.getJSONObject(i);
                obj.remove("essentialShare");
                if (obj.has("amount"))
                    obj.put("amount", obj.getDouble("amount"));
                else
                    obj.put("amount", obj.getJSONArray("tokens").length());
                resultArray.put(obj);
            }
            return resultArray;
        }

        for (int i = 1; i <= n; i++) {

            obj = transArray.getJSONObject(transArray.length() - i);
            obj.remove("essentialShare");
            if (obj.has("amount"))
                obj.put("amount", obj.getDouble("amount"));
            else
                obj.put("amount", obj.getJSONArray("tokens").length());

            resultArray.put(obj);
        }
        return resultArray;
    }

    /**
     * A call to get list transactions within a range
     * 
     * @param start start index
     * @param end   end index
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

        if (start > end) {
            countResult.put("Message", "Invalid ranges");
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
        if (transArray.length() == 0) {
            resultArray.put(countResult);
            return resultArray;
        }

        if (!(end < transArray.length())) {
            for (int i = start; i < transArray.length(); i++) {
                JSONObject obj = transArray.getJSONObject(i);
                obj.remove("essentialShare");
                if (obj.has("amount"))
                    obj.put("amount", obj.getDouble("amount"));
                else
                    obj.put("amount", obj.getJSONArray("tokens").length());

                resultArray.put(obj);
            }
        } else {
            if (start == end) {
                JSONObject obj = transArray.getJSONObject(start);
                obj.remove("essentialShare");
                if (obj.has("amount"))
                    obj.put("amount", obj.getDouble("amount"));
                else
                    obj.put("amount", obj.getJSONArray("tokens").length());

                resultArray.put(obj);
            }
            for (int i = start; i < end; i++) {
                JSONObject obj = transArray.getJSONObject(i);
                obj.remove("essentialShare");
                if (obj.has("amount"))
                    obj.put("amount", obj.getDouble("amount"));
                else
                    obj.put("amount", obj.getJSONArray("tokens").length());

                resultArray.put(obj);
            }
        }

        return resultArray;
    }

    /**
     * @throws JSONException
     *
     */
    public static JSONObject creditsInfo() throws JSONException {
        // String thFile = WALLET_DATA_PATH.concat("TransactionHistory.json");
        String qstFile = WALLET_DATA_PATH.concat("QuorumSignedTransactions.json");
        String mineFile = WALLET_DATA_PATH.concat("MinedCreditsHistory.json");

        // File txnFile = new File(thFile);
        File quorumFile = new File(qstFile);
        File minedFile = new File(mineFile);

        // int txnCount = 0;
        // if(txnFile.exists()){
        // String transactionFile =
        // readFile(WALLET_DATA_PATH.concat("TransactionHistory.json"));
        // JSONArray txnArray = new JSONArray(transactionFile);
        // txnCount = txnArray.length();
        //
        // }
        int spentCredits = 0;
        int unspentCredits = 0;
        if (quorumFile.exists()) {
            String qFile = readFile(qstFile);
            JSONArray qArray = new JSONArray(qFile);
            unspentCredits = qArray.length();
        }
        if (minedFile.exists()) {
            String mFile = readFile(mineFile);
            JSONArray mArray = new JSONArray(mFile);
            spentCredits = mArray.length();
        }

        JSONObject returnObject = new JSONObject();
        // returnObject.put("txnCount",txnCount);
        returnObject.put("spentCredits", spentCredits);
        returnObject.put("unspentCredits", unspentCredits);

        return returnObject;
    }

    /**
     * A call to close all open IPFS streams
     */
    public static void closeStreams() {
        executeIPFSCommands("ipfs p2p close --all");
    }

    /**
     * A call to get list transactions with the mentioned comment
     * 
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

            if (obj.get("comment").equals(comment)) {
                obj.remove("essentialShare");
                if (obj.has("amount"))
                    obj.put("amount", obj.getDouble("amount"));
                else
                    obj.put("amount", obj.getJSONArray("tokens").length());

                resultArray.put(obj);
            }

        }
        if (resultArray.length() < 1) {
            JSONObject returnObject = new JSONObject();
            returnObject.put("Message", "No transactions found with the comment " + comment);
            resultArray.put(returnObject);
        }

        return resultArray;
    }

    /**
     * A call to get list transactions made by the user with the input Did
     * 
     * @param did DID of the contact
     * @return List of transactions committed with the user DID
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray transactionsByDID(String did) throws JSONException {
        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray transArray = new JSONArray(transactionHistory);
        JSONArray resultArray = new JSONArray();
        for (int i = 0; i < transArray.length(); i++) {
            JSONObject obj = transArray.getJSONObject(i);
            obj.remove("essentialShare");

            if (obj.has("amount"))
                obj.put("amount", obj.getDouble("amount"));
            else
                obj.put("amount", obj.getJSONArray("tokens").length());

            resultArray.put(obj);

            if (obj.get("senderDID").equals(did) || obj.get("receiverDID").equals(did))
                resultArray.put(obj);
        }

        return resultArray;
    }

    public static int onlinePeersCount() throws JSONException, IOException, InterruptedException {
        JSONArray peersArray = peersOnlineStatus();
        int count = 0;
        for (int i = 0; i < peersArray.length(); i++) {
            if (peersArray.getJSONObject(i).getString("onlineStatus").contains("online"))
                count++;
        }
        return count;
    }

    public static ArrayList swarmPeersList() throws IOException, InterruptedException {
        String OS = getOsName();
        String[] command = new String[3];
        if (OS.contains("Mac") || OS.contains("Linux")) {
            command[0] = "bash";
            command[1] = "-c";
        } else if (OS.contains("Windows")) {
            command[0] = "cmd.exe";
            command[1] = "/c";
        }
        command[2] = "export PATH=/usr/local/bin:$PATH && ipfs swarm peers";

        Process P = Runtime.getRuntime().exec(command);
        BufferedReader br = new BufferedReader(new InputStreamReader(P.getInputStream()));

        ArrayList peersArray = new ArrayList();
        String line;
        while ((line = br.readLine()) != null) {
            peersArray.add(line);
        }
        if (!OS.contains("Windows"))
            P.waitFor();
        br.close();
        P.destroy();

        ArrayList peersIdentities = new ArrayList();
        if (peersArray.size() != 0) {
            List<Peer> k = ipfs.swarm.peers();
            for (int i = 0; i < k.size(); i++)
                peersIdentities.add(k.get(i).toString().substring(0, 46));

            return peersIdentities;
        }
        return peersArray;
    }

    /**
     * A call to get the online/offline status of your contacts
     * 
     * @return List indicating online status of each DID contact
     * @throws JSONException handles JSON Exceptions
     * @throws IOException   handles IO Exceptions
     */
    public static JSONArray peersOnlineStatus() throws JSONException, IOException, InterruptedException {
        ArrayList peersArray = swarmPeersList();
        String dataTable = readFile(DATA_PATH + "DataTable.json");
        JSONArray dataArray = new JSONArray(dataTable);
        JSONArray onlinePeers = new JSONArray();

        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject peerObject = dataArray.getJSONObject(i);
            String peerID = peerObject.getString("peerid");
            if (peersArray.contains(peerID)) {
                JSONObject onlinePeersObject = new JSONObject();
                onlinePeersObject.put("did", getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID));
                onlinePeersObject.put("onlineStatus", "online");
                onlinePeers.put(onlinePeersObject);
            } else {
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
     * 
     * @return A list of user wallet contacts
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray contacts() throws JSONException {
        String dataTable = readFile(DATA_PATH + "DataTable.json");
        JSONArray dataArray = new JSONArray(dataTable);
        JSONArray didArray = new JSONArray();
        for (int i = 0; i < dataArray.length(); i++) {
            didArray.put(dataArray.getJSONObject(i).getString("didHash"));
        }
        return didArray;
    }

    /**
     * A call to list out number of transactions made per day
     * 
     * @return List of transactions committed on every date
     * @throws JSONException handles JSON Exceptions
     */
    public static JSONArray txnPerDay() throws JSONException {
        String dataTable = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray dataArray = new JSONArray(dataTable);
        HashSet<String> dateSet = new HashSet<>();
        for (int i = 0; i < dataArray.length(); i++)
            dateSet.add(dataArray.getJSONObject(i).getString("Date"));

        JSONObject datesTxn = new JSONObject();
        Iterator<String> dateIterator = dateSet.iterator();
        while (dateIterator.hasNext()) {
            String date = dateIterator.next();
            int count = 0;
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject obj = dataArray.getJSONObject(i);

                if (date.equals(obj.getString("Date"))) {
                    count++;
                }
            }
            datesTxn.put(date, count);

        }

        return new JSONArray().put(datesTxn);
    }

    public static JSONObject syncNetworkNodes() throws JSONException, IOException {
        String dataTable = readFile(DATA_PATH + "DataTable.json");
        JSONArray dataArray = new JSONArray(dataTable);

        for (int i = 0; i < dataArray.length(); i++)
            nodeData(dataArray.getJSONObject(i).getString("didHash"),
                    dataArray.getJSONObject(i).getString("walletHash"), ipfs);

        return new JSONObject("{\"message\":\"Synced all nodes\"}");
    }
}
