package com.rubix.NFT;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

import static com.rubix.Resources.IPFSNetwork.*;
import static com.rubix.Resources.Functions.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONObject;

import io.ipfs.api.IPFS;

public class Burn {

    public static Logger burnLogger = Logger.getLogger(NftSeller.class);

    private static final JSONObject APIResponse = new JSONObject();

    private static final IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);

    public static void burnNFT()
    {
        ServerSocket ss = null;
        Socket sk = null;
        int BURN_PORT=15060;
        String burnNodePeerId = getPeerID(DATA_PATH + "DID.json");
        try {
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
            listen(burnNodePeerId + "NFT_BURN", BURN_PORT);
            ss = new ServerSocket(BURN_PORT);
            burnLogger.debug("Burn Node Listening on " + BURN_PORT + "with app name " + burnNodePeerId + "NFT_BURN");
            sk = ss.accept();

            BufferedReader input = new BufferedReader(new InputStreamReader(sk.getInputStream()));
            PrintStream output = new PrintStream(sk.getOutputStream());
            long startTime = System.currentTimeMillis();

            
        } catch (Exception e) {
            //TODO: handle exception
        }
    }
    
}
