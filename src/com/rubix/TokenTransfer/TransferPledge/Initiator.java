package com.rubix.TokenTransfer.TransferPledge;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Consensus.InitiatorConsensus;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.HashSet;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.forward;
import static com.rubix.Resources.IPFSNetwork.swarmConnectP2P;

public class Initiator {
    public static Logger PledgeInitiatorLogger = Logger.getLogger(Initiator.class);
    public static JSONArray pledgedNodes  = new JSONArray();
    public static JSONObject abortReason = new JSONObject();

    public static boolean abort = false;

    public static boolean pledgeSetUp(String data, IPFS ipfs, int PORT) throws JSONException, IOException {
        PledgeInitiatorLogger.debug("Initiator Calling");
        abortReason = new JSONObject();
        abort = false;
        pledgedNodes = new JSONArray();
        JSONArray nodesToPledgeTokens = new JSONArray();
        JSONObject dataObject = new JSONObject(data);
        JSONArray alphaList = dataObject.getJSONArray("alphaList");


        // Get level of token from advisory node
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
            PledgeInitiatorLogger.debug("Credits Required " + creditsRequired);
        }

        int contactedNodes = 0;
        for (int i = 0; i < alphaList.length(); i++) {
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

            String quorumID = alphaList.getString(i);
            swarmConnectP2P(quorumID, ipfs);
            syncDataTable(null, quorumID);
            String quorumDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", quorumID);
            String quorumWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "peerid", quorumID);
            nodeData(quorumDidIpfsHash, quorumWidIpfsHash, ipfs);
            String appName = quorumID.concat("alphaPledge");

            PledgeInitiatorLogger.debug("Quorum ID " + quorumID + " AppName " + appName);
            forward(appName, PORT, quorumID);
            PledgeInitiatorLogger.debug("Connected to " + quorumID + "on port " + (PORT) + "with AppName" + appName);

            Socket qSocket = new Socket("127.0.0.1", PORT);
            BufferedReader qIn = new BufferedReader(new InputStreamReader(qSocket.getInputStream()));
            PrintStream qOut = new PrintStream(qSocket.getOutputStream());

            JSONObject dataArray = new JSONObject();
            dataArray.put("amount", dataObject.getInt("amount"));
            dataArray.put("tokenList", dataObject.getJSONArray("tokenList"));
            dataArray.put("senderPID", getPeerID(DATA_PATH.concat("DID.json")));
            dataArray.put("credits", creditsRequired);
            qOut.println(dataArray.toString());
            PledgeInitiatorLogger.debug("Sent request to alpha node: " + quorumID);


            String qResponse = "";
            try {
                qResponse = qIn.readLine();
            } catch (SocketException e) {
                PledgeInitiatorLogger.warn("Quorum " + quorumID + " is unable to respond!");
                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                qSocket.close();
            }


            if (qResponse != null) {
                contactedNodes++;
                JSONObject pledgeObject = new JSONObject(qResponse);
                if (pledgeObject.getString("pledge").equals("Credits")) {
                    PledgeInitiatorLogger.debug("Quorum " + quorumID + " is pledging Credits");
                    // Level 1 Verification: Verify hash of n objects
                    JSONArray qstArray = pledgeObject.getJSONArray("qstArray");
                    JSONArray creditsArray = pledgeObject.getJSONArray("credits");

                    boolean flag = true;
                    // if qstArray has any duplicate object
                    for (int k = 0; k < qstArray.length(); k++) {
                        for (int l = k + 1; l < qstArray.length(); l++) {
                            if (qstArray.getJSONObject(k).getString("credits").equals(qstArray.getJSONObject(l).getString("credits"))) {
                                flag = false;
                                break;
                            }
                        }
                    }
                    for (int k = 0; k < creditsRequired; k++) {
                        PledgeInitiatorLogger.debug("Credit object: " + creditsArray.getJSONObject(k).toString());
                        PledgeInitiatorLogger.debug("Credit Hash: " + calculateHash(creditsArray.getJSONObject(k).toString(), "SHA3-256"));
                        String reHash = calculateHash(qstArray.getJSONObject(k).getString("credits"), "SHA3-256");
                        if (!reHash.equals(qstArray.getJSONObject(k).getString("creditHash"))) {
                            PledgeInitiatorLogger.debug("Recalculation " + reHash + " - " + qstArray.getJSONObject(k).getString("creditHash"));
                            flag = false;
                        }
                    }

                    if (flag) {
                        PledgeInitiatorLogger.debug("Credits details match for " + quorumID);
                        boolean verifySigns = true;
                        for (int k = 0; k < creditsRequired; k++) {
                            if (!Authenticate.verifySignature(creditsArray.getJSONObject(k).toString()))
                                verifySigns = false;
                        }
                        if (verifySigns) {
                            PledgeInitiatorLogger.debug("Signatures Verified for " + quorumID);
                            HashSet hashSet = new HashSet();
                            long startTime = System.currentTimeMillis();
                            for (int k = 0; k < creditsArray.length(); k++) {
                                String sign = creditsArray.getJSONObject(k).getString("signature");
                                String signHash = calculateHash(sign, "SHA3-256");
                                hashSet.add(signHash);
                            }
                            long endTime = System.currentTimeMillis();
                            PledgeInitiatorLogger.debug("Total Time for HashSet: " + (endTime - startTime));
                            if (hashSet.size() == qstArray.length() * 15) {
                                PledgeInitiatorLogger.debug("Credits Verified for " + quorumID);

                            } else {
                                PledgeInitiatorLogger.debug("1. Setting abort tot true");
                                abort = true;
                                abortReason.put("Quorum", quorumID);
                                abortReason.put("Reason", "Mining Not Verified: Duplicates Found");
                                PledgeInitiatorLogger.debug("HashSet: " + hashSet.size() + " QST Size " + qstArray.length());
                                PledgeInitiatorLogger.debug("Mining Not Verified: Duplicates Found");
                                PledgeInitiatorLogger.debug("Credit Verification failed: Duplicates found");
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                                qSocket.close();
                                return abort;
                            }
                        } else {
                            PledgeInitiatorLogger.debug("2. Setting abort tot true");
                            abortReason.put("Quorum", quorumID);
                            abortReason.put("Reason", "Credit Signatures Not Verified");
                            abort = true;
                            PledgeInitiatorLogger.debug("Signatures Not Verified");
                            PledgeInitiatorLogger.debug("Credit Verification failed");
                            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                            qSocket.close();
                            return abort;
                        }
                    } else {
                        PledgeInitiatorLogger.debug("3. Setting abort tot true");
                        abortReason.put("Quorum", quorumID);
                        abortReason.put("Reason", "Credits Verification Failed");
                        abort = true;
                        PledgeInitiatorLogger.debug("4- Mining Not Verified: Duplicates Found");
                        PledgeInitiatorLogger.debug("4- Credit Verification failed: Duplicates found");
                        IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                        qSocket.close();
                        return abort;
                    }
                    PledgeInitiatorLogger.debug("Node " + quorumID + " have credits to pledge");
                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                    qSocket.close();
                } else if (pledgeObject.getString("pledge").equals("Tokens")) {
                    PledgeInitiatorLogger.debug("Quorum " + quorumID + " is pledging Tokens");
                    nodesToPledgeTokens.put(quorumID);
                    JSONArray tokenDetails = pledgeObject.getJSONArray("tokenDetails");
                    for (int k = 0; k < tokenDetails.length(); k++) {

                        JSONObject tokenObject = tokenDetails.getJSONObject(k);
                        JSONArray tokenChain = tokenObject.getJSONArray("chain");
                        JSONObject genesisObject = tokenChain.getJSONObject(0);
                        if (genesisObject.has("pledgeToken")) {
                            PledgeInitiatorLogger.debug("This token has already been pledged - Aborting");
                            PledgeInitiatorLogger.debug("4. Setting abort tot true");
                            abortReason.put("Quorum", quorumID);
                            abortReason.put("Reason", "Token " + tokenObject.getString("tokenHash") + " has already been pledged");
                            abort = true;
                            qSocket.close();
                            return abort;
                        }
                    }
                    PledgeInitiatorLogger.debug("Contacted Node "+ quorumID + "  for Pledging  - Over with abort status: " + abort);
                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                    qSocket.close();

                } else if (pledgeObject.getString("pledge").equals("Abort")) {
                    PledgeInitiatorLogger.debug("Quorum " + quorumID + " cannot back this transfer - Abort");
                    PledgeInitiatorLogger.debug("5. Setting abort tot true");
                    abortReason.put("Quorum", quorumID);
                    abortReason.put("Reason", "Quorum Node acknowledged Abort");
                    abort = true;
                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                    qSocket.close();
                    return abort;
                }
            } else {
                PledgeInitiatorLogger.debug("6. Setting abort tot true");
                abortReason.put("Quorum", quorumID);
                abortReason.put("Reason", "Response is null");
                abort = true;
                PledgeInitiatorLogger.debug("Response is null");
                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                qSocket.close();
                return abort;
            }
        }
        if(contactedNodes != alphaList.length()) {
            PledgeInitiatorLogger.debug("Initiator could not contact all nodes for confirmation, Aborting...");
            abortReason.put("Reason", "Initiator could not contact all nodes for confirmation, Aborting...");
            abort = true;
            return abort;
        }

        if (!abort) {
            PledgeInitiatorLogger.debug("Tokens can be pledged from " + nodesToPledgeTokens.length() + " quorum nodes");
            for (int j = 0; j < nodesToPledgeTokens.length(); j++) {
                String quorumID = nodesToPledgeTokens.getString(j);
                String appName = quorumID.concat("alphaPledge");

                PledgeInitiatorLogger.debug("Quorum " + quorumID + " pledging");
                PledgeInitiatorLogger.debug("Quorum ID " + quorumID + " AppName " + appName);
                forward(appName, PORT, quorumID);
                PledgeInitiatorLogger.debug("Connected to " + quorumID + "on port " + (PORT) + "with AppName" + appName);

                Socket qSocket = new Socket("127.0.0.1", PORT);
                BufferedReader qIn = new BufferedReader(new InputStreamReader(qSocket.getInputStream()));
                PrintStream qOut = new PrintStream(qSocket.getOutputStream());


                JSONObject object = new JSONObject();
                object.put("PledgeTokens", true);
                object.put("amount", dataObject.getInt("amount"));
                qOut.println(object.toString());

                String qResponse = "";
                try {
                    qResponse = qIn.readLine();
                } catch (SocketException e) {
                    PledgeInitiatorLogger.warn("Quorum " + quorumID + " is unable to respond!");
                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                }

                if (qResponse != null) {
                    JSONArray tokenDetails = new JSONArray(qResponse);
                    JSONArray newChains = new JSONArray();
                    for (int k = 0; k < tokenDetails.length(); k++) {
                        JSONObject tokenObject = tokenDetails.getJSONObject(k);
                        JSONArray tokenChain = tokenObject.getJSONArray("chain");
                        JSONObject genesisObject = tokenChain.getJSONObject(0);
                        if (genesisObject.has("pledgeToken")) {
                            PledgeInitiatorLogger.debug("7. Setting abort to true because token has been already pledged");
                            abortReason.put("Quorum", quorumID);
                            abortReason.put("Reason", "Token " + tokenObject.getJSONArray("tokenHash") + " has been already pledged");
                            abort = true;
                            qSocket.close();
                            return abort;
                        } else {
                            genesisObject.put("pledgeToken", dataObject.getJSONArray("tokenList").getString(k));

                            tokenChain.remove(0);
                            JSONArray newTokenChain = new JSONArray();
                            newTokenChain.put(genesisObject);
                            for (int l = 0; l < tokenChain.length(); l++) {
                                newTokenChain.put(tokenChain.getJSONObject(l));
                            }
                            newChains.put(newTokenChain);
                        }
                    }
                    pledgedNodes = nodesToPledgeTokens;
                    if (abort) {
                        qOut.println("Abort");
                        PledgeInitiatorLogger.debug("Quorum " + quorumID + " Aborted as already Pledged");
                        return abort;
                    } else {
                        qOut.println(newChains.toString());
                        PledgeInitiatorLogger.debug("Quorum " + quorumID + " Pledged");
                    }
                } else {
                    PledgeInitiatorLogger.debug("8. Setting abort to true");
                    abortReason.put("Quorum", quorumID);
                    abortReason.put("Reason", "Response is null");
                    abort = true;
                    qSocket.close();
                    PledgeInitiatorLogger.debug("Alpha Quorum " + quorumID + " Verification Complete with abort status true");
                    return abort;
                }
                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                qSocket.close();

            }
        }

        PledgeInitiatorLogger.debug("Quorum Verification with Tokens or Credits with Abort Status:" + Initiator.abort);
        return abort;
    }

}
