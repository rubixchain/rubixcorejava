package com.rubix.TokenTransfer.TransferPledge;

import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.PrivateKey;
import java.util.Iterator;

import static com.rubix.NFTResources.NFTFunctions.getPvtKey;
import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.forward;
import static com.rubix.Resources.IPFSNetwork.swarmConnectP2P;

public class Initiator {

<<<<<<< HEAD
    public static Logger PledgeInitiatorLogger = Logger.getLogger(Initiator.class);
    public static JSONArray pledgedNodes = new JSONArray();
    public static JSONObject abortReason = new JSONObject();
    public static JSONArray tokenList = new JSONArray();
    public static JSONObject distributedObject;
    public static JSONArray nodesToPledgeTokens = new JSONArray();
    public static JSONObject quorumDetails = new JSONObject();
    public static JSONArray quorumWithHashesArray = new JSONArray();
    public static String sender;
    public static String receiver;
    public static String pvt;
    public static String tid;
    public static String keyPass;
    public static JSONObject quorumHashObject = new JSONObject();
    public static boolean pledged = false;
    public static JSONArray pledgedTokensWithNodesArray = new JSONArray();
    public static JSONArray alphaList = new JSONArray();


    public static JSONArray pledgedTokensArray = new JSONArray();

    public static boolean abort = false;

    public static void resetVariables() {
        alphaList = new JSONArray();
        pledgedTokensWithNodesArray = new JSONArray();
        distributedObject = new JSONObject();
        pledgedNodes = new JSONArray();
        abortReason = new JSONObject();
        tokenList = new JSONArray();
        nodesToPledgeTokens = new JSONArray();
        quorumDetails = new JSONObject();
        quorumWithHashesArray = new JSONArray();
        sender = "";
        receiver = "";
        pvt = "";
        tid = "";
        keyPass = "";
        quorumHashObject = new JSONObject();
        pledgedTokensArray = new JSONArray();
        abort = false;
        pledged = false;

    }

    public static boolean pledgeSetUp(String data, IPFS ipfs, int PORT) throws JSONException, IOException {
        resetVariables();
        PledgeInitiatorLogger.debug("Initiator Calling");
        pledgedNodes = new JSONArray();
        JSONObject dataObject = new JSONObject(data);

        alphaList = dataObject.getJSONArray("alphaList");
        pvt = dataObject.getString("pvt");
        tid = dataObject.getString("tid");
        String keyPass = dataObject.getString("pvtKeyPass");
        sender = dataObject.getString("sender");
        receiver = dataObject.getString("receiver");
        tokenList = dataObject.getJSONArray("tokenList");

        Socket qSocket = null;
        int tokensCount = dataObject.getInt("amount");
        PledgeInitiatorLogger.debug("Amount Received; " + tokensCount);
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

            qSocket = new Socket("127.0.0.1", PORT);
            BufferedReader qIn = new BufferedReader(new InputStreamReader(qSocket.getInputStream()));
            PrintStream qOut = new PrintStream(qSocket.getOutputStream());

            JSONObject dataArray = new JSONObject();
            dataArray.put("amount", tokensCount);
            dataArray.put("senderPID", getPeerID(DATA_PATH.concat("DID.json")));
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
                JSONObject pledgeObject = new JSONObject(qResponse);
                if (pledgeObject.getString("pledge").equals("Tokens")) {
                    JSONArray tokenDetails = pledgeObject.getJSONArray("tokenDetails");
                    PledgeInitiatorLogger.debug("Quorum " + quorumID + " is pledging Tokens: " + tokenDetails);

                    JSONArray hashesArray = new JSONArray();
                    for (int k = 0; k < tokenDetails.length(); k++) {

                        JSONObject tokenObject = tokenDetails.getJSONObject(k);
                        JSONArray tokenChain = tokenObject.getJSONArray("chain");
                        JSONObject lasObject = tokenChain.getJSONObject(tokenChain.length() - 1);
                        if (lasObject.optString("pledgeToken").length() > 0) {
                            PledgeInitiatorLogger.debug(tokenObject.getString("tokenHash") +" is a recently unpledged token");
                            /**
                             *  code block to verify unpledged tokens proof
                             */
                            if (tokenObject.has("cid")) {
                                String token = tokenObject.getString("token");
                                String cid = tokenObject.getString("cid");
                                String proof = IPFSNetwork.get(cid, ipfs);
                                File proofFile = new File(TOKENCHAIN_PATH + "Proof/");
                                if (!proofFile.exists()) {
                                    proofFile.mkdirs();
                                }
                                writeToFile(proofFile.getAbsolutePath() + "/" + token + ".proof", proof, false);

                                String tokenName = lasObject.getString("pledgeToken");
                                String did = lasObject.getString("receiver");
                                String tidForProof = lasObject.getString("tid");
								PledgeInitiatorLogger.debug("tid for Proof verification "+ tidForProof);
                                pledged = Unpledge.verifyProof(tokenName, did, tidForProof);

                                if (!pledged) {
                                    PledgeInitiatorLogger.debug("PoW not Verified");
                                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                                    qSocket.close();
                                }else {
                                    PledgeInitiatorLogger.debug("PoW verified");
                                    JSONObject pledgeNewObject = new JSONObject();
                                    pledgeNewObject.put("sender", sender);
                                    pledgeNewObject.put("receiver", receiver);
                                    pledgeNewObject.put("tid", tid);
                                    pledgeNewObject.put("pledgeToken", tokenObject.getString("tokenHash"));
                                    pledgeNewObject.put("tokensPledgedFor", tokenList);
                                    pledgeNewObject.put("tokensPledgedWith", tokenObject.getString("tokenHash"));
                                    pledgeNewObject.put("distributedObject", new JSONObject());

                                    pledgedTokensArray.put(tokenObject.getString("tokenHash"));

                                    tokenChain.put(pledgeNewObject);
                                    String chainHashString = calculateHash(tokenChain.toString(), "SHA3-256");
                                    hashesArray.put(chainHashString);
                                }

                            } else {
                                PledgeInitiatorLogger.debug("The token does not have PoW");
                                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                                qSocket.close();
                            }
                        } else {
                            JSONObject pledgeNewObject = new JSONObject();
                            pledgeNewObject.put("sender", sender);
                            pledgeNewObject.put("receiver", receiver);
                            pledgeNewObject.put("tid", tid);
                            pledgeNewObject.put("pledgeToken", tokenObject.getString("tokenHash"));
                            pledgeNewObject.put("tokensPledgedFor", tokenList);
                            pledgeNewObject.put("tokensPledgedWith", tokenObject.getString("tokenHash"));
                            pledgeNewObject.put("distributedObject", new JSONObject());


                            pledgedTokensArray.put(tokenObject.getString("tokenHash"));
                            tokenChain.put(pledgeNewObject);
                            String chainHashString = calculateHash(tokenChain.toString(), "SHA3-256");
                            hashesArray.put(chainHashString);

                        }
                    }
                    quorumHashObject.put(quorumID, hashesArray);
                    quorumWithHashesArray.put(quorumHashObject);
                    quorumDetails.put("tokenDetails", tokenDetails);

                    PledgeInitiatorLogger.debug("tokensCount: " + tokensCount);
                    quorumDetails.put("ID", quorumID);
                    quorumDetails.put("count", tokenDetails.length());
                    nodesToPledgeTokens.put(quorumDetails);
                    tokensCount -= tokenDetails.length();
                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                    qSocket.close();
                    PledgeInitiatorLogger.debug("tokensCount: " + tokensCount);
                    if (tokensCount == 0)
                        break;

                } else {
                    PledgeInitiatorLogger.debug("Quorum " + quorumID + " is not pledging tokens");
                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                    qSocket.close();

                }
            } else {
                PledgeInitiatorLogger.debug("Quorum " + quorumID + " Response is null");
                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                qSocket.close();
            }
        }

        closeAllConnections(alphaList);
        PledgeInitiatorLogger.debug("Token Count: " + tokensCount);
        if (tokensCount > 0) {
            PledgeInitiatorLogger.debug("Quorum nodes with lesser tokens to pledge, Exiting...");
            return true;
        }else {
            distributePledgeTokens(tokenList, pledgedTokensArray);
            return false;
        }
    }

    public static boolean pledge(JSONArray pledgeDetails, double amount, int PORT)
            throws JSONException, UnknownHostException, IOException {
        pledgedTokensArray = new JSONArray();

        Double tokensPledged = amount;
        //PledgeInitiatorLogger.debug("pledgeDetails in pledge is " + pledgeDetails.toString());

        if (!abort) {
            PledgeInitiatorLogger.debug("Initating pledging");
            // PledgeInitiatorLogger.debug("Tokens can be pledged from " +
            // nodesToPledgeTokens);
            for (int j = 0; j < nodesToPledgeTokens.length(); j++) {
                String quorumID = nodesToPledgeTokens.getJSONObject(j).getString("ID");
                String appName = quorumID.concat("alphaPledge");

                PledgeInitiatorLogger.debug("Quorum " + quorumID + " pledging");
                PledgeInitiatorLogger.debug("Quorum ID " + quorumID + " AppName " + appName);
                forward(appName, PORT, quorumID);
                PledgeInitiatorLogger
                        .debug("Connected to " + quorumID + "on port " + (PORT) + "with AppName" + appName);

                Socket qSocket1 = new Socket("127.0.0.1", PORT);
                BufferedReader qIn = new BufferedReader(new InputStreamReader(qSocket1.getInputStream()));
                PrintStream qOut = new PrintStream(qSocket1.getOutputStream());

                JSONObject object = new JSONObject();
                object.put("PledgeTokens", true);
                object.put("amount", nodesToPledgeTokens.getJSONObject(j).getInt("count"));
                object.put("senderPID", getPeerID(DATA_PATH.concat("DID.json")));
                qOut.println(object.toString());
                PledgeInitiatorLogger.debug("Sent request to alpha node: " + quorumID + ": " + object.toString());

                String qResponse = "";
                try {
                    qResponse = qIn.readLine();
                } catch (SocketException e) {
                    PledgeInitiatorLogger.warn("Quorum " + quorumID + " is unable to respond!");
                    IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                }

                JSONArray tokens = new JSONArray();
                if (qResponse != null) {
                    JSONArray tokenDetails = new JSONArray(qResponse);
                    //PledgeInitiatorLogger.debug("TokenDetails is " + tokenDetails.toString());
                    JSONArray newChains = new JSONArray();
                    for (int k = 0; k < tokenDetails.length(); k++) {
                        JSONObject tokenObject = tokenDetails.getJSONObject(k);
                        JSONArray tokenChain = tokenObject.getJSONArray("chain");
                        String tokenHash = tokenObject.getString("tokenHash");
                        PledgeInitiatorLogger.debug(tokenHash);
                        PledgeInitiatorLogger.debug("in json " + tokenObject.getString("tokenHash"));

                        JSONObject lastObject = tokenChain.getJSONObject(tokenChain.length() - 1);
                        PledgeInitiatorLogger.debug("tokenHash is " + tokenHash);

                        if (lastObject.optString("pledgeToken").length() > 0 && !pledged) {
                            PledgeInitiatorLogger
                                    .debug("Quorum " + quorumID + " sent a token which is already pledged");
                            abortReason.put("Quorum", quorumID);
                            abortReason.put("Reason", "Token " + tokenHash + " has been already pledged");
                            abort = true;
                            qSocket1.close();
                            return abort;

                        } else {
                            JSONObject pledgeObject = new JSONObject();
                            pledgeObject.put("sender", sender);
                            pledgeObject.put("receiver", receiver);
                            pledgeObject.put("tid", tid);
                            pledgeObject.put("pledgeToken", tokenHash);
                            pledgeObject.put("tokensPledgedFor", tokenList);
                            pledgeObject.put("tokensPledgedWith", tokenObject.getString("tokenHash"));
                            pledgeObject.put("distributedObject", new JSONObject());
                            tokenChain.put(pledgeObject);

                            pledgedTokensArray.put(tokenObject.getString("tokenHash"));

                            // lastObject.put("pledgeToken", dataObject.getString("tid"));
                            //
                            // tokenChain.remove(0);
                            // JSONArray newTokenChain = new JSONArray();
                            // newTokenChain.put(lastObject);
                            // for (int l = 0; l < tokenChain.length(); l++) {
                            // newTokenChain.put(tokenChain.getJSONObject(l));
                            // }
                            PrivateKey pvtKey = getPvtKey(keyPass, 1);

                            String hashForTokenChain = calculateHash(tokenChain.toString(), "SHA3-256");
                            /* FileWriter spfile = new FileWriter(
                                    WALLET_DATA_PATH.concat("/hashForTokenChain").concat(tid).concat(".json"));
                            spfile.write(tokenChain.toString());
                            spfile.close(); */


                            // for (int i = 0; i < pledgeDetails.length(); i++) {

                            //PledgeInitiatorLogger.debug("!@#$%^& pledgeDetails is " + pledgeDetails.getJSONObject(j));

                            //PledgeInitiatorLogger.debug("!@#$%^& hashForTokenChain is "+ hashForTokenChain);

                            tokenChain.remove(tokenChain.length() - 1);
                            /* FileWriter pfile = new FileWriter(
                                    WALLET_DATA_PATH.concat("/hashForTokenChainRemoved").concat(tid).concat(".json"));
                            pfile.write(tokenChain.toString());
                            pfile.close(); */


                            //PledgeInitiatorLogger.debug("!@#$%^& hashForTokenChain after removing new last object "+ calculateHash(tokenChain.toString(), "SHA3-256"));

                            pledgeObject.put("hash", hashForTokenChain);
                            pledgeObject.put("pvtShareBits", fetchSign(pledgeDetails, hashForTokenChain));

                            //PledgeInitiatorLogger.debug("pledgeObject is " + pledgeObject.toString());

                            // pledgeObject.put("pvtKeySign", PvtKeySign);

                            tokenChain.put(pledgeObject);
                            newChains.put(tokenChain);

                            tokensPledged -= nodesToPledgeTokens.getJSONObject(j).getInt("count");
                            tokens.put(tokenHash);

                            //PledgeInitiatorLogger.debug("newChains length is " + newChains.length());
//                            //PledgeInitiatorLogger.debug("newChains is " + newChains.toString());
                        }
                    }
                    JSONObject nodesWithTokensObject = new JSONObject();
                    nodesWithTokensObject.put("node", nodesToPledgeTokens.getJSONObject(j).getString("ID"));
                    nodesWithTokensObject.put("tokens", pledgedTokensArray);
                    pledgedTokensWithNodesArray.put(nodesWithTokensObject);

                    /*
                     * JSONObject jsonObject = pledgeDetails.getJSONObject(j); Iterator<String> keys
                     * = jsonObject.keys();
                     *
                     * PledgeInitiatorLogger.debug("!@#$%^& The object is " +
                     * jsonObject.toString()); String key = ""; while (keys.hasNext()) { key =
                     * keys.next(); PledgeInitiatorLogger.debug("!@#$%^& key of quorumn is " + key);
                     * if (jsonObject.get(key) instanceof JSONArray) { // do something with
                     * jsonObject here
                     *
                     * JSONArray hashArray = new JSONArray(jsonObject.get(key).toString());
                     * PledgeInitiatorLogger.debug("!@#$%^&  hash array: " + hashArray);
                     *
                     * for(int l = 0; l < hashArray.length(); l++) { JSONObject hashObject =
                     * hashArray.getJSONObject(k);
                     * PledgeInitiatorLogger.debug("!@#$%^&  hash object: " + hashObject);
                     *
                     *
                     * signString = hashObject.getString("sign"); hashString =
                     * hashObject.getString("hash");
                     *
                     * PledgeInitiatorLogger.debug("signString is " + signString);
                     * PledgeInitiatorLogger.debug("hashString is " + hashString);
                     *
                     * // TODO tokenChain.remove(tokenChain.length() - 1); pledgeObject.put("hash",
                     * hashString); pledgeObject.put("pvtShareBits", signString); //
                     * pledgeObject.put("pvtKeySign", PvtKeySign);
                     *
                     * tokenChain.put(pledgeObject); newChains.put(tokenChain);
                     *
                     * tokensPledged -= nodesToPledgeTokens.getJSONObject(j).getInt("count");
                     * tokens.put(tokenHash);
                     *
                     * } PledgeInitiatorLogger.debug("newChains length is "+newChains.length());
                     * PledgeInitiatorLogger.debug("newChains is "+newChains.toString()); } }
                     */

                    // }

                    // }
                    // }
                    JSONObject pledgeObjectDetails = new JSONObject();
                    pledgeObjectDetails.put("did", quorumID);
                    pledgeObjectDetails.put("tokens", tokens);
                    pledgeDetails.put(pledgeObjectDetails);
                    pledgedNodes = nodesToPledgeTokens;
                    // for (int i = 0; i < tokenObject.getString("tokenHash"); i++) {
                    // }
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
                    qSocket1.close();
                    PledgeInitiatorLogger.debug("Alpha Quorum " + quorumID + " Replied Null (abort status true)");
                    return abort;
                }

                IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + quorumID);
                qSocket1.close();

            }

        }
        closeAllConnections(alphaList);
        distributePledgeTokens(tokenList, pledgedTokensArray);
        if (tokensPledged > 0)
            abort = true;

        PledgeInitiatorLogger.debug("Quorum Verification with Tokens or Credits with Abort Status:" + Initiator.abort);
        return abort;
    }

    public static void closeAllConnections(JSONArray nodes){
        for(int i = 0; i < nodes.length(); i++){
            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + nodes.getString(i));
        }
    }

    public static boolean distributePledgeTokens(JSONArray transferTokens, JSONArray pledgedTokens){
        distributedObject = new JSONObject();

        PledgeInitiatorLogger.debug("<1> Transfer tokens: " + transferTokens);
        PledgeInitiatorLogger.debug("<1> Pledge tokens: " + pledgedTokens);

        if(transferTokens.length() > pledgedTokens.length()) {
            PledgeInitiatorLogger.debug("Only Whole Tokens");
            String partPledgedTokenString = pledgedTokens.getString(pledgedTokens.length()-1);
            pledgedTokens.remove(pledgedTokens.length()-1);

            PledgeInitiatorLogger.debug("<2> Transfer tokens: " + transferTokens);
            PledgeInitiatorLogger.debug("<2> Pledge tokens: " + pledgedTokens);
            for(int i = 0; i < transferTokens.length(); i++) {
                distributedObject.put(transferTokens.getString(i), pledgedTokens.getString(i));
                transferTokens.remove(i);
            }

            PledgeInitiatorLogger.debug("<3> Transfer tokens: " + transferTokens);
            PledgeInitiatorLogger.debug("<3> Pledge tokens: " + pledgedTokens);

            for (int i = 0; i < transferTokens.length(); i++)
                distributedObject.put(transferTokens.getString(i), partPledgedTokenString);

        }else {
            PledgeInitiatorLogger.debug("Parts Tokens Included");
            for(int i = 0; i < transferTokens.length(); i++) {
                distributedObject.put(transferTokens.getString(i), pledgedTokens.getString(i));
            }
            PledgeInitiatorLogger.debug("<4> Transfer tokens: " + transferTokens);
            PledgeInitiatorLogger.debug("<4> Pledge tokens: " + pledgedTokens);
        }

        return true;
    }

    public static String fetchSign(JSONArray pledgeArray, String hash) throws JSONException {

        String str = "";

        for (int i = 0; i < pledgeArray.length(); i++) {
            JSONObject pledgeObject = pledgeArray.getJSONObject(i);
            Iterator<String> keys = pledgeObject.keys();

            //PledgeInitiatorLogger.debug("!@#$%^& The object is " + pledgeObject.toString());
            String key = "";
            while (keys.hasNext()) {
                key = keys.next();
                PledgeInitiatorLogger.debug("!@#$%^& key of quorumn is " + key);
                if (pledgeObject.get(key) instanceof JSONArray) {
                    // do something with jsonObject here

                    JSONArray hashArray = new JSONArray(pledgeObject.get(key).toString());
                    PledgeInitiatorLogger.debug("!@#$%^&  hash array: " + hashArray);

                    for (int l = 0; l < hashArray.length(); l++) {
                        JSONObject hashObject = hashArray.getJSONObject(l);
                        PledgeInitiatorLogger.debug("!@#$%^&  hash object: " + hashObject);
                        String signString = hashObject.getString("sign");
                        String hashString = hashObject.getString("hash");

                        if (hashString.equals(hash)) {
                            str = signString;
                            return str;
                        }
                        PledgeInitiatorLogger.debug("signString is " + signString);
                        PledgeInitiatorLogger.debug("hashString is " + hashString);
                    }
                }
            }

        }

        return str;
    }

}
