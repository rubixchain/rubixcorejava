package com.rubix.NFTResources;

import static com.rubix.Resources.Functions.*;

import java.io.File;
import java.io.IOException;
import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.rubix.KeyPairGen.EcDSAKeyGen;
import com.rubix.Resources.IPFSNetwork;

import static com.rubix.NFTResources.NFTFunctions.*;
import static com.rubix.Resources.APIHandler.*;
import static com.rubix.NFT.NftBuyer.*;
import static com.rubix.NFTResources.EnableNft.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.*;

import io.ipfs.api.IPFS;

public class NFTAPIHandler {

    private static final Logger APILogger = Logger.getLogger(NFTAPIHandler.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);

    public static JSONObject sendNft(String data) {
        pathSet();
        nftPathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        // networkInfo();
        /*
         * String sellerPeerID = getPeerID(DATA_PATH + "DID.json");
         * String sellerDID = getValues(DATA_PATH + "DID.json", "didHash", "peerid",
         * sellerPeerID);
         */
        JSONObject sendMessage = new JSONObject();
        try {
            JSONObject dataObject = new JSONObject(data);
            String buyerDID = dataObject.getString("buyerDidIpfsHash");
            String nftTokenIpfsHash = dataObject.getString("nftToken");
            String sellerDID = dataObject.getString("sellerDidIpfsHash");

            boolean isObjectValid = false;

            String datatable = readFile(DATA_PATH + "DataTable.json");
            JSONArray dataTable = new JSONArray(datatable);
            for (int i = 0; i < dataTable.length(); i++) {
                JSONObject dataTableObject = dataTable.getJSONObject(i);
                if (dataTableObject.getString("didHash").equals(sellerDID)) {
                    isObjectValid = true;
                }
            }

            if (!isObjectValid) {
                networkInfo();
            }

            if (sellerDID.length() != 46) {
                sendMessage.put("did", buyerDID);
                sendMessage.put("tid", "null");
                sendMessage.put("status", "Failed");
                sendMessage.put("message", "Invalid Buyer Did Entered");
                sendMessage.put("comment", "");
                sendMessage.put("nfttoken", nftTokenIpfsHash);
                return sendMessage;
            }
            if (nftTokenIpfsHash.length() != 46) {
                sendMessage.put("did", buyerDID);
                sendMessage.put("tid", "null");
                sendMessage.put("status", "Failed");
                sendMessage.put("message", "Invalid NFT Token Entered");
                sendMessage.put("comment", "");
                sendMessage.put("nfttoken", nftTokenIpfsHash);
                return sendMessage;
            }
            // dataObject.put("pvt", DATA_PATH + sellerDID + "/PrivateShare.png");
            sendMessage = send(dataObject.toString(), ipfs, BUYER_PORT);
            APILogger.info(sendMessage);
        } catch (JSONException e) {
            // TODO: handle exception
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return sendMessage;
    }

    public static String generateRacToken(String data) {
        pathSet();
        String tokens = null;
        JSONObject response = new JSONObject();
        try {
            JSONObject apiData = new JSONObject(data);
            int type = apiData.getInt("racType");
            switch (type) {
                case 1:
                    JSONObject temp1 = new JSONObject();
                    JSONArray tempArray1 = new JSONArray();
                    temp1.put("Status", "Failed");
                    temp1.put("Message", "Type 1 depricated. Please use RAC Type 2 to mint NFT tokens");
                    temp1.put("Tokens", tempArray1);
                    tokens = temp1.toString();
                    break;
                case 2:
                    tokens = createNftToken(data);
                    break;
                default:
                    JSONObject temp = new JSONObject();
                    JSONArray tempArray = new JSONArray();
                    temp.put("Status", "Failed");
                    temp.put("Message", "Wrong type selected");
                    temp.put("Tokens", tempArray);
                    tokens = temp.toString();
                    break;
            }

            JSONObject tokenCreationResponse = new JSONObject(tokens);

            if (tokenCreationResponse.has("Status")) {
                if (tokenCreationResponse.getString("Status").equals("Failed")) {
                    response.put("status", "false");
                    response.put("message", tokenCreationResponse.getString("Message"));
                    response.put("tokens", tokenCreationResponse.getJSONArray("Tokens"));
                }

                if (tokenCreationResponse.getString("Status").equals("Success")) {
                    response.put("status", "true");
                    response.put("message", tokenCreationResponse.getString("Message"));
                    response.put("tokens", tokenCreationResponse.getJSONArray("Tokens"));
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return response.toString();

    }

    /**
     * A call to get details of a transaction given its ID
     * 
     * @param txnId Transaction ID
     * @return Transaction Details
     * @throws JSONException handles JSON Exceptions
     * @files TransactionHistory.json
     */
    public static JSONArray nftTransactionDetails(String txnId) {
        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        String nftTransactionHistory = readFile(WALLET_DATA_PATH + "nftTransactionHistory.json");
        JSONObject countResult = new JSONObject();
        JSONArray resultArray = new JSONArray();
        JSONObject nftTransactionObject = new JSONObject();
        try {
            if (transactionHistory.length() == 0 || nftTransactionHistory.length() == 0) {
                countResult.put("Message", "No transactions found");
                resultArray.put(countResult);
                return resultArray;
            }

            JSONArray transactionArray = new JSONArray(transactionHistory);
            JSONArray nftTransactioArray = new JSONArray(nftTransactionHistory);

            JSONObject transactionObject = new JSONObject();

            for (int i = 0; i < transactionArray.length(); i++) {
                if (transactionArray.getJSONObject(i).getString("txn").equals(txnId)) {
                    transactionObject = transactionArray.getJSONObject(i);
                }
            }

            for (int i = 0; i < nftTransactioArray.length(); i++) {
                nftTransactionObject = nftTransactioArray.getJSONObject(i);
                if (nftTransactionObject.get("txn").equals(txnId)) {
                    nftTransactionObject.remove("essentialShare");

                    if (nftTransactionObject.has("quorumList")) {
                        nftTransactionObject.remove("quorumList");
                    }

                    if (transactionObject.has("amount-received")) {
                        nftTransactionObject.put("amount", transactionObject.getDouble("amount-received"));
                    } else if (transactionObject.has("amount-spent")) {
                        nftTransactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                    } else if (transactionObject.has("amount"))
                        nftTransactionObject.put("amount", transactionObject.getDouble("amount"));
                    else {
                        JSONArray tokensArray = (JSONArray) transactionObject.get("tokens");
                        nftTransactionObject.put("amount", tokensArray.length());
                    }
                    resultArray.put(nftTransactionObject);
                }

            }
        } catch (JSONException e) {
            // TODO: handle exception
        }
        APILogger.info("Transaction Details for : " + nftTransactionObject.toString());
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
    public static JSONArray nftTransactionsByDate(String s, String e) {
        JSONArray resultArray = new JSONArray();
        String strDateFormat = "yyyy-MMM-dd HH:mm:ss"; // Date format is Specified
        SimpleDateFormat objSDF = new SimpleDateFormat(strDateFormat);
        try {
            Date date1 = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy").parse(s);
            String startDateString = objSDF.format(date1);
            Date date2 = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy").parse(e);
            String endDateString = objSDF.format(date2);
            JSONObject countResult = new JSONObject();
            Date startDate = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss").parse(startDateString);
            Date endDate = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss").parse(endDateString);
            File fileCheck1 = new File(WALLET_DATA_PATH + "nftTransactionHistory.json");
            if (!fileCheck1.exists()) {
                countResult.put("Message", "File not found");
                resultArray.put(countResult);
                return resultArray;
            }
            String nftTransactionHistory = readFile(WALLET_DATA_PATH + "nftTransactionHistory.json");
            JSONArray transArray = new JSONArray(nftTransactionHistory);
            if (transArray.length() == 0) {
                countResult.put("Message", "No Transactions made yet");
                resultArray.put(countResult);
                return resultArray;
            }
            JSONObject nftTransactionObject;
            for (int i = 0; i < transArray.length(); i++) {
                nftTransactionObject = transArray.getJSONObject(i);
                String dateFromTxnHistoryString = nftTransactionObject.get("Date").toString();
                if (dateFromTxnHistoryString.length() != 10) {
                    Date dateTH = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy").parse(dateFromTxnHistoryString);
                    String dateTHS = objSDF.format(dateTH);
                    Calendar c = Calendar.getInstance();
                    c.setTime(objSDF.parse(dateTHS));
                    dateTH = c.getTime();
                    if (dateTH.after(startDate) && dateTH.before(endDate)) {
                        nftTransactionObject.remove("essentialShare");

                        /*
                         * if (transactionObject.has("amount-received")) {
                         * transactionObject.put("amount",
                         * transactionObject.getDouble("amount-received"));
                         * } else if (transactionObject.has("amount-spent")) {
                         * transactionObject.put("amount", transactionObject.getDouble("amount-spent"));
                         * } else if (transactionObject.has("amount"))
                         * transactionObject.put("amount", transactionObject.getDouble("amount"));
                         * else {
                         * JSONArray tokensArray = (JSONArray) transactionObject.get("tokens");
                         * transactionObject.put("amount", tokensArray.length());
                         * }
                         */

                        if (nftTransactionObject.has("quorumList")) {
                            nftTransactionObject.remove("quorumList");
                        }

                        resultArray.put(nftTransactionObject);
                    }
                }

            }
        } catch (Exception ex) {
            // TODO: handle exception
        }

        return resultArray;
    }

    public static String generateCryptoKeys(String password, String keyType, int returnKey) {
        String result = null;
        if (keyType.equals("ECDSA")) {
            if (returnKey == 0) {
                EcDSAKeyGen.generateKeyPair(password);
                if (checkKeyFiles()) {
                    JSONObject temp = new JSONObject();
                    try {
                        temp.put("privateKey", "ECDSA key saved to Rubix/DATA/privatekey.pem");
                        temp.put("publicKey", "ECDSA key saved to Rubix/DATA/publickey.pub");
                        temp.put("publicKeyIpfsHash", "saved to Rubix/DATA/PulicKeyIpfsHash");
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    result = temp.toString();

                }
            } else {
                result = EcDSAKeyGen.genAndRetKey(password);
            }
        }
        return result;

    }



}
