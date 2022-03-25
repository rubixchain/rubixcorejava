package com.rubix.Mining;

import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.EXPLORER_IP;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.PAYMENTS_PATH;
import static com.rubix.Resources.Functions.QuorumCheck;
import static com.rubix.Resources.Functions.QuorumSwarmConnect;
import static com.rubix.Resources.Functions.SEND_PORT;
import static com.rubix.Resources.Functions.SYNC_IP;
import static com.rubix.Resources.Functions.TOKENCHAIN_PATH;
import static com.rubix.Resources.Functions.TOKENS_PATH;
import static com.rubix.Resources.Functions.WALLET_DATA_PATH;
import static com.rubix.Resources.Functions.calculateHash;
import static com.rubix.Resources.Functions.deleteFile;
import static com.rubix.Resources.Functions.getQuorum;
import static com.rubix.Resources.Functions.minQuorum;
import static com.rubix.Resources.Functions.mineUpdate;
import static com.rubix.Resources.Functions.readFile;
import static com.rubix.Resources.Functions.strToIntArray;
import static com.rubix.Resources.Functions.updateJSON;
import static com.rubix.Resources.Functions.updateQuorum;
import static com.rubix.Resources.Functions.writeToFile;
import static com.rubix.Resources.IPFSNetwork.add;
import static com.rubix.Resources.IPFSNetwork.pin;
import static com.rubix.Resources.IPFSNetwork.repo;

import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Consensus.InitiatorConsensus;
import com.rubix.Consensus.InitiatorProcedure;
import com.rubix.Consensus.StakeConsensus;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;





public class ProofCredits {

    public static Logger ProofCreditsLogger = Logger.getLogger(ProofCredits.class);
    private static ArrayList alphaPeersList;
    private static ArrayList betaPeersList;
    private static ArrayList gammaPeersList;
    private static int alphaSize = 0;

    public static JSONObject create(String data, IPFS ipfs) throws IOException, JSONException {

        repo(ipfs);
        JSONObject APIResponse = new JSONObject();
        JSONObject detailsObject = new JSONObject(data);
        String DID = detailsObject.getString("receiverDidIpfsHash");
        String pvt = detailsObject.getString("pvt");
        int type = detailsObject.getInt("type");
        int creditUsed = 0;
        long totalTime = 0;
        int QSTHeight = 0;

        JSONArray alphaQuorum = new JSONArray();
        JSONArray betaQuorum = new JSONArray();
        JSONArray gammaQuorum = new JSONArray();

        int creditsRequired = 50000, level;
        long starttime = System.currentTimeMillis();
        JSONArray resJsonData = new JSONArray();
        new JSONObject();
        JSONObject resJsonData_credit;

        JSONArray newQstArray = new JSONArray(readFile(WALLET_DATA_PATH + "QuorumSignedTransactions.json"));
        int availableCredits = newQstArray.length();

        File minedCH = new File(WALLET_DATA_PATH + "MinedCreditsHistory.json");
        if (!minedCH.exists()) {
            minedCH.createNewFile();
            writeToFile(WALLET_DATA_PATH + "MinedCreditsHistory.json", "[]", false);
        }
        String creditsHistory = readFile(WALLET_DATA_PATH + "MinedCreditsHistory.json");
        JSONArray creditsHistoryArray = new JSONArray(creditsHistory);

        // ! get actual qst height for current scenario
        QSTHeight = availableCredits + creditsHistoryArray.length();

        ProofCreditsLogger.debug("Credits available: " + availableCredits);
        String GET_URL_credit = SYNC_IP + "/getlevel";
        URL URLobj_credit = new URL(GET_URL_credit);
        HttpURLConnection con_credit = (HttpURLConnection) URLobj_credit.openConnection();
        con_credit.setRequestMethod("GET");
        int responseCode_credit = con_credit.getResponseCode();
        System.out.println("GET Response Code :: " + responseCode_credit);
        if (responseCode_credit == HttpURLConnection.HTTP_OK) {
            BufferedReader in_credit = new BufferedReader(new InputStreamReader(con_credit.getInputStream()));
            String inputLine_credit;
            StringBuffer response_credit = new StringBuffer();
            while ((inputLine_credit = in_credit.readLine()) != null) {
                response_credit.append(inputLine_credit);
            }
            in_credit.close();
            ProofCreditsLogger.debug("response from service " + response_credit.toString());
            resJsonData_credit = new JSONObject(response_credit.toString());
            int level_credit = resJsonData_credit.getInt("level");
            creditsRequired = (int) Math.pow(2, (2 + level_credit));
            ProofCreditsLogger.debug("credits required " + creditsRequired);

        } else
            ProofCreditsLogger.debug("GET request not worked");

        ProofCreditsLogger.debug("credits required " + creditsRequired + " available credits " + availableCredits);

        if (availableCredits >= creditsRequired) {

            boolean oldCreditsFlag = false;
            for (int i = 0; i < creditsRequired; i++)
                if (!newQstArray.getJSONObject(i).has("creditHash"))
                    oldCreditsFlag = true;

            ProofCreditsLogger.debug("Credits Old: " + oldCreditsFlag);

            // String GET_URL = SYNC_IP+"/getInfo?count="+availableCredits;
            String GET_URL = SYNC_IP + "/minetoken";
            URL URLobj = new URL(GET_URL);
            HttpURLConnection con = (HttpURLConnection) URLobj.openConnection();
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            System.out.println("GET Response Code :: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                ProofCreditsLogger.debug("response from service " + response.toString());
                resJsonData = new JSONArray(response.toString());

            } else
                ProofCreditsLogger.debug("GET request not worked");

            // Check if node can mine token
            if (resJsonData.length() > 0) {
                // Calling Mine token function
                JSONArray token = new JSONArray();

                level = resJsonData.getJSONObject(0).getInt("level");

                for (int i = 0; i < resJsonData.length(); i++) {
                    token.put(Functions.mineToken(resJsonData.getJSONObject(i).getInt("level"),
                            resJsonData.getJSONObject(i).getInt("token")));

                    creditUsed += (int) Math.pow(2, (2 + resJsonData.getJSONObject(i).getInt("level")));

                }

                if (resJsonData.getJSONObject(0).getInt("level") == 1)
                    creditUsed = 10;

                JSONArray prooftid = new JSONArray();
                String comments = resJsonData.toString() + prooftid;

                String authSenderByRecHash = calculateHash(token + DID + comments, "SHA3-256");
                String tid = calculateHash(authSenderByRecHash, "SHA3-256");

                writeToFile(LOGGER_PATH + "tempbeta", tid.concat(DID), false);
                String betaHash = IPFSNetwork.add(LOGGER_PATH + "tempbeta", ipfs);
                deleteFile(LOGGER_PATH + "tempbeta");

                writeToFile(LOGGER_PATH + "tempgamma", tid.concat(DID), false);
                String gammaHash = IPFSNetwork.add(LOGGER_PATH + "tempgamma", ipfs);
                deleteFile(LOGGER_PATH + "tempgamma");

                JSONArray quorumArray;
                switch (type) {
                    case 2: {
                        quorumArray = new JSONArray(readFile(DATA_PATH + "quorumlist.json"));
                        break;
                    }
                    default: {
                        quorumArray = getQuorum(betaHash, gammaHash, DID, DID, token.length());
                    }
                }

                QuorumSwarmConnect(quorumArray, ipfs);

                alphaSize = quorumArray.length() - 14;

                for (int i = 0; i < alphaSize; i++)
                    alphaQuorum.put(quorumArray.getString(i));

                for (int i = 0; i < 7; i++) {
                    betaQuorum.put(quorumArray.getString(alphaSize + i));
                    gammaQuorum.put(quorumArray.getString(alphaSize + 7 + i));
                }

                ProofCreditsLogger.debug("alphaquorum " + alphaQuorum + " size " + alphaQuorum.length());
                ProofCreditsLogger.debug("betaquorum " + betaQuorum + " size " + betaQuorum.length());
                ProofCreditsLogger.debug("gammaquorum " + gammaQuorum + " size " + gammaQuorum.length());

                alphaPeersList = QuorumCheck(alphaQuorum, alphaSize);
                betaPeersList = QuorumCheck(betaQuorum, 7);
                gammaPeersList = QuorumCheck(gammaQuorum, 7);

                // quorumPeersList = QuorumCheck(quorumArray, ipfs);

                if (alphaPeersList.size() < minQuorum(alphaSize) || betaPeersList.size() < 5
                        || gammaPeersList.size() < 5) {
                    updateQuorum(quorumArray, null, false, type);
                    APIResponse.put("did", DID);
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Quorum Members not available");
                    ProofCreditsLogger.warn("Quorum Members not available");
                    return APIResponse;
                }

                JSONArray signedQuorumList = new JSONArray();
                if (!oldCreditsFlag) {
                    ProofCreditsLogger.debug("New Credits");
                    // Send QST for verification
                    String qstContent = readFile(WALLET_DATA_PATH.concat("QuorumSignedTransactions.json"));
                    JSONArray qstArray = new JSONArray(qstContent);

                    int count = 0;
                    JSONArray creditSignsArray = new JSONArray();
                    for (int k = 0; k < qstArray.length(); k++) {
                        String creditIpfs = add(WALLET_DATA_PATH.concat("/Credits/")
                                .concat(qstArray.getJSONObject(k).getString("credits")).concat(".json"), ipfs);
                        pin(creditIpfs, ipfs);

                        String filePath = WALLET_DATA_PATH.concat("/Credits/")
                                .concat(qstArray.getJSONObject(k).getString("credits")).concat(".json");
                        File creditFile = new File(filePath);
                        if (creditFile.exists()) {
                            count++;
                            String creditContent = readFile(filePath);
                            JSONArray creditArray = new JSONArray(creditContent);

                            for (int i = 0; i < creditArray.length(); i++)
                                creditSignsArray.put(creditArray.getJSONObject(i));
                        } else
                            ProofCreditsLogger
                                    .debug(qstArray.getJSONObject(k).getString("credits").concat(" file not found"));
                    }

                    JSONObject qstObject = new JSONObject();
                    if (count == qstArray.length()) {
                        qstObject.put("qstArray", qstArray);
                        qstObject.put("credits", creditSignsArray);
                    } else {
                        updateQuorum(quorumArray, null, false, type);
                        APIResponse.put("did", DID);
                        APIResponse.put("tid", "null");
                        APIResponse.put("status", "Failed");
                        APIResponse.put("message", "Credit File(s) missing");
                        ProofCreditsLogger.warn("Credit File(s) missing");
                        return APIResponse;
                    }

                    JSONObject dataObject = new JSONObject();
                    dataObject.put("tid", tid);
                    dataObject.put("message", comments);
                    dataObject.put("receiverDidIpfs", DID);
                    dataObject.put("pvt", pvt);
                    dataObject.put("senderDidIpfs", DID);
                    dataObject.put("token", token.toString());
                    dataObject.put("alphaList", alphaPeersList);
                    dataObject.put("betaList", betaPeersList);
                    dataObject.put("gammaList", gammaPeersList);
                    dataObject.put("qstDetails", qstObject);

                    InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 3, alphaSize,
                            "new-credits-mining");

                    if (!(InitiatorConsensus.quorumSignature.length() >= 3 * minQuorum(7))) {
                        APIResponse.put("did", DID);
                        APIResponse.put("tid", "null");
                        APIResponse.put("status", "Failed");
                        APIResponse.put("message", "Consensus failed");
                        ProofCreditsLogger.debug("consensus failed");
                    } else {
                        ProofCreditsLogger.debug("token mined " + token);

                        int counter = 0;

                        for (int i = 0; i < availableCredits; i++) {
                            JSONObject temp = newQstArray.getJSONObject(i);
                            if (counter < creditUsed) {
                                prooftid.put(temp.getString("tid"));
                                counter++;
                            }
                        }

                        for (int i = 0; i < creditUsed; i++)
                            deleteFile(WALLET_DATA_PATH.concat("/Credits/")
                                    .concat(qstArray.getJSONObject(i).getString("credits")).concat(".json"));

                    }
                } else {
                    ProofCreditsLogger.debug("Old Credits");

                    JSONObject dataObject = new JSONObject();
                    dataObject.put("tid", tid);
                    dataObject.put("message", comments);
                    dataObject.put("receiverDidIpfs", DID);
                    dataObject.put("pvt", pvt);
                    dataObject.put("senderDidIpfs", DID);
                    dataObject.put("token", token.toString());
                    dataObject.put("alphaList", alphaPeersList);
                    dataObject.put("betaList", betaPeersList);
                    dataObject.put("gammaList", gammaPeersList);

                    InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 3, alphaSize, "");

                    if (!(InitiatorConsensus.quorumSignature.length() >= 3 * minQuorum(7))) {
                        APIResponse.put("did", DID);
                        APIResponse.put("tid", "null");
                        APIResponse.put("status", "Failed");
                        APIResponse.put("message", "Consensus failed");
                        ProofCreditsLogger.debug("consensus failed");
                    } else {
                        ProofCreditsLogger.debug("token mined " + token);

                        int counter = 0;

                        for (int i = 0; i < availableCredits; i++) {
                            JSONObject temp = newQstArray.getJSONObject(i);
                            if (counter < creditUsed) {
                                prooftid.put(temp.getString("tid"));
                                counter++;
                            }
                        }
                    }
                }
                // JSONObject stakingData = new JSONObject();
                // String tkHash = null;

                for (int i = 0; i < token.length(); i++) {
                    writeToFile(LOGGER_PATH + "tempToken", token.getString(i), false);
                    String tokenHash = IPFSNetwork.add(LOGGER_PATH + "tempToken", ipfs);
                    writeToFile(TOKENS_PATH + tokenHash, token.getString(i), false);
                    deleteFile(LOGGER_PATH + "tempToken");

                    FileWriter shareWriter = new FileWriter(new File(LOGGER_PATH + "mycredit.txt"), true);
                    shareWriter.write(InitiatorConsensus.quorumSignature.toString());
                    shareWriter.close();
                    File readCredit = new File(LOGGER_PATH + "mycredit.txt");
                    String credit = add(readCredit.toString(), ipfs);

                    File creditFile = new File(WALLET_DATA_PATH.concat("/Credits/").concat(credit).concat(".json"));
                    if (!creditFile.exists())
                        creditFile.createNewFile();
                    writeToFile(creditFile.toString(), InitiatorConsensus.quorumSignature.toString(), false);

                    String tokens = tokenHash;
                    String hashString = tokens.concat(DID);
                    String hashForPositions = calculateHash(hashString, "SHA3-256");

                    BufferedImage privateImage = ImageIO
                            .read(new File(DATA_PATH.concat(DID).concat("/PrivateShare.png")));
                    String firstPrivate = PropImage.img2bin(privateImage);
                    int[] privateIntegerArray1 = strToIntArray(firstPrivate);
                    String privateBinary = Functions.intArrayToStr(privateIntegerArray1);
                    String positions = "";
                    for (int j = 0; j < privateIntegerArray1.length; j += 49152) {
                        positions += privateBinary.charAt(j);
                    }
                    String ownerIdentity = hashForPositions.concat(positions);
                    String ownerIdentityHash = calculateHash(ownerIdentity, "SHA3-256");

                    ProofCreditsLogger.debug("Ownership Here");
                    ProofCreditsLogger.debug("tokens: " + tokenHash);
                    ProofCreditsLogger.debug("hashString: " + hashString);
                    ProofCreditsLogger.debug("hashForPositions: " + hashForPositions);
                    ProofCreditsLogger.debug("p1: " + positions);
                    ProofCreditsLogger.debug("QSTHeight: " + QSTHeight);
                    ProofCreditsLogger.debug("ownerIdentity: " + ownerIdentity);
                    ProofCreditsLogger.debug("ownerIdentityHash: " + ownerIdentityHash);
                    JSONArray tokenChainArray = new JSONArray();
                    JSONObject tokenChainGenesisObject = new JSONObject();
                    tokenChainGenesisObject.put("quorumSignatures", credit);
                    tokenChainGenesisObject.put("quorumSignContent", InitiatorConsensus.quorumSignature);
                    tokenChainGenesisObject.put("sender", DID);
                    tokenChainGenesisObject.put("comment", "Mining-Genesis");
                    tokenChainGenesisObject.put("tid", tid);
                    tokenChainGenesisObject.put("owner", ownerIdentityHash);
                    tokenChainGenesisObject.put("blockNumber", 0);
                    tokenChainGenesisObject.put("QSTHeight", QSTHeight);
                    tokenChainGenesisObject.put("tokenHash", tokenHash);
                    tokenChainGenesisObject.put("tokenContent", token.getString(i));
                    tokenChainGenesisObject.put("nextHash", calculateHash(tid, "SHA3-256"));
                    tokenChainGenesisObject.put("previousHash", "");
                    // stakingData = tokenChainGenesisObject;
                    // tkHash = tokenHash;
                    tokenChainArray.put(tokenChainGenesisObject);

                    // ! new token will now need a staked token
                    StakeConsensus.getStakeConsensus(InitiatorConsensus.signedAphaQuorumArray,
                            tokenChainGenesisObject, ipfs, SEND_PORT + 3,
                            "alpha-stake-token");

                    if (StakeConsensus.stakeDetails.length() > 0) {
                        tokenChainArray.put(StakeConsensus.stakeDetails);
                    } else {
                        updateQuorum(quorumArray, null, false, type);
                        APIResponse.put("did", DID);
                        APIResponse.put("tid", "null");
                        APIResponse.put("status", "Failed");
                        APIResponse.put("message",
                                "Staking failed.. Retry with alpha quorum members with aleast 1 RBT balance");
                        ProofCreditsLogger.warn("Staking failed");
                        return APIResponse;
                    }

                    writeToFile(TOKENCHAIN_PATH + tokenHash + ".json", tokenChainArray.toString(), false);
                    JSONObject temp = new JSONObject();
                    temp.put("tokenHash", tokenHash);
                    JSONArray tempArray = new JSONArray();
                    tempArray.put(temp);
                    updateJSON("add", PAYMENTS_PATH + "BNK00.json", tempArray.toString());
                }

                // updateJSON("add", TOKENCHAIN_PATH + tkHash + ".json", tempArray.toString());

                if (!(InitiatorConsensus.quorumSignature.length() >= 3 * minQuorum(7))) {
                    APIResponse.put("did", DID);
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Consensus failed");
                    ProofCreditsLogger.debug("consensus failed");
                } else {
                    ProofCreditsLogger.debug("token mined " + token);

                    int counter = 0;

                    for (int i = 0; i < availableCredits; i++) {
                        JSONObject temp = newQstArray.getJSONObject(i);
                        if (counter < creditUsed) {
                            prooftid.put(temp.getString("tid"));
                            counter++;
                        }
                    }
                }

                File minedCH = new File(WALLET_DATA_PATH + "MinedCreditsHistory.json");
                if (!minedCH.exists()) {
                    minedCH.createNewFile();
                    writeToFile(WALLET_DATA_PATH + "MinedCreditsHistory.json", "[]", false);
                }
                String creditsHistory = readFile(WALLET_DATA_PATH + "MinedCreditsHistory.json");
                JSONArray creditsHistoryArray = new JSONArray(creditsHistory);
                for (int i = 0; i < creditUsed; i++) {
                    JSONObject minedObject = new JSONObject();
                    minedObject.put("tid", newQstArray.getJSONObject(i).getString("tid"));
                    minedObject.put("consensusID", newQstArray.getJSONObject(i).getString("consensusID"));
                    minedObject.put("token", token);
                    creditsHistoryArray.put(minedObject);
                }
                writeToFile(WALLET_DATA_PATH + "MinedCreditsHistory.json", creditsHistoryArray.toString(), false);

                ProofCreditsLogger.debug("Before mining: " + newQstArray.length());
                for (int i = 0; i < creditUsed; i++)
                    newQstArray.remove(0);
                writeToFile(WALLET_DATA_PATH + "QuorumSignedTransactions.json", newQstArray.toString(), false);
                ProofCreditsLogger.debug("After mining: " + newQstArray.length());

                ProofCreditsLogger.debug("Updated balance of node : " + (availableCredits - creditUsed));
                long endtime = System.currentTimeMillis();
                totalTime = endtime - starttime;
                Iterator<String> keys = InitiatorConsensus.quorumSignature.keys();

                while (keys.hasNext())
                    signedQuorumList.put(keys.next());

                updateQuorum(quorumArray, signedQuorumList, true, type);
                mineUpdate(DID, creditUsed);
                APIResponse.put("did", DID);
                APIResponse.put("tid", tid);
                APIResponse.put("token", token);
                APIResponse.put("creditsused", creditUsed);
                APIResponse.put("quorumlist", signedQuorumList);
                APIResponse.put("time", totalTime);
                APIResponse.put("status", "Success");
                APIResponse.put("message", token.length() + " tokens mined");

                DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
                Date date = new Date();
                LocalDate currentTime = LocalDate.parse(formatter.format(date).replace("/", "-"));
                JSONObject transactionRecord = new JSONObject();
                transactionRecord.put("role", "Sender");
                transactionRecord.put("tokens", token);
                transactionRecord.put("txn", tid);
                transactionRecord.put("quorumList", signedQuorumList);
                transactionRecord.put("senderDID", DID);
                transactionRecord.put("receiverDID", DID);
                transactionRecord.put("Date", currentTime);
                transactionRecord.put("totalTime", totalTime);
                transactionRecord.put("comment", "minedtxn");

                JSONArray transactionHistoryEntry = new JSONArray();
                transactionHistoryEntry.put(transactionRecord);
                updateJSON("add", WALLET_DATA_PATH + "TransactionHistory.json", transactionHistoryEntry.toString());

                if (!EXPLORER_IP.contains("127.0.0.1")) {

                    String url = EXPLORER_IP.concat("/CreateOrUpdateRubixToken");
                    URL obj = new URL(url);
                    HttpsURLConnection connection_Explorer = (HttpsURLConnection) obj.openConnection();

                    // Setting basic post request
                    connection_Explorer.setRequestMethod("POST");
                    connection_Explorer.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
                    connection_Explorer.setRequestProperty("Accept", "application/json");
                    connection_Explorer.setRequestProperty("Content-Type", "application/json");
                    connection_Explorer.setRequestProperty("Authorization", "null");

                    // Serialization
                    JSONObject dataToSend = new JSONObject();
                    dataToSend.put("bank_id", "01");
                    dataToSend.put("user_did", DID);
                    dataToSend.put("token_id", token);
                    dataToSend.put("level", level);
                    dataToSend.put("denomination", 1);
                    String populate = dataToSend.toString();

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("inputString", populate);
                    String postJsonData = jsonObject.toString();
                    // Send post request
                    connection_Explorer.setDoOutput(true);
                    DataOutputStream wr = new DataOutputStream(connection_Explorer.getOutputStream());
                    wr.writeBytes(postJsonData);
                    wr.flush();
                    wr.close();

                    int responseCodeExplorer = connection_Explorer.getResponseCode();
                    ProofCreditsLogger.debug("Sending 'POST' request to URL : " + url);
                    ProofCreditsLogger.debug("Post Data : " + postJsonData);
                    ProofCreditsLogger.debug("Response Code : " + responseCodeExplorer);

                    BufferedReader in_BR = new BufferedReader(
                            new InputStreamReader(connection_Explorer.getInputStream()));
                    String output;
                    StringBuffer response_Explorer = new StringBuffer();

                    while ((output = in_BR.readLine()) != null) {
                        response_Explorer.append(output);
                    }
                    in_BR.close();

                }

                if (!EXPLORER_IP.contains("127.0.0.1")) {
                    List<String> tokenList = new ArrayList<>();
                    for (int i = 0; i < token.length(); i++)
                        tokenList.add(token.getString(i));
                    String urlTxn = EXPLORER_IP + "/CreateOrUpdateRubixTransaction";
                    URL objTxn = new URL(urlTxn);
                    HttpsURLConnection conTxn = (HttpsURLConnection) objTxn.openConnection();

                    // Setting basic post request
                    conTxn.setRequestMethod("POST");
                    conTxn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
                    conTxn.setRequestProperty("Accept", "application/json");
                    conTxn.setRequestProperty("Content-Type", "application/json");
                    conTxn.setRequestProperty("Authorization", "null");

                    // Serialization
                    JSONObject dataToSend = new JSONObject();
                    dataToSend.put("transaction_id", tid);
                    dataToSend.put("sender_did", DID);
                    dataToSend.put("receiver_did", DID);
                    dataToSend.put("token_id", tokenList);
                    dataToSend.put("token_time", (int) totalTime);
                    dataToSend.put("amount", tokenList.size());
                    String populate = dataToSend.toString();

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("inputString", populate);
                    String postJsonData = jsonObject.toString();

                    // Send post request
                    conTxn.setDoOutput(true);
                    DataOutputStream wrTxn = new DataOutputStream(conTxn.getOutputStream());
                    wrTxn.writeBytes(postJsonData);
                    wrTxn.flush();
                    wrTxn.close();

                    int responseCodeTxn = conTxn.getResponseCode();
                    ProofCreditsLogger.debug("Sending 'POST' request to URL : " + urlTxn);
                    ProofCreditsLogger.debug("Post Data : " + postJsonData);
                    ProofCreditsLogger.debug("Response Code : " + responseCode);

                    BufferedReader inTxn = new BufferedReader(
                            new InputStreamReader(conTxn.getInputStream()));
                    String outputTxn;
                    StringBuffer responseTxn = new StringBuffer();

                    while ((outputTxn = inTxn.readLine()) != null) {
                        responseTxn.append(outputTxn);
                    }
                    inTxn.close();

                    ProofCreditsLogger.debug(responseTxn.toString());
                }
            } else {
                APIResponse.put("did", DID);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "error from mine service");
                ProofCreditsLogger.warn("error from mine service");
                return APIResponse;
            }
        } else {
            APIResponse.put("did", DID);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Insufficient proofs");
            ProofCreditsLogger.warn("Insufficient proof credits to mine");
            return APIResponse;
        }

        return APIResponse;
    }
}
