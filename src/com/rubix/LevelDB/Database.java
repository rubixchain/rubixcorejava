package com.rubix.LevelDb;

import org.iq80.leveldb.DB;
import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

import java.lang.StackWalker.Option;
import java.util.function.Function;

import javax.json.JsonObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import static com.rubix.Resources.Functions.*;

import org.iq80.leveldb.Options;
import org.json.simple.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;

public class DataBase {

    public static DB transactionHistory = null;
    public static DB essentialShare = null;
    public static DB levelDb;

    /*
     * public static DB createDB(String dbName) throws IOException {
     * pathSet();
     * 
     * Options options = new Options();
     * levelDb = factory.open(new File(dbName), options);
     * return levelDb;
     * }
     */
    public static void createOrOpenDB(){
        pathSet();

        try {
            Options options = new Options();
            transactionHistory = factory.open(new File(WALLET_DATA_PATH + transactionHistory), options);
            essentialShare = factory.open(new File(WALLET_DATA_PATH + essentialShare), options);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void putDataTransactionHistory(String key, String value) {
        transactionHistory.put(key.getBytes(), value.getBytes());
    }

    public static void putDataEssentialShare(String key, String value) {
        essentialShare.put(key.getBytes(), value.getBytes());
    }

    public static JSONObject getData(String key, DB database) throws JSONException {
        String value = new String(database.get(key.getBytes()));
        JSONObject jsonValue = new JSONObject(value);
        return jsonValue;
    }

    public static String getDatabyTxnId(String txnId) {

        String resultTxn = null, txnHis, essShr;
        JSONObject resultTxnObj = new JSONObject();

        try {
            txnHis = new String(transactionHistory.get(txnId.getBytes()));
            essShr = new String(essentialShare.get(txnId.getBytes()));

            JSONObject tempTxnhis = new JSONObject(txnHis);
            JSONObject tempEssShr = new JSONObject(essShr);

            resultTxnObj.put("senderDID", tempTxnhis.get("senderDID"));
            resultTxnObj.put("role", tempTxnhis.get("role"));
            resultTxnObj.put("totalTime", tempTxnhis.get("totalTime"));
            resultTxnObj.put("quorumList", tempTxnhis.get("quorumList"));
            resultTxnObj.put("tokens", tempTxnhis.get("tokens"));
            resultTxnObj.put("comment", tempTxnhis.get("comment"));
            resultTxnObj.put("txn", tempTxnhis.get("txn"));
            resultTxnObj.put("essentialShare", tempEssShr.get("essentailShare"));
            resultTxnObj.put("receiverDID", tempTxnhis.get("receiverDID"));
            resultTxnObj.put("Date", tempTxnhis.get("Date"));

            resultTxn = resultTxnObj.toString();
        } catch (NullPointerException e) {
            return "No Transaction Found / Please check TransactionID";
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return resultTxn;

    }

    public static void pushTxnFiletoDB() {
        FileReader fr;
        try {
            fr = new FileReader(WALLET_DATA_PATH + "TransactionHistory.json");
            JSONParser jsonParser = new JSONParser();
            JSONArray jsonArray = (JSONArray) jsonParser.parse(fr);
            for (Object o : jsonArray) {
                org.json.simple.JSONObject obj = (org.json.simple.JSONObject) o;
                org.json.simple.JSONObject value1 = new org.json.simple.JSONObject();
                org.json.simple.JSONObject value2 = new org.json.simple.JSONObject();
                value1.put("senderDID", obj.get("senderDID"));
                value1.put("role", obj.get("role"));
                value1.put("totalTime", obj.get("totalTime"));
                value1.put("quorumList", obj.get("quorumList"));
                value1.put("tokens", obj.get("tokens"));
                value1.put("comment", obj.get("comment"));
                value1.put("txn", obj.get("txn"));
                value1.put("receiverDID", obj.get("receiverDID"));
                value1.put("Date", obj.get("Date"));

                value2.put("essentialShare", obj.get("essentialShare"));

                putDataTransactionHistory(obj.get("txn").toString(), value1.toString());

                putDataEssentialShare(obj.get("txn").toString(), value2.toString());

                fr.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
