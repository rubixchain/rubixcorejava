package com.rubix.TokenTransfer.TransferPledge;

import com.rubix.Consensus.QuorumConsensus;
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

public class Pledger implements Runnable {
    public static Logger PledgerLogger = Logger.getLogger(Pledger.class);
    int port;
    IPFS ipfs;

    String senderPID;

    public Pledger() {

        this.port = 15070;
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

                File creditsFolder = new File(Functions.WALLET_DATA_PATH.concat("/Credits"));
                if (!creditsFolder.exists())
                    creditsFolder.mkdirs();
                peerID = getPeerID(DATA_PATH + "DID.json");
                appName = peerID.concat("alphaPledge");

                listen(appName, port);

                PledgerLogger.debug("Quorum Listening on " + port + " appname " + appName);

                serverSocket = new ServerSocket(port);

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

                    } else {

                        senderPID = transferObject.getString("senderPID");
                        PledgerLogger.debug("Initiator transfer amount: " + amountOfTokens);

                        JSONArray newQstArray = new JSONArray(readFile(WALLET_DATA_PATH + "QuorumSignedTransactions.json"));
                        int availableCredits = newQstArray.length();
                        PledgerLogger.debug("Available Credits: " + availableCredits);

                        File minedCH = new File(WALLET_DATA_PATH + "MinedCreditsHistory.json");
                        if (!minedCH.exists()) {
                            minedCH.createNewFile();
                            writeToFile(WALLET_DATA_PATH + "MinedCreditsHistory.json", "[]", false);
                        }

                        boolean oldCreditsFlag = false;
                        for (int i = 0; i < availableCredits; i++)
                            if (!newQstArray.getJSONObject(i).has("creditHash"))
                                oldCreditsFlag = true;

                        PledgerLogger.debug("Credits Old: " + oldCreditsFlag);

                        int numberOfTokens = 0;
                        File bank00File = new File(PAYMENTS_PATH.concat("BNK00.json"));
                        if (bank00File.exists()) {
                            String bankFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
                            JSONArray tokensArray = new JSONArray(bankFile);
                            for (int i = 0; i < tokensArray.length(); i++) {
                                File chain = new File(TOKENCHAIN_PATH.concat(tokensArray.getJSONObject(i).getString("tokenHash")).concat(".json"));
                                if (chain.exists()) {
                                    numberOfTokens++;
                                }
                            }
                        }

                        int creditsRequired = 0;
                        JSONObject resJsonData_credit = new JSONObject();
                        String GET_URL_credit = SYNC_IP + "/getCurrentLevel";
                        URL URLobj_credit = new URL(GET_URL_credit);
                        HttpURLConnection con_credit = (HttpURLConnection) URLobj_credit.openConnection();
                        con_credit.setRequestMethod("GET");
                        int responseCode_credit = con_credit.getResponseCode();
                        System.out.println("GET Response Code :: " + responseCode_credit);
                        if (responseCode_credit == HttpURLConnection.HTTP_OK) {
                            BufferedReader in_credit = new BufferedReader(
                                    new InputStreamReader(con_credit.getInputStream()));
                            String inputLine_credit;
                            StringBuffer response_credit = new StringBuffer();
                            while ((inputLine_credit = in_credit.readLine()) != null) {
                                response_credit.append(inputLine_credit);
                            }
                            in_credit.close();
                            resJsonData_credit = new JSONObject(response_credit.toString());
                            int level_credit = resJsonData_credit.getInt("level");
                            creditsRequired = (int) Math.pow(2, (2 + level_credit));
                            PledgerLogger.debug("Credits Required " + creditsRequired);
                        }


                        PledgerLogger.debug("Number of tokens available: " + numberOfTokens);
                        PledgerLogger.debug("Number of tokens required: " + amountOfTokens);
                        PledgerLogger.debug("Number of credits required: " + creditsRequired);
                        int neededCredits = creditsRequired * amountOfTokens;
                        if (availableCredits > neededCredits && !oldCreditsFlag) {
                            // Send QST for verification
                            String qstContent = readFile(WALLET_DATA_PATH.concat("QuorumSignedTransactions.json"));
                            JSONArray qstArray = new JSONArray();
                            qstArray = new JSONArray(qstContent);

                            int count = 0;
                            JSONArray creditSignsArray = new JSONArray();
                            for (int k = 0; k < qstArray.length(); k++) {
                                String creditIpfs = add(WALLET_DATA_PATH.concat("/Credits/").concat(qstArray.getJSONObject(k).getString("credits")).concat(".json"), ipfs);
                                pin(creditIpfs, ipfs);

                                String filePath = WALLET_DATA_PATH.concat("/Credits/").concat(qstArray.getJSONObject(k).getString("credits")).concat(".json");
                                File creditFile = new File(filePath);
                                if (creditFile.exists()) {
                                    count++;
                                    String creditContent = readFile(filePath);
                                    JSONArray creditArray = new JSONArray(creditContent);

                                    for (int i = 0; i < creditArray.length(); i++)
                                        creditSignsArray.put(creditArray.getJSONObject(i));
                                } else
                                    PledgerLogger.debug(qstArray.getJSONObject(k).getString("credits").concat(" file not found"));
                            }

                            JSONObject qstObject = new JSONObject();
                            if (count == qstArray.length()) {
                                qstObject.put("pledge", "Credits");
                                qstObject.put("qstArray", qstArray);
                                qstObject.put("credits", creditSignsArray);
                            }
                            out.println(qstObject.toString());
                            PledgerLogger.debug("Sent Credits for Pledging");
                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);

                        } else if (numberOfTokens >= amountOfTokens) {
                            JSONObject pledgeObject = new JSONObject();
                            pledgeObject.put("pledge", "Tokens");


                            String bankFileContent = readFile(PAYMENTS_PATH.concat("BNK00.json"));
                            JSONArray tokensArray = new JSONArray(bankFileContent);
                            JSONObject token = new JSONObject();
                            JSONArray tokenDetails = new JSONArray();
                            for (int i = 0; i < amountOfTokens; i++) {
                                String chainFile = readFile(TOKENCHAIN_PATH.concat(tokensArray.getJSONObject(i).getString("tokenHash")).concat(".json"));
                                JSONArray chainArray = new JSONArray(chainFile);

                                token.put("tokenHash", tokensArray.getJSONObject(i).getString("tokenHash"));
                                token.put("chain", chainArray);
                                tokenDetails.put(token);
                            }
                            pledgeObject.put("tokenDetails", tokenDetails);
                            out.println(pledgeObject.toString());
                            PledgerLogger.debug("Sent Tokens for Pledging");

                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                        } else {
                            JSONObject pledgeObject = new JSONObject();
                            pledgeObject.put("pledge", "Abort");
                            out.println(pledgeObject.toString());

                            socket.close();
                            serverSocket.close();
                            executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);
                        }
                    }
                    socket.close();
                    serverSocket.close();
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + senderPID);

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
