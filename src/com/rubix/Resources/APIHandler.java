package com.rubix.Resources;

import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Mining.ProofCredits;
import com.rubix.TokenTransfer.TokenSender;
import io.ipfs.api.*;
import org.apache.log4j.*;
import org.json.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;

public class APIHandler {
    private static final Logger APILogger = Logger.getLogger(APIHandler.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    private static final Logger eventLogger = Logger.getLogger("eventLogger");

    /**
     * Arrange the nodes in the quorumlist file
     * @return Message if success or failure of arrangement
     * @throws JSONException handles JSON Exceptions
     * @throws NoSuchAlgorithmException handles Invalid Algorithms Exceptions
     * @throws IOException handles IO Exceptions
     */


    public static JSONObject sortType2Quorum(){
        Functions.pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        JSONObject sendMessage = new JSONObject();
        JSONArray quorumArray = new JSONArray(readFile(DATA_PATH + "quorumlist.json"));
        APILogger.debug("Before Sorting: " + quorumArray);
        int code = 0;
        try {
            code = arrangeQuorum(quorumArray, SEND_PORT+11, 0);
        } catch (IOException e) {
            APILogger.debug("Credits failed");
        }

        if(code == 200) {
            quorumArray = new JSONArray(readFile(DATA_PATH + "quorumlist.json"));
            APILogger.debug("After Sorting: " + quorumArray);
            sendMessage.put("status", "Success");
            sendMessage.put("message", "Sorted");
        }
        else if(code == 401){
            APILogger.debug("Could not collect all(min. 21) credits");
            sendMessage.put("status", "Failed");
            sendMessage.put("message", "");
        }
        else if(code == 402){
            APILogger.debug("7 alpha node credits not summing up to requested amount");
            sendMessage.put("status", "Failed");
            sendMessage.put("message", "");
        }

        return sendMessage;

    }
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
        if(!isObjectValid)
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
        sendMessage =  TokenSender.Send(dataObject.toString(), ipfs, SEND_PORT);

        APILogger.info(sendMessage);
        return sendMessage;
    }

    /**
     * An API call to mine tokens
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
        if(!isObjectValid)
            networkInfo();

        JSONObject sendMessage = new JSONObject();
        JSONObject detailsObject = new JSONObject();
        detailsObject.put("receiverDidIpfsHash", senDID);
        detailsObject.put("pvt", DATA_PATH + senDID + "/PrivateShare.png");
        detailsObject.put("type", type);
        sendMessage =  ProofCredits.create(detailsObject.toString(), ipfs);
        APILogger.info(sendMessage);
        return sendMessage;
    }

    /**
     * Utility function to add token ownership
     */
    public static JSONObject addOwnership() throws IOException {
        pathSet();
        String wholeToken = readFile(PAYMENTS_PATH.concat("BNK00.json"));
        JSONArray wholeTokensArray = new JSONArray(wholeToken);
        String partsToken;
        JSONArray partsTokenArray = new JSONArray();
        boolean parts = false;
        if(new File(PAYMENTS_PATH.concat("PartsToken.json")).exists()) {
            partsToken = readFile(PAYMENTS_PATH.concat("PartsToken.json"));
            partsTokenArray = new JSONArray(partsToken);
            parts=true;
        }
        JSONArray allTokens = new JSONArray();
        for(int i = 0; i < wholeTokensArray.length(); i++)
            allTokens.put(wholeTokensArray.getString(i));

        if(parts) {
            for (int i = 0; i < partsTokenArray.length(); i++)
                allTokens.put(partsTokenArray.getString(i));
        }

        String didFile = readFile(DATA_PATH.concat("DID.json"));
        JSONArray didArray = new JSONArray(didFile);
        String did = didArray.getJSONObject(0).getString("didHash");

        for (int i = 0; i < allTokens.length(); i++) {
            String tokens = allTokens.getString(i);
            String tokenChain = readFile(TOKENCHAIN_PATH.concat(tokens).concat(".json"));
            JSONArray tokenChainArray = new JSONArray(tokenChain);
            JSONObject lastObject = tokenChainArray.getJSONObject(tokenChainArray.length()-1);
            if(!lastObject.has("owner")) {
                String hashString = tokens.concat(did);
                String hashForPositions = calculateHash(hashString, "SHA3-256");

                BufferedImage pvt = ImageIO.read(new File(DATA_PATH.concat(did).concat("/PrivateShare.png")));
                String firstPrivate = PropImage.img2bin(pvt);
                int[] privateIntegerArray1 = strToIntArray(firstPrivate);
                String privateBinary = Functions.intArrayToStr(privateIntegerArray1);
                String positions = "";
                for (int j = 0; j < privateIntegerArray1.length; j += 49152) {
                    positions += privateBinary.charAt(j);
                }
                String ownerIdentity = hashForPositions.concat(positions);
                String ownerIdentityHash = calculateHash(ownerIdentity, "SHA3-256");
                lastObject.put("owner", ownerIdentityHash);
            }
            tokenChainArray.remove(tokenChainArray.length()-1);
            tokenChainArray.put(lastObject);
            writeToFile(TOKENCHAIN_PATH.concat(tokens).concat(".json"), tokenChainArray.toString(), false);


        }

        JSONObject sendMessage = new JSONObject();
        sendMessage.put("did", did);
        sendMessage.put("tid", "null");
        sendMessage.put("status", "Success");
        sendMessage.put("message", "Ownership Added");
        return sendMessage;

    }

    /**
     * A method to add and host your DID ans Public share to ipfs
     * @files DID.json, DataTable.json, DID.png, PublicShare.png
     */
    public static void addPublicData(){
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



    /**
     * Method to query the credits information
     * @files QuorumSignedTransactions.json, MinedCreditsHistory.json
     */
    public static JSONObject creditsInfo(){
        String qstFile = WALLET_DATA_PATH.concat("QuorumSignedTransactions.json");
        String mineFile = WALLET_DATA_PATH.concat("MinedCreditsHistory.json");

        File quorumFile = new File(qstFile);
        File minedFile = new File(mineFile);
        JSONObject returnObject = new JSONObject();
        int spentCredits = 0;
        int unspentCredits = 0;
        try {
            if(quorumFile.exists()){
                String qFile = readFile(qstFile);
                JSONArray qArray = new JSONArray(qFile);
                unspentCredits = qArray.length();
            }
            if(minedFile.exists()){
                String mFile = readFile(mineFile);
                JSONArray mArray = new JSONArray(mFile);
                spentCredits = mArray.length();
            }
    
            
            returnObject.put("spentCredits",spentCredits);
            returnObject.put("unspentCredits",unspentCredits);
        } catch (JSONException e) {
            //TODO: handle exception
        }

        return returnObject;
    }

    /**
     * A call to close all open IPFS streams
     */
    public static void closeStreams(){
        executeIPFSCommands("ipfs p2p close --all");
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


    public static JSONObject syncNetworkNodes() throws JSONException, IOException {
        String dataTable = readFile(DATA_PATH + "DataTable.json");
        JSONArray dataArray = new JSONArray(dataTable);

        for (int i = 0; i < dataArray.length(); i++)
            nodeData(dataArray.getJSONObject(i).getString("didHash"), dataArray.getJSONObject(i).getString("walletHash"), ipfs);

        return new JSONObject("{\"message\":\"Synced all nodes\"}");
    }

    /**
     * A call to get the account information
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
        accountDetails.put("totalTxn", txnAsSender+txnAsReceiver);

        resultArray.put(accountDetails);
        return resultArray;
    }

    /**
     * A call to list out number of transactions made per day
     * @return List of transactions committed on every date
     * @throws JSONException handles JSON Exceptions
     * @files TransactionHistory.json
     */
    public static JSONArray txnPerDay() throws JSONException {
        String dataTableFileContent = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray dataTable = new JSONArray(dataTableFileContent);
        HashSet<String> dateSet = new HashSet<>();
        for(int i = 0; i < dataTable.length(); i++)
            dateSet.add(dataTable.getJSONObject(i).getString("Date"));

        JSONObject datesTxn = new JSONObject();
        Iterator<String> dateIterator = dateSet.iterator();
        while (dateIterator.hasNext()){
            String date = dateIterator.next();
            int count = 0;
            for(int i = 0; i < dataTable.length(); i++){
                JSONObject obj = dataTable.getJSONObject(i);

                if(date.equals(obj.getString("Date"))){
                    count++;
                }
            }
            datesTxn.put(date, count);

        }

        return new JSONArray().put(datesTxn);
    }

    /**
     * A call to get details of a transaction given its ID
     * @param txnId Transaction ID
     * @return Transaction Details
     * @throws JSONException handles JSON Exceptions
     * @files TransactionHistory.json
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

        JSONArray transactionArray = new JSONArray(transactionHistory);
        JSONObject transactionObject = new JSONObject();
        for (int i = 0; i < transactionArray.length(); i++) {
            transactionObject = transactionArray.getJSONObject(i);
            if (transactionObject.get("txn").equals(txnId)) {
                transactionObject.remove("essentialShare");

                if(transactionObject.has("amount-received")){
                    transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                }else if(transactionObject.has("amount-spent")){
                    transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                }
                else if(transactionObject.has("amount"))
                    transactionObject.put("amount", transactionObject.getDouble("amount"));
                else{
                    JSONArray tokensArray = (JSONArray)transactionObject.get("tokens");
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
     * @param s Start Date
     * @param e End Date
     * @return List of transactions
     * @throws JSONException handles JSON Exceptions
     * @files TransactionHistory.json
     */
    public static JSONArray transactionsByDate(String s, String e) throws JSONException, ParseException {
        JSONArray resultArray = new JSONArray();
        String strDateFormat = "yyyy-MMM-dd HH:mm:ss"; //Date format is Specified
        SimpleDateFormat objSDF = new SimpleDateFormat(strDateFormat);
        Date date1=new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy").parse(s);
        String startDateString= objSDF.format(date1);
        Date date2=new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy").parse(e);
        String endDateString= objSDF.format(date2);
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
        if (transArray.length() == 0){
            countResult.put("Message", "No Transactions made yet");
            resultArray.put(countResult);
            return resultArray;
        }
        JSONObject transactionObject;
        for (int i=0;i<transArray.length();i++)
        {
            transactionObject = transArray.getJSONObject(i);
            String dateFromTxnHistoryString = transactionObject.get("Date").toString();
            if(dateFromTxnHistoryString.length() != 10) {
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
        if (transArray.length() == 0){
            countResult.put("Message", "No transactions made yet");
            resultArray.put(countResult);
            return resultArray;
        }

        if (n >= transArray.length()) {
            for (int i = transArray.length()-1; i>=0; i--) {
                transactionObject = transArray.getJSONObject(i);
                transactionObject.remove("essentialShare");
                if(transactionObject.has("amount-received")){
                    transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                }else if(transactionObject.has("amount-spent")){
                    transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                }
                else if(transactionObject.has("amount"))
                    transactionObject.put("amount", transactionObject.getDouble("amount"));
                else{
                    JSONArray tokensArray = (JSONArray)transactionObject.get("tokens");
                    transactionObject.put("amount", tokensArray.length());
                }
                resultArray.put(transactionObject);
            }
            return resultArray;
        }

        for( int i = 1; i <= n; i++) {

            transactionObject = transArray.getJSONObject(transArray.length() - i);
            transactionObject.remove("essentialShare");
            if(transactionObject.has("amount-received")){
                transactionObject.put("amount", transactionObject.getDouble("amount-received"));
            }else if(transactionObject.has("amount-spent")){
                transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
            }
            else if(transactionObject.has("amount"))
                transactionObject.put("amount", transactionObject.getDouble("amount"));
            else{
                JSONArray tokensArray = (JSONArray)transactionObject.get("tokens");
                transactionObject.put("amount", tokensArray.length());
            }

            resultArray.put(transactionObject);
        }
        return resultArray;
    }

    /**
     * A call to get list transactions within a range
     * @param start start index
     * @param end end index
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

        if(start > end){
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
        if (transArray.length() == 0){
            resultArray.put(countResult);
            return resultArray;
        }


        if(!(end < transArray.length())) {
            for (int i = start; i < transArray.length(); i++) {
                JSONObject transactionObject = transArray.getJSONObject(i);
                transactionObject.remove("essentialShare");
                if(transactionObject.has("amount-received")){
                    transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                }else if(transactionObject.has("amount-spent")){
                    transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                }
                else if(transactionObject.has("amount"))
                    transactionObject.put("amount", transactionObject.getDouble("amount"));
                else{
                    JSONArray tokensArray = (JSONArray)transactionObject.get("tokens");
                    transactionObject.put("amount", tokensArray.length());
                }

                resultArray.put(transactionObject);
            }
        }else{
            if(start == end){
                JSONObject transactionObject = transArray.getJSONObject(start);
                transactionObject.remove("essentialShare");
                if(transactionObject.has("amount-received")){
                    transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                }else if(transactionObject.has("amount-spent")){
                    transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                }
                else if(transactionObject.has("amount"))
                    transactionObject.put("amount", transactionObject.getDouble("amount"));
                else{
                    JSONArray tokensArray = (JSONArray)transactionObject.get("tokens");
                    transactionObject.put("amount", tokensArray.length());
                }

                resultArray.put(transactionObject);
            }
            else {
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
                if(transactionObject.has("amount-received")){
                    transactionObject.put("amount", transactionObject.getDouble("amount-received"));
                }else if(transactionObject.has("amount-spent")){
                    transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                }
                else if(transactionObject.has("amount"))
                    transactionObject.put("amount", transactionObject.getDouble("amount"));
                else{
                    JSONArray tokensArray = (JSONArray)transactionObject.get("tokens");
                    transactionObject.put("amount", tokensArray.length());
                }
                resultArray.put(transactionObject);
            }

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

}
