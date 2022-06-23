package com.rubix.LevelTwoToken;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.rubix.Resources.Functions.*;

import java.io.File;

import io.ipfs.api.IPFS;

public class LTwoAPIHandler {
    private static final Logger L2Logger = Logger.getLogger(LTwoAPIHandler.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);

   
    public static void enableL2Wallet(String l2TokenName)
    {
        pathSet();

        /* String L2_WALLET_PATH = dirPath + File.separator+"L2_WALLET";

        //L2Logger.info("Rubix Wallet Path "+WALLET_PATH);
        File l2WalletFolder = new File(dirPath + File.separator+"L2_WALLET");

        L2Logger.debug("Checking if L2 Wallet folder Exist/Enabled "+l2WalletFolder.exists());
        if(!l2WalletFolder.exists())
        {
            L2Logger.info("L2 Wallet folder does not Exist/Enabled. Creating L2_WALLET folder");
            l2WalletFolder.mkdirs();
        }

        L2Logger.info("L2 Wallet folder Exist/Enabled"); */

        String WALLET_PATH = dirPath + File.separator+"Wallet";

        File l2TokenFolder = new File(WALLET_PATH+File.separator+l2TokenName.concat("WALLET") );
        L2Logger.debug("Checking if L2 token Wallet l2 folder Exist/Enabled "+l2TokenFolder.exists());
        if(!l2TokenFolder.exists())
        {
            L2Logger.info("L2 Wallet folder does not Exist/Enabled. Creating L2_WALLET folder");
            l2TokenFolder.mkdirs();
        }

        L2Logger.info("L2 Wallet folder Exist/Enabled");

        JSONArray initiate = new JSONArray();
        File l2TOKENSFOLDER = new File(l2TokenFolder+File.separator+l2TokenName+"TOKENS");
        File l2TOKENCHAINFOLDER = new File(l2TokenFolder+File.separator+l2TokenName+"TOKENCHAIN");
        File l2TOKENTransactionHistoryFile = new File(WALLET_DATA_PATH + l2TokenName.concat("TransactionHistory.json"));
        File l2BANKFile = new File(l2TokenFolder+File.separator+l2TokenName.concat("BNK.json"));

        if(!l2TOKENSFOLDER.exists())
        {
            L2Logger.info(l2TokenName.concat("TOKENS")+" folder not Exist/Enabled. Creating Folder");            
            l2TOKENSFOLDER.mkdirs();
        }
        if(!l2TOKENCHAINFOLDER.exists())
        {
            L2Logger.info(l2TokenName.concat("TOKENCHAIN")+" folder not Exist/Enabled. Creating Folder");            
            l2TOKENCHAINFOLDER.mkdirs();
        }
        if(!l2TOKENTransactionHistoryFile.exists())
        {
            L2Logger.info(l2TokenName.concat("TransactionHistory.json")+" File not Exist/Enabled. Creating file");            
            writeToFile(WALLET_DATA_PATH + l2TokenName.concat("TransactionHistory.json"), initiate.toString(), Boolean.valueOf(false)); 
        }
        if(!l2BANKFile.exists())
        {
            L2Logger.info(l2TokenName.concat("BNK.json")+" File not Exist/Enabled. Creating file");            
            writeToFile(l2TokenFolder+File.separator+l2TokenName.concat("BNK.json"), initiate.toString(), Boolean.valueOf(false)); 
        }
    }

    

    public static String checkL2TokenWalletFolders(String l2TokenName)
    {
        String WALLET_PATH = dirPath + File.separator+"Wallet";
        String L2_WALLET_PATH = WALLET_PATH + File.separator+l2TokenName.concat("WALLET");
        
        JSONObject result = new JSONObject();

        File l2WALLETFOLDER = new File(L2_WALLET_PATH);

        File l2TOKENSFOLDER = new File(L2_WALLET_PATH+File.separator+l2TokenName+"TOKENS");
        File l2TOKENCHAINFOLDER = new File(L2_WALLET_PATH+File.separator+l2TokenName+"TOKENCHAIN");
        File l2TOKENTransactionHistoryFile = new File(WALLET_DATA_PATH + l2TokenName.concat("TransactionHistory.json"));
        File l2BANKFile = new File(L2_WALLET_PATH+File.separator+l2TokenName.concat("BNK.json"));

        try {
            L2Logger.info("checking if "+l2TokenName.concat("WALLET")+" folder Exist/Enabled");
            if (!l2WALLETFOLDER.exists()) {
                L2Logger.info(l2TokenName.concat("WALLET")+" folder not Exist/Enabled");
                result.put("Status", "Failed");
                result.put("message",
                        "L2 Wallet Folder " + l2TokenName.concat("WALLET") + " does not exist/not enabled");

                return result.toString();
            }

            L2Logger.info("checking if "+l2TokenName.concat("TOKENS")+" folder Exist/Enabled");
            if (!l2TOKENSFOLDER.exists()) {
                L2Logger.info(l2TokenName.concat("TOKENS")+" folder not Exist/Enabled");
                result.put("Status", "Failed");
                result.put("message",
                        "L2 Tokens Folder " + l2TokenName.concat("TOKENS") + " does not exist/not enabled");

                return result.toString();
            }

            L2Logger.info("checking if "+l2TokenName.concat("TOKENCHAIN")+" folder Exist/Enabled");
            if (!l2TOKENCHAINFOLDER.exists()) {
                L2Logger.info(l2TokenName.concat("TOKENCHAIN")+" folder not Exist/Enabled");
                result.put("Status", "Failed");
                result.put("message",
                        "L2 TokenChain Folder " + l2TokenName.concat("TOKENCHAIN") + " does not exist/not enabled");

                return result.toString();
            }

            L2Logger.info("checking if "+l2TokenName.concat("TransactionHistory.json")+" file Exist/Enabled");
            if (!l2TOKENTransactionHistoryFile.exists()) {
                L2Logger.info(l2TokenName.concat("TransactionHistory.json")+" folder not Exist/Enabled");
                result.put("Status", "Failed");
                result.put("message",
                        "L2 Token TransactionHistory File " + l2TokenName.concat("TransactionHistory.json") + " does not exist/not enabled");

                return result.toString();
            }

            L2Logger.info("checking if "+l2TokenName.concat("BNK.json")+" file Exist/Enabled");

            if (!l2BANKFile.exists()) {
                L2Logger.info(l2TokenName.concat("BNK.json")+" folder not Exist/Enabled");
                result.put("Status", "Failed");
                result.put("message",
                        "L2 Token BANK File " + l2TokenName.concat("BNK.json") + " does not exist/not enabled");

                return result.toString();
            }

            L2Logger.info("All L2 Token "+l2TokenName+" Wallet, files and folder are exist/enabled");
            result.put("Status", "Success");
            result.put("message", "L2 Token "+l2TokenName+" Wallet folder enabled");
        } catch (JSONException e) {
            // TODO: handle exception
        }
        return result.toString();
    }
    
}
