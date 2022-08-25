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

import com.rubix.Datum.DataCommitter;
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
        sendMessage = TokenSender.Send(dataObject.toString(), ipfs, SEND_PORT);

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
     * A method to commit block to the RubixChain and 
     * returns data token
     * 
     * @param data
     * @return Data Token
     */
    public static JSONObject commit(String data) throws Exception {
        Functions.pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        
     //   APILogger.debug("data is "+ data);
        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String senDID = getValues(DATA_PATH + "DID.json", "didHash", "peerid", senderPeerID);
        JSONArray tokens;
        JSONObject dataObject = new JSONObject(data);
        
   //     APILogger.debug("dataObject is "+ dataObject.toString());
       // String recDID = dataObject.getString("receiverDidIpfsHash");
        String blockHash;
        String recDID;

        String dataTableData = readFile(DATA_PATH + "DataTable.json");
        boolean isObjectValid = false;
        JSONArray dataTable = new JSONArray(dataTableData);
        JSONObject sendMessage = new JSONObject();

    
    //       APILogger.debug("Trans type is "+ DATA);

         //  dataObject.put(TRANS_TYPE, DATA);
           
           blockHash = dataObject.getString("blockHash");
           if (blockHash.length() != 46) {
               sendMessage.put("did", senDID);
               sendMessage.put("tid", "null");
               sendMessage.put("status", "Failed");
               sendMessage.put("message", "Invalid Block Hash Entered");
               return sendMessage;
           }
       
      
       dataObject.put("pvt", DATA_PATH + senDID + "/PrivateShare.png");
   //    APILogger.debug("dataObeject is "+dataObject.toString());
    	   sendMessage = DataCommitter.Commit(dataObject.toString(), ipfs, SEND_PORT);
   //    APILogger.debug("send Message is "+sendMessage);
       return sendMessage;
   }

    /**
     * A method to add and host your DID ans Public share to ipfs
     * 
     * @files DID.json, DataTable.json, DID.png, PublicShare.png
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
     * @files DataTable.json
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


    public static String getPubKeyIpfsHash_DIDserver(String senderDidIpfsHash, int type) throws IOException{
        
            String pubKeyIpfsHash;
            String quorum_pubKeyIpfsHash;

            URL url2 = new URL(SYNC_IP + "/getPubKeyData");
            HttpURLConnection conn = (HttpURLConnection) url2.openConnection();
            conn.setRequestMethod("GET");
            StringBuilder result = new StringBuilder();
            //JSONArray resultArray = new JSONArray();
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
    
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();
              
            File dt_PubKey =new File(DATA_PATH + "DataTable_PublicKeys.json");
            if(! dt_PubKey.exists()) {
                dt_PubKey.createNewFile();
            }
            
            writeToFile(DATA_PATH + "DataTable_PublicKeys.json", result.toString(), false);
            
    
            if(type == 1){
                
                pubKeyIpfsHash = getValues(DATA_PATH + "DataTable_PublicKeys.json","pubKeyIpfsHash","didHash", senderDidIpfsHash);
                return pubKeyIpfsHash;

            }
            else{
                
                quorum_pubKeyIpfsHash = getValues(DATA_PATH + "DataTable_PublicKeys.json","quorum_pubKeyIpfsHash","didHash", senderDidIpfsHash);
                return quorum_pubKeyIpfsHash;
            }
            
    }

    /**
     * Method to query the credits information
     * 
     * @throws JSONException
     * @files QuorumSignedTransactions.json, MinedCreditsHistory.json
     */
    public static JSONObject creditsInfo() throws JSONException {
        String qstFile = WALLET_DATA_PATH.concat("QuorumSignedTransactions.json");
        String mineFile = WALLET_DATA_PATH.concat("MinedCreditsHistory.json");

        File quorumFile = new File(qstFile);
        File minedFile = new File(mineFile);

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

    public static JSONObject syncNetworkNodes() throws JSONException, IOException {
        String dataTable = readFile(DATA_PATH + "DataTable.json");
        JSONArray dataArray = new JSONArray(dataTable);

        for (int i = 0; i < dataArray.length(); i++)
            nodeData(dataArray.getJSONObject(i).getString("didHash"),
                    dataArray.getJSONObject(i).getString("walletHash"), ipfs);

        return new JSONObject("{\"message\":\"Synced all nodes\"}");
    }

    /**
     * A call to get the account information
     * 
     * @return Detailed explanation of the account information of the user
     * @throws JSONException handles JSON Exceptions
     * @files TransactionHistory.json, DID.json
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
     * A call to list out number of transactions made per day
     * 
     * @return List of transactions committed on every date
     * @throws JSONException handles JSON Exceptions
     * @files TransactionHistory.json
     */
    public static JSONArray txnPerDay() throws JSONException {
        String dataTableFileContent = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray dataTable = new JSONArray(dataTableFileContent);
        HashSet<String> dateSet = new HashSet<>();
        for (int i = 0; i < dataTable.length(); i++)
            dateSet.add(dataTable.getJSONObject(i).getString("Date"));

        JSONObject datesTxn = new JSONObject();
        Iterator<String> dateIterator = dateSet.iterator();
        while (dateIterator.hasNext()) {
            String date = dateIterator.next();
            int count = 0;
            for (int i = 0; i < dataTable.length(); i++) {
                JSONObject obj = dataTable.getJSONObject(i);

                if (date.equals(obj.getString("Date"))) {
                    count++;
                }
            }
            datesTxn.put(date, count);

        }

        return new JSONArray().put(datesTxn);
    }

    /**
     * A call to get details of a transaction given its ID
     * 
     * @param txnId Transaction ID
     * @return Transaction Details
     * @throws JSONException handles JSON Exceptions
     * @files TransactionHistory.json
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

        JSONArray transactionArray = new JSONArray(transactionHistory);
        JSONObject transactionObject = new JSONObject();
        for (int i = 0; i < transactionArray.length(); i++) {
            transactionObject = transactionArray.getJSONObject(i);
            if (transactionObject.get("txn").equals(txnId)) {
                transactionObject.remove("essentialShare");

                if (transactionObject.has("amount-received")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                } else if (transactionObject.has("amount-spent")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                } else if (transactionObject.has("amount"))
                    transactionObject.put("amount", transactionObject.getDouble("amount"));
                else {
                    JSONArray tokensArray = (JSONArray) transactionObject.get("tokens");
                    transactionObject.put("amount", tokensArray.length());
                }

                resultArray.put(transactionObject);
            }

        }
        APILogger.info("Transaction Details for : " + transactionObject.toString());
        return resultArray;
    }

    /**
     * A call to get list transactions between two mentioned dates
     * 
     * @param s Start Date
     * @param e End Date
     * @return List of transactions
     * @throws JSONException handles JSON Exceptions
     * @files TransactionHistory.json
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
        JSONObject transactionObject;
        for (int i = 0; i < transArray.length(); i++) {
            transactionObject = transArray.getJSONObject(i);
            String dateFromTxnHistoryString = transactionObject.get("Date").toString();
            if (dateFromTxnHistoryString.length() != 10) {
                Date dateTH = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy").parse(dateFromTxnHistoryString);
                String dateTHS = objSDF.format(dateTH);
                Calendar c = Calendar.getInstance();
                c.setTime(objSDF.parse(dateTHS));
                dateTH = c.getTime();
                if (dateTH.after(startDate) && dateTH.before(endDate)) {
                    transactionObject.remove("essentialShare");

                    if (transactionObject.has("amount-received")) {
                        transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                    } else if (transactionObject.has("amount-spent")) {
                        transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                    } else if (transactionObject.has("amount"))
                        transactionObject.put("amount", transactionObject.getDouble("amount"));
                    else {
                        JSONArray tokensArray = (JSONArray) transactionObject.get("tokens");
                        transactionObject.put("amount", tokensArray.length());
                    }

                    resultArray.put(transactionObject);
                }
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
     * @files TransactionHistory.json
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
        JSONObject transactionObject;
        if (transArray.length() == 0) {
            countResult.put("Message", "No transactions made yet");
            resultArray.put(countResult);
            return resultArray;
        }

        if (n >= transArray.length()) {
            for (int i = transArray.length() - 1; i >= 0; i--) {
                transactionObject = transArray.getJSONObject(i);
                transactionObject.remove("essentialShare");
                if (transactionObject.has("amount-received")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                } else if (transactionObject.has("amount-spent")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                } else if (transactionObject.has("amount"))
                    transactionObject.put("amount", transactionObject.getDouble("amount"));
                else {
                    JSONArray tokensArray = (JSONArray) transactionObject.get("tokens");
                    transactionObject.put("amount", tokensArray.length());
                }
                resultArray.put(transactionObject);
            }
            return resultArray;
        }

        for (int i = 1; i <= n; i++) {

            transactionObject = transArray.getJSONObject(transArray.length() - i);
            transactionObject.remove("essentialShare");
            if (transactionObject.has("amount-received")) {
                transactionObject.put("amount", transactionObject.getDouble("amount-received"));
            } else if (transactionObject.has("amount-spent")) {
                transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
            } else if (transactionObject.has("amount"))
                transactionObject.put("amount", transactionObject.getDouble("amount"));
            else {
                JSONArray tokensArray = (JSONArray) transactionObject.get("tokens");
                transactionObject.put("amount", tokensArray.length());
            }

            resultArray.put(transactionObject);
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
     * @files TransactionHistory.json
     */
    public static JSONArray transactionsByRange(int start, int end) throws JSONException {
        JSONObject countResult = new JSONObject();
        JSONArray resultArray = new JSONArray();
        if (start < 0 || end <= 0) {
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
                JSONObject transactionObject = transArray.getJSONObject(i);
                transactionObject.remove("essentialShare");
                if (transactionObject.has("amount-received")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                } else if (transactionObject.has("amount-spent")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                } else if (transactionObject.has("amount"))
                    transactionObject.put("amount", transactionObject.getDouble("amount"));
                else {
                    JSONArray tokensArray = (JSONArray) transactionObject.get("tokens");
                    transactionObject.put("amount", tokensArray.length());
                }

                resultArray.put(transactionObject);
            }
        } else {
            if (start == end) {
                JSONObject transactionObject = transArray.getJSONObject(start);
                transactionObject.remove("essentialShare");
                if (transactionObject.has("amount-received")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                } else if (transactionObject.has("amount-spent")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                } else if (transactionObject.has("amount"))
                    transactionObject.put("amount", transactionObject.getDouble("amount"));
                else {
                    JSONArray tokensArray = (JSONArray) transactionObject.get("tokens");
                    transactionObject.put("amount", tokensArray.length());
                }

                resultArray.put(transactionObject);
            } else {
                for (int i = start; i <= end; i++) {
                    JSONObject transactionObject = transArray.getJSONObject(i);
                    transactionObject.remove("essentialShare");
                    if (transactionObject.has("amount-received")) {
                        transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                    } else if (transactionObject.has("amount-spent")) {
                        transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                    } else if (transactionObject.has("amount"))
                        transactionObject.put("amount", transactionObject.getDouble("amount"));
                    else {
                        JSONArray tokensArray = (JSONArray) transactionObject.get("tokens");
                        transactionObject.put("amount", tokensArray.length());
                    }
                    resultArray.put(transactionObject);
                }
            }
        }

        return resultArray;
    }

    /**
     * A call to get list transactions with the mentioned comment
     * 
     * @param comment Comment
     * @return List of transactions
     * @throws JSONException handles JSON Exceptions
     * @files TransactionHistory.json
     */
    public static JSONArray transactionsByComment(String comment) throws JSONException {

        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray transArray = new JSONArray(transactionHistory);
        JSONObject transactionObject;
        JSONArray resultArray = new JSONArray();
        for (int i = 0; i < transArray.length(); i++) {
            transactionObject = transArray.getJSONObject(i);

            if (transactionObject.get("comment").equals(comment)) {
                transactionObject.remove("essentialShare");
                if (transactionObject.has("amount-received")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                } else if (transactionObject.has("amount-spent")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                } else if (transactionObject.has("amount"))
                    transactionObject.put("amount", transactionObject.getDouble("amount"));
                else {
                    JSONArray tokensArray = (JSONArray) transactionObject.get("tokens");
                    transactionObject.put("amount", tokensArray.length());
                }
                resultArray.put(transactionObject);
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
     * @files TransactionHistory.json
     */
    public static JSONArray transactionsByDID(String did) throws JSONException {
        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray transArray = new JSONArray(transactionHistory);
        JSONArray resultArray = new JSONArray();
        for (int i = 0; i < transArray.length(); i++) {
            JSONObject transactionObject = transArray.getJSONObject(i);
            transactionObject.remove("essentialShare");

            if (transactionObject.get("senderDID").equals(did) || transactionObject.get("receiverDID").equals(did)) {
                if (transactionObject.has("amount-received")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                } else if (transactionObject.has("amount-spent")) {
                    transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                } else if (transactionObject.has("amount"))
                    transactionObject.put("amount", transactionObject.getDouble("amount"));
                else {
                    JSONArray tokensArray = (JSONArray) transactionObject.get("tokens");
                    transactionObject.put("amount", tokensArray.length());
                }

                resultArray.put(transactionObject);
            }
        }

        return resultArray;
    }
    
    /**
     * A call to generate hashtable in node
     * 
     * @return Message if failed or succeeded
     * @throws JSONException 
     * @throws InterruptedException 
     * @files TokenHashTable.json
     */
    public static JSONArray tokenHashTableGeneration() throws JSONException, InterruptedException {
    	System.out.println("API Handler - generting hash table");
        StringBuilder result = new StringBuilder();
        JSONArray resultArray = new JSONArray();
        JSONObject jsonObject = new JSONObject();
        boolean generationStatus = false;
        File tokenHashTable = new File(DATA_PATH.concat("DataHash"));
        System.out.println("File path is "+ tokenHashTable.toString());
        generationStatus = Functions.generateMultiLoopWithHashMap(tokenHashTable.toString());
        
        if (generationStatus == true && (tokenHashTable.exists() && (tokenHashTable.length() / (1024 * 1024))>0)) {
            jsonObject.put("message", "Token Hash Table Generation successful");
        } else {
            jsonObject.put("message", "Unable to generate Token Hash Table! Try again after sometime.");
        }
        resultArray.put(jsonObject);
        return resultArray;
    }
    
    /**
     * This method is to get the no. of staked tokens
     * 
     * @param 
     * @return no. of staked token 
     * @throws JSONException handles JSON Exceptions
     * @files 
     */    
    public static int stakedTokencount() throws JSONException {
        String stakedtokens = Functions.WALLET_DATA_PATH.concat("Stake/");
        int count =0;
        File stkedtokensfile = new File(stakedtokens);
          File[] filesList = stkedtokensfile.listFiles();
          if (stkedtokensfile.exists()) {
              for ( File file : filesList) {
            	  String sFile = file.getName();
                  String k =  stakedtokens+sFile;
                  String contactsFile = Functions.readFile(k);
                
                  JSONObject js = new JSONObject(contactsFile);
                 
                  JSONObject stakedata = js.getJSONObject("stakeData");
                  String staked_mineid = stakedata.getString("stakedToken");
                  count++;
              }
          }
          return count;
     }
    
    /**
     * This method is to get the withdrawal balance
     * 
     * @param 
     * @return withdrawal balance 
     * @throws JSONException handles JSON Exceptions
     * @files 
     */    
    public static Double getAvailableBalance() throws JSONException, IOException
    {
    	int stakedtokens=APIHandler.stakedTokencount();
    	Double balance=Functions.getBalance();
      
    	Double availablebalance = balance - stakedtokens;
      
    	return availablebalance;
      
    }

}
