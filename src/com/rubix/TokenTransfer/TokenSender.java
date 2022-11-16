package com.rubix.TokenTransfer;

import com.rubix.Consensus.InitiatorConsensus;
import com.rubix.Consensus.InitiatorProcedure;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;
import com.rubix.TokenTransfer.TransferPledge.Initiator;
import com.rubix.TokenTransfer.TransferPledge.Unpledge;

import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.bouncycastle.cert.cmp.CertificateConfirmationContent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.rubix.NFTResources.NFTFunctions.*;
import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;

public class TokenSender {
	private static final Logger TokenSenderLogger = Logger.getLogger(TokenSender.class);
	private static final Logger eventLogger = Logger.getLogger("eventLogger");
	public static BufferedReader serverInput;
	private static JSONObject detailsObject = new JSONObject();
	private static JSONArray quorumArray = new JSONArray();
	private static String tid = null;
	private static JSONArray partTokens = new JSONArray();
	private static JSONArray wholeTokens = new JSONArray();
	private static ArrayList alphaPeersList;
	private static ArrayList betaPeersList;
	private static ArrayList gammaPeersList;
	private static int alphaSize = -1;
	private static JSONArray tokenPreviousSender = new JSONArray();
	private static JSONArray wholeTokenChainHash = new JSONArray();
	private static JSONArray partTokenChainHash = new JSONArray();
	public static JSONArray lastObJsonArray = new JSONArray();
	private static JSONObject partTokenChainsPrevState = new JSONObject();
	private static String tokenChainPath = "", tokenPath = "";
	private static JSONObject amountLedger = new JSONObject();
	private static JSONArray allTokens = new JSONArray();
	private static boolean newPart = false, oldNew = false;
	private static Double decimalAmount = -1.00;
	private static String PART_TOKEN_CHAIN_PATH = TOKENCHAIN_PATH.concat("PARTS/");
	private static String PART_TOKEN_PATH = TOKENS_PATH.concat("PARTS/");
	private static String senderPayloadHash = null;
	private static int intPart = -1;
	private static int wholeAmount = -1;
	private static PrintStream output;
	private static BufferedReader input;
	private static Socket senderSocket;
	private static boolean senderMutex = false;
	public static String authSenderByRecHash;

	/**
	 * A sender node to transfer tokens
	 *
	 * @param data Details required for tokenTransfer
	 * @param ipfs IPFS instance
	 * @param port Sender port for communication
	 * @return Transaction Details (JSONObject)
	 * @throws IOException              handles IO Exceptions
	 * @throws JSONException            handles JSON Exceptions
	 * @throws NoSuchAlgorithmException handles No Such Algorithm Exceptions
	 */
	public static JSONObject SendPartA(String data, IPFS ipfs, int port) throws Exception {
		repo(ipfs);
		JSONObject APIResponse = new JSONObject();
		PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

		JSONObject challengeObject = new JSONObject();

		detailsObject = new JSONObject(data);
		String receiverDidIpfsHash = detailsObject.getString("receiverDidIpfsHash");
		syncDataTableByDID(receiverDidIpfsHash);
		String pvt = detailsObject.getString("pvt");
		double requestedAmount = detailsObject.getDouble("amount");
		int type = detailsObject.getInt("type");
		String comment = detailsObject.getString("comment");
		String keyPass = detailsObject.getString("pvtKeyPass");
		PrivateKey pvtKey = null;
		pvtKey = getPvtKey(keyPass, 1);

		// If user enters wrong pvt key password
		if (pvtKey == null) {
			APIResponse.put("message",
					"Incorrect password entered for Private Key, cannot proceed with the transaction");
			TokenSenderLogger.warn("Incorrect Private Key password entered");
			return APIResponse;
		}

		// detailsObject.remove("pvtKeyPass");

		APIResponse = new JSONObject();

		intPart = (int) requestedAmount;
		String senderPeerID = getPeerID(DATA_PATH + "DID.json");
		TokenSenderLogger.debug("sender peer id" + senderPeerID);
		String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);
		TokenSenderLogger.debug("sender did ipfs hash" + senderDidIpfsHash);

		if (senderMutex) {
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Sender busy. Try again later");
			TokenSenderLogger.warn("Sender busy");
			return APIResponse;
		}

		senderMutex = true;

		File partFolder = new File(PART_TOKEN_PATH);
		if (!partFolder.exists())
			partFolder.mkdir();
		partFolder = new File(PART_TOKEN_CHAIN_PATH);
		if (!partFolder.exists())
			partFolder.mkdir();
		File partTokensFile = new File(PAYMENTS_PATH.concat("PartsToken.json"));
		if (!partTokensFile.exists()) {
			partTokensFile.createNewFile();
			writeToFile(partTokensFile.toString(), "[]", false);
		}

		TokenSenderLogger.debug("Requested Part: " + requestedAmount);
		TokenSenderLogger.debug("Int Part: " + intPart);
		String bankFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
		JSONArray bankArray = new JSONArray(bankFile);
		if (intPart <= bankArray.length())
			wholeAmount = intPart;
		else
			wholeAmount = bankArray.length();

		for (int i = 0; i < wholeAmount; i++) {
			wholeTokens.put(bankArray.getJSONObject(i).getString("tokenHash"));
		}

		for (int i = 0; i < wholeTokens.length(); i++) {
			String tokenRemove = wholeTokens.getString(i);
			for (int j = 0; j < bankArray.length(); j++) {
				if (bankArray.getJSONObject(j).getString("tokenHash").equals(tokenRemove))
					bankArray.remove(j);
			}
		}

		for (int i = 0; i < wholeTokens.length(); i++) {
			File token = new File(TOKENS_PATH + wholeTokens.get(i));
			File tokenchain = new File(TOKENCHAIN_PATH + wholeTokens.get(i) + ".json");
			if (!(token.exists() && tokenchain.exists())) {
				TokenSenderLogger.info("Tokens Not Verified");
				senderMutex = false;
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Invalid token(s)");
				return APIResponse;

			}
			String wholeTokenHash = add(TOKENS_PATH + wholeTokens.get(i), ipfs);
			pin(wholeTokenHash, ipfs);
			String tokenChainHash = add(TOKENCHAIN_PATH + wholeTokens.get(i) + ".json", ipfs);
			wholeTokenChainHash.put(tokenChainHash);

			String tokenChainFileContent = readFile(TOKENCHAIN_PATH + wholeTokens.get(i) + ".json");
			JSONArray tokenChainFileArray = new JSONArray(tokenChainFileContent);
			JSONArray previousSenderArray = new JSONArray();

			if (tokenChainFileArray.length() > 0) {
				// JSONObject lastObject =
				// tokenChainFileArray.getJSONObject(tokenChainFileArray.length() - 1);

				TokenSenderLogger.debug("Reading TokenChain file : " + TOKENCHAIN_PATH + wholeTokens.get(i) + ".json");
				for (int j = 0; j < tokenChainFileArray.length(); j++) {

//                    TokenSenderLogger.debug("Reading token chain block = "+j);
					String peerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
							tokenChainFileArray.getJSONObject(j).getString("sender"));
					previousSenderArray.put(peerID);
				}
			}

			JSONObject previousSenderObject = new JSONObject();
			previousSenderObject.put("token", wholeTokenHash);
			previousSenderObject.put("sender", previousSenderArray);
			tokenPreviousSender.put(previousSenderObject);

		}

		decimalAmount = requestedAmount - wholeAmount;
		decimalAmount = formatAmount(decimalAmount);

		TokenSenderLogger.debug("Decimal Part: " + decimalAmount);
		if (decimalAmount > 0.000D) {
			TokenSenderLogger.debug("Decimal Amount > 0.000D");
			String partFileContent = readFile(partTokensFile.toString());
			JSONArray partContentArray = new JSONArray(partFileContent);

			if (partContentArray.length() == 0) {
				newPart = true;
				TokenSenderLogger.debug("New token for parts");
				String chosenToken = bankArray.getJSONObject(0).getString("tokenHash");
				partTokens.put(chosenToken);
				amountLedger.put(chosenToken, formatAmount(decimalAmount));

			} else {
				Double counter = decimalAmount;
				JSONArray selectParts = new JSONArray(partFileContent);
				while (counter > 0.000D) {
					counter = formatAmount(counter);
					TokenSenderLogger.debug("Counter: " + formatAmount(counter));
					if (!(selectParts.length() == 0)) {
						TokenSenderLogger.debug("Old Parts");
						String currentPartToken = selectParts.getJSONObject(0).getString("tokenHash");
						Double currentPartBalance = partTokenBalance(currentPartToken);
						currentPartBalance = formatAmount(currentPartBalance);
						if (counter >= currentPartBalance)
							amountLedger.put(currentPartToken, formatAmount(currentPartBalance));
						else
							amountLedger.put(currentPartToken, formatAmount(counter));

						partTokens.put(currentPartToken);
						counter -= currentPartBalance;
						selectParts.remove(0);
					} else {
						oldNew = true;
						TokenSenderLogger.debug("Old Parts then new parts");
						String chosenToken = bankArray.getJSONObject(0).getString("tokenHash");
						partTokens.put(chosenToken);
						amountLedger.put(chosenToken, formatAmount(counter));
						File tokenFile = new File(TOKENS_PATH.concat(chosenToken));
						tokenFile.renameTo(new File(PART_TOKEN_PATH.concat(chosenToken)));
						File chainFile = new File(TOKENCHAIN_PATH.concat(chosenToken).concat(".json"));
						chainFile.renameTo(new File(PART_TOKEN_CHAIN_PATH.concat(chosenToken).concat(".json")));

						File shiftedFile = new File(PAYMENTS_PATH.concat("ShiftedTokens.json"));
						if (!shiftedFile.exists()) {
							shiftedFile.createNewFile();
							JSONArray shiftedTokensArray = new JSONArray();
							shiftedTokensArray.put(chosenToken);
							writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedTokensArray.toString(),
									false);
						} else {
							String shiftedContent = readFile(PAYMENTS_PATH.concat("ShiftedTokens.json"));
							JSONArray shiftedArray = new JSONArray(shiftedContent);
							shiftedArray.put(chosenToken);
							writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedArray.toString(), false);
						}
						counter = 0.000D;
					}
				}
			}
		}

		if (newPart) {
			tokenChainPath = TOKENCHAIN_PATH;
			tokenPath = TOKENS_PATH;
		} else {
			tokenChainPath = TOKENCHAIN_PATH.concat("PARTS/");
			tokenPath = TOKENS_PATH.concat("PARTS/");
		}

		TokenSenderLogger.debug("Tokenchain path: " + tokenChainPath);
		TokenSenderLogger.debug("Token path: " + tokenPath);
		for (int i = 0; i < partTokens.length(); i++) {
			File token = new File(tokenPath.concat(partTokens.getString(i)));
			File tokenchain = new File(tokenChainPath.concat(partTokens.getString(i)) + ".json");
			if (!(token.exists() && tokenchain.exists())) {
				if (!token.exists())
					TokenSenderLogger.debug("Token File for parts not avail");
				if (!tokenchain.exists())
					TokenSenderLogger.debug("Token Chain File for parts not avail");

				TokenSenderLogger.info("Tokens Not Verified");
				senderMutex = false;
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Invalid part token(s)");
				return APIResponse;

			}
			String hash = add(tokenPath + partTokens.getString(i), ipfs);
			pin(hash, ipfs);

			String chainContent = readFile(tokenChainPath.concat(partTokens.getString(i)).concat(".json"));

			JSONArray chainArray = new JSONArray();
			JSONArray finalChainArray = new JSONArray(chainContent);
			for (int j = 0; j < finalChainArray.length(); j++) {
				JSONObject object = finalChainArray.getJSONObject(j);
				/*
				 * if (finalChainArray.length() == 1) { object.put("previousHash", "");
				 * object.put("nextHash", ""); } else if (finalChainArray.length() > 1) { if (j
				 * == 0) { object.put("previousHash", ""); object.put("nextHash",
				 * calculateHash(finalChainArray.getJSONObject(j + 1).getString("tid"),
				 * "SHA3-256")); } else if (j == finalChainArray.length() - 1) {
				 * object.put("previousHash", calculateHash(finalChainArray.getJSONObject(j -
				 * 1).getString("tid"), "SHA3-256")); object.put("nextHash", ""); } else {
				 * object.put("previousHash", calculateHash(finalChainArray.getJSONObject(j -
				 * 1).getString("tid"), "SHA3-256")); object.put("nextHash",
				 * calculateHash(finalChainArray.getJSONObject(j + 1).getString("tid"),
				 * "SHA3-256")); } }
				 */

				if (object.has("nextHash") && object.has("previousHash")) {
					object.remove("nextHash");
					object.remove("previousHash");
				}

				chainArray.put(object);

			}

			partTokenChainsPrevState.put(partTokens.getString(i), chainArray);
			// TokenSenderLogger.debug("Part token chain to be sent for verification:
			// "+prevStateChainContent);

			writeToFile(tokenChainPath.concat(partTokens.getString(i)).concat(".json"), chainArray.toString(), false);

			partTokenChainHash.put(add(tokenChainPath.concat(partTokens.getString(i)).concat(".json"), ipfs));
		}
		TokenSenderLogger.debug("Completed tokens selection");

		authSenderByRecHash = calculateHash(
				wholeTokens.toString() + wholeTokenChainHash.toString() + partTokens.toString()
						+ partTokenChainHash.toString() + receiverDidIpfsHash + senderDidIpfsHash + comment,
				"SHA3-256");
		TokenSenderLogger.debug("Hash to verify Sender: " + authSenderByRecHash);
		tid = calculateHash(authSenderByRecHash, "SHA3-256");
		TokenSenderLogger.debug("Sender by Receiver Hash " + authSenderByRecHash);
		TokenSenderLogger.debug("TID on sender " + tid);

		challengeObject.put("authSenderByRecHash", authSenderByRecHash);

		for (int i = 0; i < wholeTokens.length(); i++)
			allTokens.put(wholeTokens.getString(i));
		for (int i = 0; i < partTokens.length(); i++)
			allTokens.put(partTokens.getString(i));

		JSONArray alphaQuorum = new JSONArray();
		JSONArray betaQuorum = new JSONArray();
		JSONArray gammaQuorum = new JSONArray();

		switch (type) {
		case 1: {
			writeToFile(LOGGER_PATH + "tempbeta", tid.concat(senderDidIpfsHash), false);
			String betaHash = IPFSNetwork.add(LOGGER_PATH + "tempbeta", ipfs);
			deleteFile(LOGGER_PATH + "tempbeta");

			writeToFile(LOGGER_PATH + "tempgamma", tid.concat(receiverDidIpfsHash), false);
			String gammaHash = IPFSNetwork.add(LOGGER_PATH + "tempgamma", ipfs);
			deleteFile(LOGGER_PATH + "tempgamma");

			quorumArray = getQuorum(senderDidIpfsHash, receiverDidIpfsHash, allTokens.length());
			break;
		}

		case 2: {
			File quorumFile = new File(DATA_PATH.concat("quorumlist.json"));
			if (!quorumFile.exists()) {
				TokenSenderLogger.error("Quorum List for Subnet not found");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Quorum List for Subnet not found");
				return APIResponse;
			} else {
				String quorumList = readFile(DATA_PATH + "quorumlist.json");
				if (quorumList != null) {
					quorumArray = new JSONArray(readFile(DATA_PATH + "quorumlist.json"));
				} else {
					TokenSenderLogger.error("File for Quorum List for Subnet is empty");
					APIResponse.put("status", "Failed");
					APIResponse.put("message", "File for Quorum List for Subnet is empty");
					return APIResponse;
				}

			}

			break;
		}
		case 3: {
			quorumArray = detailsObject.getJSONArray("quorum");
			break;
		}
		default: {
			TokenSenderLogger.error("Unknown quorum type input, cancelling transaction");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Unknown quorum type input, cancelling transaction");
			return APIResponse;

		}
		}

		String errMessage = null;
		for (int i = 0; i < quorumArray.length(); i++) {

			if (quorumArray.get(i).equals(senderDidIpfsHash)) {
				TokenSenderLogger.error("SenderDID " + senderDidIpfsHash + " cannot be a Quorum");
				errMessage = "SenderDID " + senderDidIpfsHash;
			}
			if (quorumArray.get(i).equals(receiverDidIpfsHash)) {
				TokenSenderLogger.error("ReceiverDID " + receiverDidIpfsHash + " cannot be a Quorum");
				if (errMessage != null) {
					errMessage = errMessage + " and ";
				}
				errMessage = "ReceiverDID " + receiverDidIpfsHash;
			}
			if (errMessage != null) {
				APIResponse.put("status", "Failed");
				APIResponse.put("message", errMessage + " cannot be a Quorum ");
				return APIResponse;
			}

		}

		TokenSenderLogger.debug("Updated quorumlist is " + quorumArray.toString());

//        //sanity check for Quorum - starts
		int alphaCheck = 0, betaCheck = 0, gammaCheck = 0;
		JSONArray sanityFailedQuorum = new JSONArray();
		for (int i = 0; i < quorumArray.length(); i++) {
			String quorumPeerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
					quorumArray.getString(i));
			boolean quorumSanityCheck = sanityCheck("Quorum", quorumPeerID, ipfs, port + 11);

			if (!quorumSanityCheck) {
				sanityFailedQuorum.put(quorumPeerID);
				if (i <= 6)
					alphaCheck++;
				if (i >= 7 && i <= 13)
					betaCheck++;
				if (i >= 14 && i <= 20)
					gammaCheck++;
			}
		}

		if (alphaCheck > 2 || betaCheck > 2 || gammaCheck > 2) {
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			String message = "Quorum: ".concat(sanityFailedQuorum.toString()).concat(" ");
			APIResponse.put("message", message.concat(sanityMessage));
			TokenSenderLogger.warn("Quorum: ".concat(message.concat(sanityMessage)));
			return APIResponse;
		}
//        //sanity check for Quorum - Ends
		long startTime, endTime, totalTime;

		QuorumSwarmConnect(quorumArray, ipfs);

		alphaSize = quorumArray.length() - 14;

		for (int i = 0; i < alphaSize; i++)
			alphaQuorum.put(quorumArray.getString(i));

		for (int i = 0; i < 7; i++) {
			betaQuorum.put(quorumArray.getString(alphaSize + i));
			gammaQuorum.put(quorumArray.getString(alphaSize + 7 + i));
		}
		startTime = System.currentTimeMillis();

		alphaPeersList = QuorumCheck(alphaQuorum, alphaSize);
		betaPeersList = QuorumCheck(betaQuorum, 7);
		gammaPeersList = QuorumCheck(gammaQuorum, 7);

		endTime = System.currentTimeMillis();
		totalTime = endTime - startTime;
		eventLogger.debug("Quorum Check " + totalTime);

		if (alphaPeersList.size() < minQuorum(alphaSize) || betaPeersList.size() < 5 || gammaPeersList.size() < 5) {
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Quorum Members not available");
			TokenSenderLogger.warn("Quorum Members not available");
			senderMutex = false;
			return APIResponse;
		}

		TokenSenderLogger.debug("Final Selected Alpha Members: " + alphaPeersList);
		JSONArray alphaList = new JSONArray();
		for (int i = 0; i < alphaPeersList.size(); i++) {
			alphaList.put(alphaPeersList.get(i));
		}

		int numberOfTokensToPledge = 0;
		if (wholeAmount > 0) {
			numberOfTokensToPledge += wholeAmount;
			if (decimalAmount > 0)
				numberOfTokensToPledge += 1;
		} else
			numberOfTokensToPledge = 1;

		TokenSenderLogger.debug("Amount being transferred: " + requestedAmount
				+ " and number of tokens required to be pledged: " + numberOfTokensToPledge);

		JSONObject dataToSendToInitiator = new JSONObject();
		dataToSendToInitiator.put("alphaList", alphaList);
		dataToSendToInitiator.put("tokenList", wholeTokens);
		dataToSendToInitiator.put("amount", numberOfTokensToPledge);
		dataToSendToInitiator.put("tid", tid);
		dataToSendToInitiator.put("pvt", pvt);
		dataToSendToInitiator.put("pvtKeyPass", keyPass);
		dataToSendToInitiator.put("sender", senderDidIpfsHash);
		dataToSendToInitiator.put("receiver", receiverDidIpfsHash);

		TokenSenderLogger.debug("Details being sent to Initiator: " + dataToSendToInitiator);

		boolean abort = Initiator.pledgeSetUp(dataToSendToInitiator.toString(), ipfs, 22143);
		if (abort) {
			Initiator.abort = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			if (Initiator.abortReason.has("Quorum"))
				APIResponse.put("message", "Alpha Node " + Initiator.abortReason.getString("Quorum") + " "
						+ Initiator.abortReason.getString("Reason"));
			else
				APIResponse.put("message", Initiator.abortReason.getString("Reason"));
			TokenSenderLogger.warn("Quorum Members with insufficient Tokens/Credits");
			senderMutex = false;
			Initiator.abortReason = new JSONObject();
			return APIResponse;
		} else {
			JSONArray array = Initiator.quorumWithHashesArray;
			challengeObject.put("pledgeDetails", array);

		}

		TokenSenderLogger.debug("Nodes that pledged tokens: " + Initiator.pledgedNodes);

		/**
		 * Send the array without sender sign calculated
		 */
		JSONArray newChains = new JSONArray();
		for (int i = 0; i < intPart; i++) {
			JSONObject lastObject = new JSONObject();
			// lastObject.put("senderSign", "");
			lastObject.put("sender", senderDidIpfsHash);
			lastObject.put("group", allTokens);
			lastObject.put("comment", comment);
			lastObject.put("tid", tid);
			lastObject.put("receiver", receiverDidIpfsHash);
			lastObject.put("pledgeToken", Initiator.pledgedNodes);
			lastObject.put("tokensPledgedFor", allTokens);

			String tokenChainFileContent = readFile(TOKENCHAIN_PATH + wholeTokens.get(i) + ".json");
			JSONArray tokenChain = new JSONArray(tokenChainFileContent);
			tokenChain.put(lastObject);
			lastObJsonArray.put(lastObject);

			String tokenChainHash = calculateHash(tokenChain.toString(), "SHA3-256");
			JSONObject tokenObject = new JSONObject();
			tokenObject.put("token", wholeTokens.getString(i));
			tokenObject.put("hash", tokenChainHash);
			newChains.put(tokenObject);
		}
		challengeObject.put("lastObject", newChains);
		senderPayloadHash = calculateHash(allTokens.toString() + senderDidIpfsHash + comment, "SHA3-256");

		challengeObject.put("senderPayloadSign", senderPayloadHash);

		FileWriter file = new FileWriter(WALLET_DATA_PATH.concat("/ChallengePayload").concat(tid).concat(".json"));
		file.write(challengeObject.toString());
		file.close();

		JSONArray signedChains = new JSONArray();
		JSONObject payloadSigned = new JSONObject();
		File privateShareFile = new File(DATA_PATH.concat(senderDidIpfsHash).concat("/PrivateShare.png"));

		if (privateShareFile.exists()) {
			payloadSigned.put("authSenderByRecHash", getSignFromShares(pvt, authSenderByRecHash));
			payloadSigned.put("senderPayloadSign", getSignFromShares(pvt, senderPayloadHash));
			for (int i = 0; i < intPart; i++) {
				JSONObject tokenObject = newChains.getJSONObject(i);
				// JSONArray chainArray = tokenObject.getJSONArray("chain");
				// JSONObject lastObject = chainArray.getJSONObject(chainArray.length() - 1);
				// String senderSign = getSignFromShares(pvt, authSenderByRecHash);
				// lastObject.put("senderSign", senderSign);

				// chainArray.remove(chainArray.length() - 1);
				// chainArray.put(lastObject);

				// String chainHash = calculateHash(chainArray.toString(), "SHA3-256");
				String chainSign = getSignFromShares(pvt, newChains.getJSONObject(i).getString("hash"));
				tokenObject.put("chainSign", chainSign);
				signedChains.put(tokenObject);
			}
			payloadSigned.put("lastObject", signedChains);

			FileWriter payloadfile = new FileWriter(
					WALLET_DATA_PATH.concat("/SignPayload").concat(tid).concat(".json"));
			payloadfile.write(payloadSigned.toString());
			payloadfile.close();

			JSONArray quorumWithSignsArray = new JSONArray();
			JSONArray pledgeArray = Initiator.quorumWithHashesArray;
			if (pledgeArray.length() == 0) {
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Not enough token to pledge");
				TokenSenderLogger.warn("Pledging failed");
				return APIResponse;
			}

			TokenSenderLogger.debug("pledge object is " + pledgeArray.toString());

			JSONObject finalSignObject = new JSONObject();
			JSONArray signsArray = new JSONArray();

			for (int i = 0; i < Initiator.quorumWithHashesArray.length(); i++) {
				JSONObject jsonObject = Initiator.quorumWithHashesArray.getJSONObject(i);
				Iterator<String> keys = jsonObject.keys();
				TokenSenderLogger.debug("jsonObject  is " + jsonObject.toString());
				JSONObject pledgeSignedObject = new JSONObject();
				String key = "";
				while (keys.hasNext()) {
					key = keys.next();
					TokenSenderLogger.debug("key of quorumn is " + key);
					if (jsonObject.get(key) instanceof JSONArray) {
						// do something with jsonObject here
						JSONArray hashArray = new JSONArray(jsonObject.get(key).toString());
						for (int j = 0; j < hashArray.length(); j++) {
							String sign = getSignFromShares(pvt, hashArray.get(j).toString());
							pledgeSignedObject.put("hash", hashArray.get(j));
							pledgeSignedObject.put("sign", sign);
						}
					}
				}
				JSONObject signObject = new JSONObject();
				signObject.put(key, pledgeSignedObject);
				TokenSenderLogger.debug("signObject is "+signObject);
				quorumWithSignsArray.put(signObject);

			}
			

//			for (int i = 0; i < intPart; i++) {
//				JSONObject jsonObject = new JSONObject(Initiator.quorumWithHashesArray);
//				TokenSenderLogger.debug("jsonObject " + jsonObject.toString());
//				Iterator<String> keys = jsonObject.keys();
//
//				TokenSenderLogger.debug("iteratir keys is " + keys.next().toString());
//
//				String key = "";
//				while (keys.hasNext()) {
//					key = keys.next();
//					TokenSenderLogger.debug("key of quorumn is " + key);
//					if (jsonObject.get(key) instanceof JSONObject) {
//						// do something with jsonObject here
//						JSONArray hashArray = jsonObject.getJSONArray(key);
//						for (int j = 0; j < hashArray.length(); j++) {
//							String sign = getSignFromShares(pvt, hashArray.getString(j));
//							signsArray.put(sign);
//						}
//
//					}
//				}
//
//				TokenSenderLogger.debug("sign array is " + signsArray.toString());
//
//				JSONObject signObject = new JSONObject();
//				signObject.put("quorum", key);
//				signObject.put("sign", signsArray);
//				quorumWithSignsArray.put(signObject);
//			}
			
			payloadSigned.put("pledgeDetails", quorumWithSignsArray);

			FileWriter spfile = new FileWriter(WALLET_DATA_PATH.concat("/signedPayload").concat(tid).concat(".json"));
			spfile.write(payloadSigned.toString());
			spfile.close();

			senderMutex = false;
			return SendPartB(payloadSigned, ipfs, port);
		}
		senderMutex = false;
		return challengeObject;
	}

	public static JSONObject SendPartB(JSONObject signPayload, IPFS ipfs, int port)
			throws JSONException, IOException, InterruptedException, ParseException {

		TokenSenderLogger.debug("PartB - signPayload " + signPayload);
		
		String senderSign = signPayload.getString("authSenderByRecHash");
		JSONObject APIResponse = new JSONObject();
		String receiverDidIpfsHash = detailsObject.getString("receiverDidIpfsHash");
		String receiverPeerId = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", receiverDidIpfsHash);
		String pvt = detailsObject.getString("pvt");
		double requestedAmount = detailsObject.getDouble("amount");
		int type = detailsObject.getInt("type");
		String comment = detailsObject.getString("comment");
		String keyPass = detailsObject.getString("pvtKeyPass");
		PrivateKey pvtKey = getPvtKey(keyPass, 1);
		String senderPeerID = getPeerID(DATA_PATH + "DID.json");
		String senderDidIpfsHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);
		JSONArray quorumWithSignsArray = signPayload.getJSONArray("pledgeDetails");
		JSONArray signedChains = signPayload.getJSONArray("lastObject");

		

		// If user enters wrong pvt key password
		if (pvtKey == null) {
			APIResponse.put("message",
					"Incorrect password entered for Private Key, cannot proceed with the transaction");
			TokenSenderLogger.warn("Incorrect Private Key password entered");
			return APIResponse;
		}

		detailsObject.remove("pvtKeyPass");
		if (senderMutex) {
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Sender busy. Try again later");
			TokenSenderLogger.warn("Sender busy");
			return APIResponse;
		}

		senderMutex = true;
		Initiator.pledge(quorumWithSignsArray, requestedAmount, port);

		boolean sanityCheck = sanityCheck("Receiver", receiverPeerId, ipfs, port + 10);
		if (!sanityCheck) {
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", sanityMessage);
			TokenSenderLogger.warn(sanityMessage);
			return APIResponse;
		}

		syncDataTable(receiverDidIpfsHash, null);

		if (!receiverPeerId.equals("")) {
			TokenSenderLogger.debug("Swarm connecting to " + receiverPeerId);
			swarmConnectP2P(receiverPeerId, ipfs);
			TokenSenderLogger.debug("Swarm connected");
		} else {
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver Peer ID null");
			TokenSenderLogger.warn("Receiver Peer ID null");
			senderMutex = false;
			return APIResponse;
		}

		String receiverWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash",
				receiverDidIpfsHash);
		if (!receiverWidIpfsHash.equals("")) {
			nodeData(receiverDidIpfsHash, receiverWidIpfsHash, ipfs);
		} else {
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver WID null");
			TokenSenderLogger.warn("Receiver WID null");
			senderMutex = false;
			return APIResponse;
		}

		TokenSenderLogger.debug("Sender IPFS forwarding to DID: " + receiverDidIpfsHash + " PeerID: " + receiverPeerId);
		forward(receiverPeerId, port, receiverPeerId);
		TokenSenderLogger.debug("Forwarded to " + receiverPeerId + " on " + port);
		senderSocket = new Socket("127.0.0.1", port);

		input = new BufferedReader(new InputStreamReader(senderSocket.getInputStream()));
		output = new PrintStream(senderSocket.getOutputStream());

		long startTime = System.currentTimeMillis();

		/**
		 * Sending Sender Peer ID to Receiver Receiver to authenticate Sender's DID
		 * (Identity)
		 */
		output.println(senderPeerID);
		TokenSenderLogger.debug("Sent PeerID");

		String peerAuth;
		try {
			peerAuth = input.readLine();
		} catch (SocketException e) {
			TokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Sender Auth");
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver " + receiverDidIpfsHash + "is unable to respond! - Sender Auth");

			return APIResponse;
		}

		if (peerAuth == null) {
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			TokenSenderLogger.info("Receiver is unable to authenticate the sender!");
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver is unable to authenticate the sender!");
			return APIResponse;

		} else if (peerAuth != null && (!peerAuth.equals("200"))) {
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			TokenSenderLogger.info("Sender Data Not Available");
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Sender Data Not Available");
			return APIResponse;

		}

		String pvtKeyType = privateKeyAlgorithm(1);
	//	String senderSignWithPvtKey = pvtKeySign(senderSign, pvtKey, pvtKeyType);

		JSONObject senderDetails2Receiver = new JSONObject();
	//	senderDetails2Receiver.put("pvtKeySign", senderSignWithPvtKey);
		senderDetails2Receiver.put("pvtShareBits", senderSign);
		senderDetails2Receiver.put("tid", tid);
		senderDetails2Receiver.put("comment", comment);

		JSONObject partTokenChainArrays = new JSONObject();
		for (int i = 0; i < partTokens.length(); i++) {
			String chainContent = readFile(tokenChainPath.concat(partTokens.getString(i)).concat(".json"));
			JSONArray chainArray = new JSONArray(chainContent);
			JSONObject newLastObject = new JSONObject();
			/*
			 * if (chainArray.length() == 0) { newLastObject.put("previousHash", "");
			 * 
			 * } else { JSONObject secondLastObject =
			 * chainArray.getJSONObject(chainArray.length() - 1);
			 * secondLastObject.put("nextHash", calculateHash(tid, "SHA3-256"));
			 * newLastObject.put("previousHash",
			 * calculateHash(chainArray.getJSONObject(chainArray.length() -
			 * 1).getString("tid"), "SHA3-256")); }
			 */

			Double amount = formatAmount(amountLedger.getDouble(partTokens.getString(i)));

			newLastObject.put("senderSign", senderSign);
			newLastObject.put("sender", senderDidIpfsHash);
			newLastObject.put("receiver", receiverDidIpfsHash);
			newLastObject.put("comment", comment);
			newLastObject.put("tid", tid);
			newLastObject.put("role", "Sender");
			newLastObject.put("amount", amount);
			chainArray.put(newLastObject);
			partTokenChainArrays.put(partTokens.getString(i), chainArray);

		}
		JSONArray proofOfWork = new JSONArray();
		for (int i = 0; i < wholeTokens.length(); i++) {
			File proofFile = new File(TOKENCHAIN_PATH + "Proof/" + wholeTokens.getString(i) + ".proof");
			if (proofFile.exists()) {
				String proofCID = add(TOKENCHAIN_PATH + "Proof/" + wholeTokens.getString(i) + ".proof", ipfs);
				JSONObject proofObject = new JSONObject();
				proofObject.put("token", wholeTokens.getString(i));
				proofObject.put("cid", proofCID);
				proofOfWork.put(proofObject);
			}
		}
		JSONObject tokenDetails = new JSONObject();
		tokenDetails.put("whole-tokens", wholeTokens);
		tokenDetails.put("whole-tokenChains", wholeTokenChainHash);
		tokenDetails.put("hashSender", partTokenChainHash);
		tokenDetails.put("part-tokens", partTokens);
		tokenDetails.put("part-tokenChains", partTokenChainArrays);
		tokenDetails.put("part-tokenChains-PrevState", partTokenChainsPrevState);
		tokenDetails.put("sender", senderDidIpfsHash);
		tokenDetails.put("proof", proofOfWork);
		String doubleSpendString = tokenDetails.toString();

		String doubleSpend = calculateHash(doubleSpendString, "SHA3-256");
		writeToFile(LOGGER_PATH + "doubleSpend", doubleSpend, false);
		TokenSenderLogger.debug("********Double Spend Hash*********:  " + doubleSpend);
		IPFSNetwork.addHashOnly(LOGGER_PATH + "doubleSpend", ipfs);
		deleteFile(LOGGER_PATH + "doubleSpend");

		JSONObject tokenObject = new JSONObject();
		tokenObject.put("tokenDetails", tokenDetails);
		tokenObject.put("previousSender", tokenPreviousSender);
		tokenObject.put("amount", requestedAmount);
		tokenObject.put("amountLedger", amountLedger);

		if (Functions.multiplePinCheck(senderDidIpfsHash, tokenObject, ipfs) == 420) {
			APIResponse.put("message", "Multiple Owners Found. Kindly re-initiate transaction");
			return APIResponse;
		} else {
			TokenSenderLogger.debug("No Multiple Pins found, initating transcation");
		}

		/**
		 * Sending Token Details to Receiver Receiver to authenticate Tokens (Double
		 * Spending, IPFS availability)
		 */
		output.println(tokenObject);

		String tokenAuth;
		try {
			tokenAuth = input.readLine();
			TokenSenderLogger.debug("Token Auth Code: " + tokenAuth);
		} catch (SocketException e) {
			TokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Token Auth");
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver " + receiverDidIpfsHash + "is unable to respond! - Token Auth");

			return APIResponse;
		}
		if (tokenAuth == null) {
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			TokenSenderLogger.info("Receiver is unable to verify the tokens!");
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver is unable to verify the tokens!");
			return APIResponse;
		} else if (tokenAuth != null && (tokenAuth.startsWith("4"))) {
			switch (tokenAuth) {
			case "419":
				String pledgedTokens = input.readLine();
				JSONArray pledgedTokensArray = new JSONArray(pledgedTokens);
				TokenSenderLogger.info("These tokens are pledged " + pledgedTokensArray);
				TokenSenderLogger.info("Kindly re-initiate transaction");
				APIResponse.put("message", "Pledged Tokens " + pledgedTokensArray + ". Kindly re-initiate transaction");
				File pledgeFile = new File(PAYMENTS_PATH.concat("PledgedTokens.json"));
				if (!pledgeFile.exists()) {
					pledgeFile.createNewFile();
					writeToFile(PAYMENTS_PATH.concat("PledgedTokens.json"), pledgedTokensArray.toString(), false);
				} else {
					String pledgedContent = readFile(PAYMENTS_PATH.concat("PledgedTokens.json"));
					JSONArray pledgedArray = new JSONArray(pledgedContent);
					for (int i = 0; i < pledgedTokensArray.length(); i++) {
						pledgedArray.put(pledgedTokensArray.getJSONObject(i));
					}
					writeToFile(PAYMENTS_PATH.concat("PledgedTokens.json"), pledgedArray.toString(), false);
				}
				break;
			case "420":
				String doubleSpent = input.readLine();
				String owners = input.readLine();
				JSONArray ownersArray = new JSONArray(owners);
				TokenSenderLogger.info("Multiple Owners for " + doubleSpent);
				TokenSenderLogger.info("Owners " + ownersArray);
				TokenSenderLogger.info("Kindly re-initiate transaction");
				APIResponse.put("message", "Multiple Owners for " + doubleSpent + " Owners: " + ownersArray
						+ ". Kindly re-initiate transaction");
				break;
			case "421":
				TokenSenderLogger.info("Consensus ID not unique. Kindly re-initiate transaction");
				APIResponse.put("message", "Consensus ID not unique. Kindly re-initiate transaction");
				break;
			case "422":
				TokenSenderLogger.info("Tokens Not Verified. Kindly re-initiate transaction");
				APIResponse.put("message", "Tokens Not Verified. Kindly re-initiate transaction");
				break;
			case "423":
				TokenSenderLogger.info("Broken Cheque Chain. Kindly re-initiate transaction");
				APIResponse.put("message", "Broken Cheque Chain. Kindly re-initiate transaction");
				break;

			case "425":
				TokenSenderLogger.info("Token wholly spent already. Kindly re-initiate transaction");
				APIResponse.put("message", "Token wholly spent already. Kindly re-initiate transaction");
				break;

			case "426":
				TokenSenderLogger.info("Contains Invalid Tokens. Kindly check tokens in your wallet");
				APIResponse.put("message", "Contains Invalid Tokens. Kindly check tokens in your wallet");
				break;

			case "430":
				TokenSenderLogger
						.info("Token chain verification has failed. Whole token chain/chains could not be verified.");
				APIResponse.put("message", "Token Chain/(s) could not be verified.");
				break;

			case "431":
				TokenSenderLogger
						.info("Token chain verification has failed. Part token chain/chains could not be verified.");
				APIResponse.put("message", "Token Chain/(s) could not be verified.");
				break;

			}
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			return APIResponse;
		}

		TokenSenderLogger.debug("Token Auth Code: " + tokenAuth);

		JSONObject dataObject = new JSONObject();
		dataObject.put("tid", tid);
		dataObject.put("senderPayloadHash", senderPayloadHash);
		dataObject.put("senderPayloadSign", signPayload.getString("senderPayloadSign"));
		dataObject.put("receiverDidIpfs", receiverDidIpfsHash);
		dataObject.put("pvt", pvt);
		dataObject.put("senderDidIpfs", senderDidIpfsHash);
		dataObject.put("token", wholeTokens.toString());
		dataObject.put("alphaList", alphaPeersList);
		dataObject.put("betaList", betaPeersList);
		dataObject.put("gammaList", gammaPeersList);
		dataObject.put("sign", signPayload.getString("authSenderByRecHash"));
		dataObject.put("hash", authSenderByRecHash);

		InitiatorProcedure.consensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 100, alphaSize, "");

		if (InitiatorConsensus.quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
			TokenSenderLogger.debug("Consensus Failed");
			senderDetails2Receiver.put("status", "Consensus Failed");
			output.println(senderDetails2Receiver);
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Transaction declined by Quorum");
			return APIResponse;

		}

		TokenSenderLogger.debug("Consensus Reached");
		senderDetails2Receiver.put("status", "Consensus Reached");
		senderDetails2Receiver.put("quorumsign", InitiatorConsensus.quorumSignature.toString());
		senderDetails2Receiver.put("pledgeDetails", Initiator.pledgedNodes);

		output.println(senderDetails2Receiver);
		TokenSenderLogger.debug("Quorum Signatures length " + InitiatorConsensus.quorumSignature.length());

		String signatureAuth;
		try {
			signatureAuth = input.readLine();
		} catch (SocketException e) {
			TokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Signature Auth");
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver " + receiverDidIpfsHash + "is unable to respond! - Signature Auth");

			return APIResponse;
		}
		TokenSenderLogger.info("signatureAuth : " + signatureAuth);
		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		if (signatureAuth == null) {
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			TokenSenderLogger.info("Receiver is unable to authenticate Sender!");
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver is unable to authenticate Sender!");
			return APIResponse;
		} else if (signatureAuth != null && (!signatureAuth.equals("200"))) {
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			TokenSenderLogger.info("Authentication Failed!");
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Sender not authenticated");
			return APIResponse;

		}

		// Sender requests for new block that is going to be added to the Token chains.
		// (For Token chain auth)
		output.println("Request for new blocks being added to the Token Chains");

		String newBlocksForTokenChains;
		try {
			newBlocksForTokenChains = input.readLine();
		} catch (SocketException e) {
			TokenSenderLogger.warn(
					"Receiver " + receiverDidIpfsHash + " could'nt send token chain blocks for hashing and signing");
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message",
					"Receiver " + receiverDidIpfsHash + "couldn't send token chain blocks for hashing and signing");

			return APIResponse;
		}

		TokenSenderLogger.debug("newBlocksForTokenChains is "+newBlocksForTokenChains);
		TokenSenderLogger.debug("");

		TokenSenderLogger.debug("lastObJsonArray is "+lastObJsonArray.toString());

		
		JSONArray newTokenChainBlocks = new JSONArray(newBlocksForTokenChains);
		JSONArray hashAndSigns = new JSONArray();

		for (int i = 0; i < intPart; i++) {
			JSONObject lastObject = lastObJsonArray.getJSONObject(i);
			JSONObject receiverLastObject = newTokenChainBlocks.getJSONObject(i);

			Map<String, Object> senderChainMap = lastObject.toMap();
			Map<String, Object> receiverChainMap = receiverLastObject.toMap();
			senderChainMap.remove("pledgeToken");
			receiverChainMap.remove("pledgeToken");

			TokenSenderLogger.debug("--------");
			TokenSenderLogger.debug("senderChainMap   " + senderChainMap.keySet().toString());
			TokenSenderLogger.debug("senderChainMap   " + senderChainMap.values().toString());

			TokenSenderLogger.debug("--------");
			TokenSenderLogger.debug("receiverChainMap   " + receiverChainMap.keySet().toString());
			TokenSenderLogger.debug("receiverChainMap   " + receiverChainMap.values().toString());

			TokenSenderLogger.debug("--------");

			TokenSenderLogger.debug(senderChainMap.equals(receiverChainMap) + " is the chainmap status");
			
			TokenSenderLogger.debug("signedChains is "+signedChains.toString());
			if (senderChainMap.equals(receiverChainMap)) {

			//	String PvtKeySign = pvtKeySign(signedChains.getJSONObject(i).getString("chainSign"), pvtKey,
			//			privateKeyAlgorithm(1));

				JSONObject obj = new JSONObject();
				obj.put("hash", signedChains.getJSONObject(i).getString("hash"));
				obj.put("pvtShareBits", signedChains.getJSONObject(i).getString("chainSign"));
			//	obj.put("pvtKeySign", PvtKeySign);

				hashAndSigns.put(obj);
			} else {
				output.println("Token chains Not Matching");
				executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
				TokenSenderLogger.info("Token chains Not Matching");
				output.close();
				input.close();
				senderSocket.close();
				senderMutex = false;
				updateQuorum(quorumArray, null, false, type);
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", tid);
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Token chains Not Matching");
				return APIResponse;
			}
		}

		output.println(hashAndSigns.toString());
		// Sender requests for new block that is going to be added to the part Token
		// chains.
//        output.println("Request for Part Token Chains to be hashed");

//        String req_newPartTokenChains;
//        try {
//            req_newPartTokenChains = input.readLine();
//            TokenSenderLogger.debug("Hashing and Signing Part Token Chains.");
//        } catch (SocketException e) {
//            TokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " could'nt send part token chain blocks for hashing and signing");
//            executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
//            output.close();
//            input.close();
//            senderSocket.close();
//            senderMutex = false;
//            updateQuorum(quorumArray, null, false, type);
//            APIResponse.put("did", senderDidIpfsHash);
//            APIResponse.put("tid", "null");
//            APIResponse.put("status", "Failed");
//            APIResponse.put("message", "Receiver " + receiverDidIpfsHash + "could'nt send part token chain blocks for hashing and signing");
//
//            return APIResponse;
//        }
//
//        JSONObject newPartTokenChains= new JSONObject(req_newPartTokenChains);
//

//        output.println(hashesAndSigns_partTokenChains.toString()); //Sending the hashes and signs for the part token chains sent by receiver.

		TokenSenderLogger.debug("Unpinned Tokens");
		output.println("Unpinned");

		String confirmation;
		try {
			confirmation = input.readLine();
		} catch (SocketException e) {
			TokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Pinning Auth");
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver " + receiverDidIpfsHash + "is unable to respond! - Pinning Auth");

			return APIResponse;
		}
		if (confirmation == null) {
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			TokenSenderLogger.info("Receiver is unable to Pin the tokens!");
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver is unable to Pin the tokens!");
			return APIResponse;

		} else if (confirmation != null && (!confirmation.equals("Successfully Pinned"))) {
			TokenSenderLogger.warn("Multiple Owners for the token");
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			TokenSenderLogger.info("Tokens with multiple pins");
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Tokens with multiple pins");
			return APIResponse;

		}

		TokenSenderLogger.debug("3");
		TokenSenderLogger.debug("Whole tokens: " + wholeTokens);
		TokenSenderLogger.debug("Part tokens: " + partTokens);
		output.println(InitiatorProcedure.essential);

		String respAuth;
		try {
			respAuth = input.readLine();
		} catch (SocketException e) {
			TokenSenderLogger.warn("Receiver " + receiverDidIpfsHash + " is unable to Respond! - Share Confirmation");
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message",
					"Receiver " + receiverDidIpfsHash + "is unable to respond! - Share Confirmation");

			return APIResponse;
		}
		if (respAuth == null) {
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			TokenSenderLogger.info("Receiver is unable to complete the transaction!");
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver is unable to complete the transaction!");
			return APIResponse;

		} else if (respAuth != null && (!respAuth.equals("Send Response"))) {
			executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Receiver process not over");
			TokenSenderLogger.info("Incomplete Transaction");
			return APIResponse;
		}

		TokenSenderLogger.debug("Operation over");

		for (int i = 0; i < wholeTokens.length(); i++)
			unpin(String.valueOf(wholeTokens.get(i)), ipfs);
		repo(ipfs);

		/*
		 * Iterator<String> keys = InitiatorConsensus.quorumSignature.keys(); JSONArray
		 * signedQuorumList = new JSONArray(); while (keys.hasNext())
		 * signedQuorumList.put(keys.next());
		 */

		JSONArray QuorumSignatures = new JSONArray(InitiatorConsensus.quorumSignature.toString());
		JSONArray signedQuorumList = new JSONArray();
		JSONObject temp = new JSONObject();

		for (int i = 0; i < QuorumSignatures.length(); i++) {

			temp = QuorumSignatures.getJSONObject(i);
			signedQuorumList.put(temp.getString("quorum_did"));
		}

		APIResponse.put("tid", tid);
		APIResponse.put("status", "Success");
		APIResponse.put("did", senderDidIpfsHash);
		APIResponse.put("message", "Tokens transferred successfully!");
		APIResponse.put("quorumlist", signedQuorumList);
		APIResponse.put("receiver", receiverDidIpfsHash);
		APIResponse.put("totaltime", totalTime);

		JSONObject transactionRecord = new JSONObject();
		transactionRecord.put("role", "Sender");
		transactionRecord.put("tokens", allTokens);
		transactionRecord.put("txn", tid);
		transactionRecord.put("quorumList", signedQuorumList);
		transactionRecord.put("senderDID", senderDidIpfsHash);
		transactionRecord.put("receiverDID", receiverDidIpfsHash);
		transactionRecord.put("Date", getCurrentUtcTime());
		transactionRecord.put("totalTime", totalTime);
		transactionRecord.put("comment", comment);
		transactionRecord.put("essentialShare", InitiatorProcedure.essential);
		requestedAmount = formatAmount(requestedAmount);
		transactionRecord.put("amount-spent", requestedAmount);

		JSONArray transactionHistoryEntry = new JSONArray();
		transactionHistoryEntry.put(transactionRecord);

		updateJSON("add", WALLET_DATA_PATH + "TransactionHistory.json", transactionHistoryEntry.toString());

		for (int i = 0; i < wholeTokens.length(); i++) {
			deleteFile(TOKENS_PATH.concat(wholeTokens.getString(i)));
			Functions.updateJSON("remove", PAYMENTS_PATH.concat("BNK00.json"), wholeTokens.getString(i));
		}
		if(decimalAmount != 0.0) {

		if (newPart) {
			TokenSenderLogger.debug("Updating files for new parts");
			JSONObject newPartTokenObject = new JSONObject();
			newPartTokenObject.put("tokenHash", partTokens.getString(0));
			JSONArray newPartArray = new JSONArray();
			newPartArray.put(newPartTokenObject);
			writeToFile(PAYMENTS_PATH.concat("PartsToken.json"), newPartArray.toString(), false);

			String bankNew = readFile(PAYMENTS_PATH.concat("BNK00.json"));
			JSONArray bankNewArray = new JSONArray(bankNew);
			bankNewArray.remove(0);
			writeToFile(PAYMENTS_PATH.concat("BNK00.json"), bankNewArray.toString(), false);

			String newTokenChain = readFile(TOKENCHAIN_PATH + partTokens.getString(0) + ".json");
			JSONArray chainArray = new JSONArray(newTokenChain);

			JSONObject newLastObject = new JSONObject();
			/*
			 * if (chainArray.length() == 0) { newLastObject.put("previousHash", "");
			 * 
			 * } else { JSONObject secondLastObject =
			 * chainArray.getJSONObject(chainArray.length() - 1);
			 * secondLastObject.put("nextHash", calculateHash(tid, "SHA3-256"));
			 * newLastObject.put("previousHash",
			 * calculateHash(chainArray.getJSONObject(chainArray.length() -
			 * 1).getString("tid"), "SHA3-256")); }
			 */

			Double amount = formatAmount(decimalAmount);

			newLastObject.put("senderSign", senderSign);
			newLastObject.put("sender", senderDidIpfsHash);
			newLastObject.put("receiver", receiverDidIpfsHash);
			newLastObject.put("comment", comment);
			newLastObject.put("tid", tid);
			// newLastObject.put("nextHash", "");
			newLastObject.put("role", "Sender");
			newLastObject.put("amount", amount);
			chainArray.put(newLastObject);

			output.println("New part token chain to be hashed");
			output.println(chainArray.toString());

			String finalPartTokenChain_string;
			try {
				finalPartTokenChain_string = input.readLine();
			} catch (SocketException e) {
				TokenSenderLogger.warn(
						"Receiver " + receiverDidIpfsHash + " could'nt send hahsed and signed token chain blocks.");
				executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
				output.close();
				input.close();
				senderSocket.close();
				senderMutex = false;
				updateQuorum(quorumArray, null, false, type);
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message",
						"Receiver " + receiverDidIpfsHash + " could'nt send hahsed and signed token chain blocks.");

				return APIResponse;
			}

			JSONArray newPartTokenChain = new JSONArray(finalPartTokenChain_string);
			writeToFile(TOKENCHAIN_PATH + partTokens.getString(0) + ".json", newPartTokenChain.toString(), false);

			File tokenFile = new File(TOKENS_PATH.concat(partTokens.getString(0)));
			tokenFile.renameTo(new File(PART_TOKEN_PATH.concat(partTokens.getString(0))));
			File chainFile = new File(TOKENCHAIN_PATH.concat(partTokens.getString(0)).concat(".json"));
			chainFile.renameTo(new File(PART_TOKEN_CHAIN_PATH.concat(partTokens.getString(0)).concat(".json")));

			File shiftedFile = new File(PAYMENTS_PATH.concat("ShiftedTokens.json"));
			if (!shiftedFile.exists()) {
				shiftedFile.createNewFile();
				JSONArray shiftedTokensArray = new JSONArray();
				shiftedTokensArray.put(partTokens.getString(0));
				writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedTokensArray.toString(), false);
			} else {
				String shiftedContent = readFile(PAYMENTS_PATH.concat("ShiftedTokens.json"));
				JSONArray shiftedArray = new JSONArray(shiftedContent);
				shiftedArray.put(partTokens.getString(0));
				writeToFile(PAYMENTS_PATH.concat("ShiftedTokens.json"), shiftedArray.toString(), false);
			}
		} else {

			JSONObject partTokenChainsToBeSentForHashing = new JSONObject();

			TokenSenderLogger.debug("Updating files for old parts");
			for (int i = 0; i < partTokens.length(); i++) {
				String newTokenChain = readFile(TOKENCHAIN_PATH.concat("PARTS/") + partTokens.getString(i) + ".json");
				JSONArray chainArray = new JSONArray(newTokenChain);

				JSONObject newLastObject = new JSONObject();

				/*
				 * if (chainArray.length() == 0) { newLastObject.put("previousHash", "");
				 * 
				 * } else {
				 * 
				 * JSONObject secondLastObject = chainArray.getJSONObject(chainArray.length() -
				 * 1); secondLastObject.put("nextHash", calculateHash(tid, "SHA3-256"));
				 * newLastObject.put("previousHash", calculateHash(
				 * chainArray.getJSONObject(chainArray.length() - 1).getString("tid"),
				 * "SHA3-256")); }
				 */

				TokenSenderLogger
						.debug("Amount from ledger: " + formatAmount(amountLedger.getDouble(partTokens.getString(i))));
				Double amount = formatAmount(amountLedger.getDouble(partTokens.getString(i)));

				newLastObject.put("senderSign", senderSign);
				newLastObject.put("sender", senderDidIpfsHash);
				newLastObject.put("receiver", receiverDidIpfsHash);
				newLastObject.put("comment", comment);
				newLastObject.put("tid", tid);
				// newLastObject.put("nextHash", "");
				newLastObject.put("role", "Sender");
				newLastObject.put("amount", amount);
				chainArray.put(newLastObject);

				partTokenChainsToBeSentForHashing.put(partTokens.getString(i), chainArray);

				TokenSenderLogger.debug("Checking Parts Token Balance ...");
				Double availableParts = partTokenBalance(partTokens.getString(i));
				TokenSenderLogger.debug("Available: " + availableParts);
				if (availableParts >= 1.000 || availableParts <= 0.000) {
					TokenSenderLogger.debug("Wholly Spent, Removing token from parts");
					String partFileContent2 = readFile(PAYMENTS_PATH.concat("PartsToken.json"));
					JSONArray partContentArray2 = new JSONArray(partFileContent2);
					for (int j = 0; j < partContentArray2.length(); j++) {
						if (partContentArray2.getJSONObject(j).getString("tokenHash").equals(partTokens.getString(i)))
							partContentArray2.remove(j);
						writeToFile(PAYMENTS_PATH.concat("PartsToken.json"), partContentArray2.toString(), false);
					}
					deleteFile(PART_TOKEN_PATH.concat(partTokens.getString(i)));
				}
			}
			output.println("Old part token chains to be hashed");
			output.println(partTokenChainsToBeSentForHashing.toString());

			String hashedPartTokenChains_string;
			try {
				hashedPartTokenChains_string = input.readLine();
			} catch (SocketException e) {
				TokenSenderLogger.warn(
						"Receiver " + receiverDidIpfsHash + " could'nt send hahsed and signed token chain blocks.");
				executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
				output.close();
				input.close();
				senderSocket.close();
				senderMutex = false;
				updateQuorum(quorumArray, null, false, type);
				APIResponse.put("did", senderDidIpfsHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message",
						"Receiver " + receiverDidIpfsHash + " could'nt send hahsed and signed token chain blocks.");

				return APIResponse;
			}

			JSONObject finalPartTokenChains = new JSONObject(hashedPartTokenChains_string);
			for (int i = 0; i < partTokens.length(); i++) {

				JSONArray chainArray = finalPartTokenChains.getJSONArray(partTokens.getString(i));
				writeToFile(TOKENCHAIN_PATH.concat("PARTS/").concat(partTokens.getString(i)).concat(".json"),
						chainArray.toString(), false);
			}

			if (oldNew) {
				String token = partTokens.getString(partTokens.length() - 1);
				String bnk = readFile(PAYMENTS_PATH.concat("BNK00.json"));
				JSONArray bnkArray = new JSONArray(bnk);
				for (int i = 0; i < bnkArray.length(); i++) {
					if (bnkArray.getJSONObject(i).getString("tokenHash").equals(token))
						bnkArray.remove(i);
				}
				writeToFile(PAYMENTS_PATH.concat("BNK00.json"), bnkArray.toString(), false);

				JSONArray pArray = new JSONArray();
				JSONObject pObject = new JSONObject();
				pObject.put("tokenHash", token);
				pArray.put(pObject);
				writeToFile(PAYMENTS_PATH.concat("PartsToken.json"), pArray.toString(), false);

			}
		}
	}
		TokenSenderLogger.info("Transaction Successful");
		executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
		updateQuorum(quorumArray, signedQuorumList, true, type);
		output.close();
		input.close();
		senderSocket.close();
		senderMutex = false;

		// Populating data to explorer
		if (!EXPLORER_IP.contains("127.0.0.1")) {
			
			List<String> tokenList = new ArrayList<>();
			for (int i = 0; i < allTokens.length(); i++)
				tokenList.add(allTokens.getString(i));
			String url = EXPLORER_IP + "/CreateOrUpdateRubixTransaction";
			URL obj = new URL(url);
			HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

			// Setting basic post request
			con.setRequestMethod("POST");
			con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
			con.setRequestProperty("Accept", "application/json");
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("Authorization", "null");

			// Serialization
			JSONObject dataToSend = new JSONObject();
			dataToSend.put("transaction_id", tid);
			dataToSend.put("sender_did", senderDidIpfsHash);
			dataToSend.put("receiver_did", receiverDidIpfsHash);
			dataToSend.put("token_id", tokenList);
			dataToSend.put("token_time", (int) totalTime);
			dataToSend.put("amount", requestedAmount);
			String populate = dataToSend.toString();

			JSONObject jsonObject = new JSONObject();
			jsonObject.put("inputString", populate);
			String postJsonData = jsonObject.toString();

			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(postJsonData);
			wr.flush();
			wr.close();

			int responseCode = con.getResponseCode();
			TokenSenderLogger.debug("Sending 'POST' request to URL : " + url);
			TokenSenderLogger.debug("Post Data : " + postJsonData);
			TokenSenderLogger.debug("Response Code : " + responseCode);

			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String output;
			StringBuffer response = new StringBuffer();

			while ((output = in.readLine()) != null) {
				response.append(output);
			}
			in.close();

			TokenSenderLogger.debug(response.toString());
		}
		return APIResponse;

	}
}