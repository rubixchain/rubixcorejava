import org.iq80.leveldb.DB;
import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

import java.lang.StackWalker.Option;
import java.util.function.Function;

import javax.json.JsonObject;

import java.io.File;
import java.io.IOException;

import static com.rubix.Resources.Functions.*;

import org.iq80.leveldb.Options;
import org.json.JSONException;
import org.json.JSONObject;

public class Database {

    public static DB transactionHistory=null;
    public static DB essentialShare=null;
    public static DB levelDb;

    /* public static DB createDB(String dbName) throws IOException {
        pathSet();

        Options options = new Options();
        levelDb = factory.open(new File(dbName), options);
        return levelDb;
    } */
    public static void createOrOpenDB() throws IOException
    {
        pathSet();

        Options options = new Options();
        transactionHistory = factory.open(new File(WALLET_DATA_PATH+transactionHistory), options);
        essentialShare=factory.open(new File(WALLET_DATA_PATH+essentialShare), options);
        
    }

    public static void putDataTransactionHistory(String key,String value)
    {
        transactionHistory.put(key.getBytes(),value.getBytes());
    }

    public static void putDataEssentialShare(String key,String value)
    {
        essentialShare.put(key.getBytes(),value.getBytes());
    }

    

    public static JSONObject getData(String key, DB database) throws JSONException
    {
        String value = new String (database.get(key.getBytes()));
        JSONObject jsonValue = new JSONObject(value);
        return jsonValue;
    }

    public static String getDatabyTxnId(String txnId)
    {

        String resultTxn=null,txnHis,essShr;
        JSONObject resultTxnObj= new JSONObject();

        try{
        txnHis=new String(transactionHistory.get(txnId.getBytes()));
        essShr=new String(essentialShare.get(txnId.getBytes()));
        
        JSONObject tempTxnhis=new JSONObject(txnHis);
        JSONObject tempEssShr=new JSONObject(essShr);

        

        resultTxnObj.put("senderDID", tempTxnhis.get("senderDID"));
        resultTxnObj.put("role", tempTxnhis.get("role"));
        resultTxnObj.put("totalTime", tempTxnhis.get("totalTime"));
        resultTxnObj.put("quorumList", tempTxnhis.get("quorumList"));
        resultTxnObj.put("tokens", tempTxnhis.get("tokens"));
        resultTxnObj.put("comment", tempTxnhis.get("comment"));
        resultTxnObj.put("txn", tempTxnhis.get("txn"));
        resultTxnObj.put("essentialShare",tempEssShr.get("essentailShare"));
        resultTxnObj.put("receiverDID", tempTxnhis.get("receiverDID"));
        resultTxnObj.put("Date", tempTxnhis.get("Date"));

        resultTxn= resultTxnObj.toString();
        }
        catch(NullPointerException e)
        {
            return "No Transaction Found / Please check TransactionID";
        }
        catch(JSONException e)
        {
            e.printStackTrace();
        }
        return resultTxn;

    }

}