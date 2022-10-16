package com.rubix.TokenTransfer.TransferPledge;

import com.rubix.Resources.Functions;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.*;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;

public class Pledger1 implements Runnable {
    public static Logger PledgerLogger = Logger.getLogger(Pledger1.class);
    int port;
    IPFS ipfs;

    String senderPID;

    public Pledger1() {

        this.port = 15071;
        this.ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);

    }

    @Override
    public void run() {
        while (true) {
            ServerSocket serverSocket = null;
            Socket socket = null;
            try {
                PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
                String peerID, appName;

                peerID = getPeerID(DATA_PATH + "DID.json");
                appName = peerID.concat("alphaPledging");

                listen(appName, port);

                PledgerLogger.debug("Quorum Listening on " + port + " appname " + appName);

                serverSocket = new ServerSocket(port);

                PledgerLogger.debug("Waiting for request...");
                socket = serverSocket.accept();

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintStream out = new PrintStream(socket.getOutputStream());

                int amountOfTokens;

                String pledgeRequest = null;
                try {
                    pledgeRequest = in.readLine();
                } catch (SocketException e) {
                    PledgerLogger.debug("Sender Input Stream Null - Pledge Request");
                    socket.close();
                    serverSocket.close();
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                }


                PledgerLogger.debug("Incoming Request: " + pledgeRequest);
                if (pledgeRequest != null) {

                    JSONObject transferObject = new JSONObject(pledgeRequest);
                    amountOfTokens = transferObject.getInt("amount");
                    senderPID = transferObject.getString("senderPID");

                    if (transferObject.has("PledgeTokens")) {
                        String bankFileContent = readFile(PAYMENTS_PATH.concat("BNK00.json"));
                        JSONArray tokensArray = new JSONArray(bankFileContent);
                        JSONObject token = new JSONObject();
                        JSONArray tokenDetails = new JSONArray();

                        JSONArray pledgingTokens = new JSONArray();
                        for (int i = 0; i < amountOfTokens; i++) {
                            String chainFile = readFile(TOKENCHAIN_PATH.concat(tokensArray.getJSONObject(i).getString("tokenHash")).concat(".json"));
                            JSONArray chainArray = new JSONArray(chainFile);

                            token.put("tokenHash", tokensArray.getJSONObject(i).getString("tokenHash"));
                            token.put("chain", chainArray);
                            tokenDetails.put(token);

                            JSONObject newObject = new JSONObject();
                            newObject.put("tokenHash", tokensArray.getJSONObject(i).getString("tokenHash"));
                            pledgingTokens.put(newObject);
                        }

                        out.println(tokenDetails.toString());
                        PledgerLogger.debug("Sent Tokens for Pledging");

                        String newChains = null;
                        try {
                            newChains = in.readLine();
                        } catch (SocketException e) {
                            PledgerLogger.debug("Sender Input Stream Null - New Chains");
                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                        }
                        PledgerLogger.debug("Received new TokenChains: " + newChains);
                        if (newChains != null) {
                            if(!newChains.contains("Abort")){
                                JSONArray newChainsArrays = new JSONArray(newChains);
                                for (int i = 0; i < newChainsArrays.length(); i++) {
                                    writeToFile(TOKENCHAIN_PATH.concat(tokensArray.getJSONObject(i).getString("tokenHash")).concat(".json"), newChainsArrays.getJSONArray(i).toString(), false);
                                }

                                String bankFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));


                                File pledgeFile = new File(PAYMENTS_PATH.concat("PledgedTokens.json"));
                                if(!pledgeFile.exists()) {
                                    pledgeFile.createNewFile();
                                    writeToFile(PAYMENTS_PATH.concat("PledgedTokens.json"), pledgingTokens.toString(), false);
                                }else{
                                    String pledgedContent = readFile(PAYMENTS_PATH.concat("PledgedTokens.json"));
                                    JSONArray pledgedArray = new JSONArray(pledgedContent);
                                    for(int i = 0; i < pledgingTokens.length(); i++){
                                        pledgedArray.put(pledgingTokens.getJSONObject(i));
                                    }
                                    writeToFile(PAYMENTS_PATH.concat("PledgedTokens.json"), pledgedArray.toString(), false);
                                }

                                JSONArray bankArray = new JSONArray(bankFile);
                                for(int i = 0; i < amountOfTokens; i++){
                                    bankArray.remove(i);
                                }
                                writeToFile(PAYMENTS_PATH.concat("BNK00.json"), bankArray.toString(), false);
                            }
                        }

                        socket.close();
                        serverSocket.close();
                        executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);

                    }
                }
                socket.close();
                serverSocket.close();
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);


            } catch (IOException | JSONException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                    serverSocket.close();
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                } catch (IOException e) {
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                    PledgerLogger.error("IOException Occurred", e);
                }

            }
        }
    }
}
