package com.rubix.Datum;

import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.DATUM_CHAIN_PATH;
import static com.rubix.Resources.Functions.LOGGER_PATH;
import static com.rubix.Resources.Functions.PAYMENTS_PATH;
import static com.rubix.Resources.Functions.QuorumCheck;
import static com.rubix.Resources.Functions.QuorumSwarmConnect;
import static com.rubix.Resources.Functions.SEND_PORT;
import static com.rubix.Resources.Functions.TOKENCHAIN_PATH;
import static com.rubix.Resources.Functions.TOKENS_PATH;
import static com.rubix.Resources.Functions.WALLET_DATA_PATH;
import static com.rubix.Resources.Functions.calculateHash;
import static com.rubix.Resources.Functions.deleteFile;
import static com.rubix.Resources.Functions.getCurrentUtcTime;
import static com.rubix.Resources.Functions.getPeerID;
import static com.rubix.Resources.Functions.getQuorum;
import static com.rubix.Resources.Functions.getSignFromShares;
import static com.rubix.Resources.Functions.getValues;
import static com.rubix.Resources.Functions.minQuorum;
import static com.rubix.Resources.Functions.readFile;
import static com.rubix.Resources.Functions.strToIntArray;
import static com.rubix.Resources.Functions.updateJSON;
import static com.rubix.Resources.Functions.updateQuorum;
import static com.rubix.Resources.Functions.writeToFile;
import static com.rubix.Resources.IPFSNetwork.add;
import static com.rubix.Resources.IPFSNetwork.pin;
import static com.rubix.Resources.IPFSNetwork.repo;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Consensus.DatainitiatorProcedure;
import com.rubix.Consensus.InitiatorConsensus;
import com.rubix.Consensus.InitiatorProcedure;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;

import io.ipfs.api.IPFS;

public class DataCommitter {
	private static final Logger DataCommitterLogger = Logger.getLogger(DataCommitter.class);
	private static final Logger eventLogger = Logger.getLogger("eventLogger");

	public static BufferedReader serverInput;
	private static PrintStream output;
	private static BufferedReader input;
	private static Socket senderSocket;
	private static boolean senderMutex = false;
	private static HashMap<String, String> dataTableHashMap = Dependency.dataTableHashMap();

	/**
	 * A committer node to commit data
	 *
	 * @param data Details required for dataCommit
	 * @param ipfs IPFS instance
	 * @param port committer port for communication
	 * @return Transaction Details (JSONObject)
	 * @throws IOException              handles IO Exceptions
	 * @throws JSONException            handles JSON Exceptions
	 * @throws NoSuchAlgorithmException handles No Such Algorithm Exceptions
	 */

	public static boolean compareJsons(JSONArray j1, JSONArray j2) {
		PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
		boolean isEqual = true;
		ArrayList<JSONArray> dataToken = new ArrayList<JSONArray>();

		// data
		for (int i = 0; i < j1.length(); i++) {
			// bank
			for (int j = 0; j < j2.length(); j++) {

			}
		}

		return isEqual;
	}

	/**
	 * @param data
	 * @param ipfs
	 * @param port
	 * @return
	 * @throws Exception
	 */
	public static JSONObject Commit(String data, IPFS ipfs, int port) throws Exception {
		repo(ipfs);
		Dependency.checkDatumPath();
		JSONObject APIResponse = new JSONObject();
		PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
		JSONObject detailsObject = new JSONObject(data);
		// DataCommitterLogger.debug("data is "+data);
		String pvt = detailsObject.getString("pvt");
		int type = detailsObject.getInt("type");
		String comment = detailsObject.getString("comment");


		APIResponse = new JSONObject();

		// DataCommitterLogger.debug("detailsObject is "+ detailsObject.toString());

		String senderPeerID = getPeerID(DATA_PATH + "DID.json");
		// DataCommitterLogger.debug("sender peer id" + senderPeerID);
		String senderDidIpfsHash = Dependency.getDIDfromPID(senderPeerID, dataTableHashMap);
		// getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", senderPeerID);
		DataCommitterLogger.debug("sender did ipfs hash" + senderDidIpfsHash);

		if (senderMutex) {
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Sender busy. Try again later");
			DataCommitterLogger.warn("Sender busy");
			return APIResponse;
		}

		/*
		 * if(!detailsObject.has(TRANS_TYPE)) { APIResponse.put("ERROR",
		 * "TRANS_TYPE not found"); APIResponse.put("did", senderDidIpfsHash);
		 * APIResponse.put("tid", "null"); APIResponse.put("status", "Failed");
		 * APIResponse.put("message", "TRANS_TYPE not found"); return APIResponse; }
		 */

		senderMutex = true;
		DataCommitterLogger.debug("sender mutex is " + senderMutex);
		// DataCommitterLogger.debug("Transscation type is "+
		// detailsObject.getString(TRANS_TYPE));

		/*
		 * 
		 * Data Jar Starts here
		 * 
		 * 
		 */
		// DataCommitterLogger.debug("skipping normal trnx");
		String datumFolderPath = DATUM_CHAIN_PATH;
		File datumFolder = new File(datumFolderPath);
		if (!datumFolder.exists()) {
			DataCommitterLogger.debug("datum Folder is missing");
			datumFolder.mkdir();
			DataCommitterLogger.debug("datum Folder created");

		}
		File datumCommitHistory = new File(datumFolderPath.concat("datumCommitHistory.json"));
		if (!datumCommitHistory.exists()) {
			DataCommitterLogger.debug("datumCommitHistory is missing");
			datumCommitHistory.createNewFile();
			writeToFile(datumCommitHistory.toString(), "[]", false);
			DataCommitterLogger.debug("datumCommitHistory is created");
		}
		File datumCommitToken = new File(PAYMENTS_PATH.concat("dataToken.json"));
		if (!datumCommitToken.exists()) {
			DataCommitterLogger.debug("datumCommitToken is missing");
			datumCommitToken.createNewFile();
			writeToFile(datumCommitToken.toString(), "[]", false);
		}
		
		File datumTokenFolder = new File(datumFolderPath + "DatumTokens/");
		if (!datumTokenFolder.exists()) {
			DataCommitterLogger.debug("datum token Folder is missing");
			datumTokenFolder.mkdir();
			DataCommitterLogger.debug("datum token Folder created");
		}
		// File datumCommitChain =
		
		
		
		

		String blockHash = detailsObject.getString("blockHash");
		// DataCommitterLogger.debug("blockhash is "+ blockHash);
		String authSenderByRecHash = calculateHash(blockHash + senderDidIpfsHash + comment, "SHA3-256");
		// TokenSenderLogger.debug("iauthSenderByRecHash is "+authSenderByRecHash);
		// TokenSenderLogger.debug("Hash to verify Data: " + authSenderByRecHash);
		String tid = calculateHash(authSenderByRecHash, "SHA3-256");
		// TokenSenderLogger.debug("DataInitator by Data Hash " + authSenderByRecHash);
		// TokenSenderLogger.debug("TID on Initator " + tid);

		// TokenSenderLogger.debug("BlockHash "+blockHash+" fetched from
		// datumCommitChain is "+
		// getValues(datumFolderPath.concat("datumCommitChain.json"), "blockHash",
		// "blockHash", blockHash));

		String bankFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
		JSONArray bankArray = new JSONArray(bankFile);
		// JSONArray wholeTokens = new JSONArray();
		JSONArray wholeTokensListForData = new JSONArray();
		wholeTokensListForData.put(Dependency.tokenToCommit());

		String dat01File = PAYMENTS_PATH.concat("DAT01.json");
		File DAT_TOKEN_FILE = new File(dat01File);

		if (!DAT_TOKEN_FILE.exists()) {
			DataCommitterLogger.debug("Data token file not found");
			DAT_TOKEN_FILE.createNewFile();
			writeToFile(DAT_TOKEN_FILE.toString(), "[]", false);
			DataCommitterLogger.debug("Data token file created");

		} else {
			DataCommitterLogger.debug("Data token file exists");
		}

		// JSONArray datArray = new JSONArray(DAT_TOKEN_FILE);
		String dataTokenFile = readFile(PAYMENTS_PATH.concat("DAT01.json"));
		JSONArray datArray = new JSONArray(dataTokenFile);

		DataCommitterLogger.debug("DAT01 size is " + datArray.length());

		// if (datArray.length() < 1) {
		// if (bankArray.length() < 1) {
		// senderMutex = false;
		// APIResponse.put("ERROR", "Insufficent Balance");
		// APIResponse.put("did", senderDidIpfsHash);
		// APIResponse.put("tid", "null");
		// APIResponse.put("status", "Failed");
		// APIResponse.put("message", "Insufficent Balance");
		// return APIResponse;
		// } else {
		// // DataCommitterLogger.debug("Data token is empty");
		//
		// DataCommitterLogger.debug("Token to be moved to DAT01 " +
		// bankArray.get(0).toString());
		// DataCommitterLogger.debug("Token to be moved to DAT01 in json is" +
		// bankArray.get(0));
		//
		// JSONObject tokenChainName = new JSONObject(bankArray.get(0).toString());
		// DataCommitterLogger.debug("tokenChainName is " + tokenChainName);
		// // DataCommitterLogger.debug("tokenChainType is " +
		// // tokenChainName.getClass().getName());
		// JSONArray tokenNameArray = new JSONArray();
		// tokenNameArray.put(tokenChainName);
		// DataCommitterLogger.debug("tokenNameArray " + tokenNameArray);
		// updateJSON("add", dat01File, tokenNameArray.toString());
		// DataCommitterLogger.debug("dat01 open");
		// dataTokenFile = readFile(PAYMENTS_PATH.concat("DAT01.json"));
		// datArray = new JSONArray(dataTokenFile);
		// }
		// } // else
		/*
		 * { DataCommitterLogger.debug("Token in data commit is "+ datArray.toString());
		 * DataCommitterLogger.debug("bankArray.toString() "+ bankArray.toString());
		 * DataCommitterLogger.debug("datArray.get(0).toString() "+
		 * datArray.get(0).toString()); DataCommitterLogger.
		 * debug("datArray.get(0).toString().contains(bankArray.toString()) is "+
		 * datArray.get(0).toString().contains(bankArray.toString()));
		 * 
		 * for(int i=0;i<datArray.length();i++) { for(int j=0;j<bankArray.length();j++)
		 * {
		 * if(!bankArray.get(j).toString().contains(datArray.getJSONObject(i).getString(
		 * "tokenHash"))) {
		 * DataCommitterLogger.debug("Data token is "+datArray.get(0).toString()
		 * +" is no more available, updating DAT01");
		 * DataCommitterLogger.debug("Removing  "+"["+datArray.get(0).toString()+"]");
		 * updateJSON("remove", dat01File, "["+datArray.get(0).toString()+"]");
		 * DataCommitterLogger.debug("Adding  "+"["+bankArray.get(0).toString()+"]");
		 * updateJSON("add", dat01File, "["+bankArray.get(0).toString()+"]");
		 * 
		 * }
		 * 
		 * }
		 * 
		 * } }
		 */

		String unstakedToken = Dependency.tokenToCommit();

		if (unstakedToken.contentEquals("Insufficent token to commit data")) {
			senderMutex = false;
			APIResponse.put("ERROR", "Insufficent Balance for Data Commit");
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Insufficent Balance");
			return APIResponse;
		}

		if (unstakedToken.contentEquals("Insufficent Balance")) {
			senderMutex = false;
			APIResponse.put("ERROR", "Insufficent Balance");
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Insufficent Balance");
			return APIResponse;
		}

		List<String> dataToken = new ArrayList<String>();
		List<String> bankToken = new ArrayList<String>();

		// for (int i = 0; i < datArray.length(); i++) {
		// dataToken.add(datArray.get(i).toString());
		// }
		dataToken.add(unstakedToken);
		for (int j = 0; j < bankArray.length(); j++) {
			bankToken.add(bankArray.get(j).toString());
		}

		dataToken.retainAll(bankToken);
		Set<String> dataTokenSet = new LinkedHashSet<String>(dataToken);
		Set<String> bankTokenSet = new LinkedHashSet<String>(bankToken);
		JSONArray jsonArr = new JSONArray(dataTokenSet.toString());
		datArray = jsonArr;

		DataCommitterLogger.debug("datArray is " + datArray.toString());
		DataCommitterLogger.debug("jsonArr is " + jsonArr.toString());
		DataCommitterLogger.debug("datArray size after cleanup is " +
				datArray.length());

		writeToFile(dataTokenFile, datArray.toString(), false);

		int wholeAmount = datArray.length();
		// DataCommitterLogger.debug("Whole amount is " + wholeAmount);

		// DataCommitterLogger.debug(
		// getValues(datumFolderPath.concat("datumCommitHistory.json"), "blockHash",
		// "blockHash", blockHash));

		if (getValues(datumFolderPath.concat("datumCommitHistory.json"), "blockHash", "blockHash", blockHash)
				.equals(blockHash)) {
			senderMutex = false;
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Block Hash already exists");
			return APIResponse;
		}

		// JSONArray allTokens = new JSONArray();

		// for (int i = 0; i < wholeAmount; i++) {
		// // wholeTokens.put(bankArray.getJSONObject(i).getString("tokenHash"));
		// wholeTokensListForData.put(datArray.getJSONObject(i).getString("tokenHash"));
		//
		// }

		// DataCommitterLogger.debug("WholeTokens for data is " +
		// wholeTokensListForData.toString());
		//
		// for (int i = 0; i < wholeTokensListForData.length(); i++) {
		// String tokenRemove = wholeTokensListForData.getString(i);
		// for (int j = 0; j < datArray.length(); j++) {
		// if (datArray.getJSONObject(j).getString("tokenHash").equals(tokenRemove))
		// datArray.remove(j);
		// }
		// }

		// unstakedToken;
		// JSONArray wholeTokenChainHash = new JSONArray();

		DataCommitterLogger.debug("wholeTokensListForData " + wholeTokensListForData.toString());
		JSONArray wholeTokenForDataChainHash = new JSONArray();
		JSONArray tokenPreviousSender = new JSONArray();

		File token = new File(TOKENS_PATH + unstakedToken);
		File tokenchain = new File(TOKENCHAIN_PATH + unstakedToken + ".json");
		if (!(token.exists() && tokenchain.exists())) {
			DataCommitterLogger.info("Tokens Not Verified : " + unstakedToken);
			senderMutex = false;
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Invalid token(s)");
			return APIResponse;
		}
		// DataCommitterLogger.debug("wholeTokenForDataHashIPFSPin path is " +
		// unstakedToken);

		String wholeTokenForDataHashIPFSPin = add(TOKENS_PATH + unstakedToken, ipfs);

		DataCommitterLogger.debug("Whole Token wholeTokenForDataHashIPFSPin is " + wholeTokenForDataHashIPFSPin);
		pin(wholeTokenForDataHashIPFSPin, ipfs);
		String tokenChainForDataHashIPFSPin = add(TOKENCHAIN_PATH + unstakedToken + ".json", ipfs);
		wholeTokenForDataChainHash.put(tokenChainForDataHashIPFSPin);

		// DataCommitterLogger.debug("IPFS pinned tokenchain is " +
		// tokenChainForDataHashIPFSPin);
		// DataCommitterLogger.debug("IPFS pinned token is " +
		// wholeTokenForDataHashIPFSPin);
		// DataCommitterLogger.debug("Whole token chain hash " +
		// wholeTokenForDataChainHash);

		// DataCommitterLogger.debug("tokenchain open");

		String tokenChainFileContent = readFile(TOKENCHAIN_PATH + unstakedToken + ".json");

		// tokenChainFileContent = null;

		// tokenChainFileContent = Functions
		// .readFile(TOKENCHAIN_PATH + wholeTokensListForData.get(i) + ".json");

		// DataCommitterLogger.debug("tokenChainFile content is "+
		// tokenChainFileContent);
		JSONArray tokenChainFileArray = new JSONArray(tokenChainFileContent);
		// DataCommitterLogger.debug("Tokenchain length is " +
		// tokenChainFileArray.length());

		// DataCommitterLogger.debug("tokenChainFileArray is
		// "+tokenChainFileArray.toString());
		JSONArray previousSenderArray = new JSONArray();
		JSONObject lastObject = tokenChainFileArray.getJSONObject(tokenChainFileArray.length() - 1);
		// DataCommitterLogger.debug("LastObject " + lastObject.toString());
		// TokenSenderLogger.debug("Last object is "+lastObject.toString());
		// DataCommitterLogger.debug("tokenChainFileArray
		// "+tokenChainFileArray.toString());

		for (int j = 0; j < tokenChainFileArray.length(); j++) {
			String peerIDString = Dependency.getPIDfromDID(tokenChainFileArray.getJSONObject(j).getString("sender"),
					dataTableHashMap);
			if (peerIDString.contains("Not Found")) {
				// throw new IOException("PeerID not found for the did");
				APIResponse.put("ERROR",
						"PeerID not found for the DID " + tokenChainFileArray.getJSONObject(j).getString("sender"));
				APIResponse.put("blockHash", blockHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Kindly rotate the token to add more commits");
				senderMutex = false;
				return APIResponse;

			} else {
				previousSenderArray.put(peerIDString);
			}

		}

		/*
		 * if (tokenChainFileArray.length() > 0) { // JSONObject lastObject = //
		 * tokenChainFileArray.getJSONObject(tokenChainFileArray.length() - 1);
		 * 
		 * for (int j = 0; j < tokenChainFileArray.length(); j++) { String peerID =
		 * getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
		 * tokenChainFileArray.getJSONObject(j).getString("sender"));
		 * previousSenderArray.put(peerID); } }
		 */

		if (tokenChainFileArray.length() > 256) {
			int dataCtr = 0;
			int nodataCtr = 0;
			List<String> tokenChainContentSet = new ArrayList<String>();
			// System.out.println(tokenChainFileArray.getJSONObject(((tokenChainFileArray.length())-256)).toString());
			// System.out.println("loop starting at "+ (tokenChainFileArray.length()-256));
			for (int dataCount = ((tokenChainFileArray.length()) - 256); dataCount < tokenChainFileArray
					.length(); dataCount++) {
				tokenChainContentSet.add(tokenChainFileArray.getJSONObject(dataCount).toString());
			}
			// System.out.println("Hashset size is " + tokenChainContentSet.size());
			// System.out.println(tokenChainContentSet.get(255).toString());

			for (int d = 0; d < tokenChainContentSet.size(); d++) {
				if (!tokenChainContentSet.get(d).contains("blockHash")) {
					nodataCtr++;
					break;
				} else {
					dataCtr++;
				}
			}
			DataCommitterLogger.debug("Data commit counter is " + dataCtr);
			DataCommitterLogger.debug("nodataCtr counter is " + nodataCtr);
			if (dataCtr >= 256) {
				senderMutex = false;
				APIResponse.put("ERROR", "Commit limit exceeded");
				APIResponse.put("blockHash", blockHash);
				APIResponse.put("tid", "null");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Kindly rotate the token to add more commits");
				return APIResponse;
			}

		}
		// DataCommitterLogger.debug("Hash set end time is "+LocalDateTime.now());
		// DataCommitterLogger.debug("Normal strating time is "+LocalDateTime.now());
		//
		//
		//
		// if(tokenChainFileArray.length()>256) {
		// int dataCtr = 0;
		// int nodataCtr = 0;
		// //System.out.println(tokenChainFileArray.getJSONObject(((tokenChainFileArray.length())-256)).toString());
		// //System.out.println("loop starting at "+
		// (tokenChainFileArray.length()-256));
		// for(int
		// dataCount=((tokenChainFileArray.length())-256);dataCount<tokenChainFileArray.length();dataCount++){
		// System.out.println(dataCount+"
		// "+!tokenChainFileArray.getJSONObject(dataCount).toString().contains("blockHash"));
		// if(!tokenChainFileArray.getJSONObject(dataCount).toString().contains("blockHash"))
		// {
		// nodataCtr++;
		// break;
		// }else {
		// dataCtr++;
		// }
		// }
		// DataCommitterLogger.debug("Data commit counter is "+dataCtr);
		// //DataCommitterLogger.debug("nodataCtr counter is "+nodataCtr);
		// if(dataCtr>=256) {
		// senderMutex =false;
		// APIResponse.put("ERROR", "Commit limit exceeded");
		// APIResponse.put("blockHash", blockHash);
		// APIResponse.put("tid", "null");
		// APIResponse.put("status", "Failed");
		// APIResponse.put("message", "Kindly rotate the token to add more commits");
		// // return APIResponse;
		// }
		// }
		// DataCommitterLogger.debug("Normal ending time is "+LocalDateTime.now());

		// if (lastObject.has("mineID")) {
		// wholeTokensListForData.remove(i);
		// }

		JSONObject previousSenderObject = new JSONObject();
		previousSenderObject.put("token", wholeTokenForDataHashIPFSPin);
		previousSenderObject.put("sender", previousSenderArray);
		tokenPreviousSender.put(previousSenderObject);

		// DataCommitterLogger.debug("previousSenderObject is " +
		// previousSenderObject.get("sender").toString());
		DataCommitterLogger.debug("previousSender size is  " + previousSenderArray.length());

		JSONArray dataCommitToken = new JSONArray();

		DataCommitterLogger.debug("unstakedToken is " + unstakedToken);
		DataCommitterLogger.debug(wholeTokensListForData.getString(0));

		dataCommitToken.put(unstakedToken);

		DataCommitterLogger.debug("WholeToken for data is " + unstakedToken);
		// DataCommitterLogger.debug("dataCommitToken for data is " +
		// dataCommitToken.toString());

		// DataCommitterLogger.debug("Whole token length is "+
		// wholeTokensListForData.length()+" and Whole token content is
		// "+wholeTokensListForData.toString());

		JSONArray positionsArray = new JSONArray();
		String tokens = new String();
		// DataCommitterLogger.debug("All token lenght is " + dataCommitToken.length() +
		// " Whole token length is "
		// + wholeTokensListForData.length());
		for (int i = 0; i < dataCommitToken.length(); i++) {
			tokens = dataCommitToken.getString(i);
			DataCommitterLogger.debug("tokens in string is " + tokens);
			String hashString = tokens.concat(senderDidIpfsHash);
			String hashForPositions = calculateHash(hashString, "SHA3-256");
			BufferedImage privateShare = ImageIO
					.read(new File(DATA_PATH.concat(senderDidIpfsHash).concat("/PrivateShare.png")));
			String firstPrivate = PropImage.img2bin(privateShare);
			int[] privateIntegerArray1 = strToIntArray(firstPrivate);
			String privateBinary = Functions.intArrayToStr(privateIntegerArray1);
			String positions = "";
			for (int j = 0; j < privateIntegerArray1.length; j += 49152) {
				positions += privateBinary.charAt(j);
			}
			positionsArray.put(positions);

			String ownerIdentity = hashForPositions.concat(positions);
			String ownerIdentityHash = calculateHash(ownerIdentity, "SHA3-256");

			DataCommitterLogger.debug("Ownership Here Sender Calculation");
			DataCommitterLogger.debug("tokens: " + tokens);
			DataCommitterLogger.debug("hashString: " + hashString);
			DataCommitterLogger.debug("hashForPositions: " + hashForPositions);
			DataCommitterLogger.debug("p1: " + positions);
			DataCommitterLogger.debug("ownerIdentity: " + ownerIdentity);
			DataCommitterLogger.debug("ownerIdentityHash: " + ownerIdentityHash);

		}

		JSONArray alphaQuorum = new JSONArray();
		JSONArray betaQuorum = new JSONArray();
		JSONArray gammaQuorum = new JSONArray();
		int alphaSize;

		ArrayList alphaPeersList;
		ArrayList betaPeersList;
		ArrayList gammaPeersList;

		JSONArray quorumArray;
		// TokenSenderLogger.debug("Type "+type);
		switch (type) {
			case 1: {
				writeToFile(LOGGER_PATH + "tempbeta", tid.concat(senderDidIpfsHash), false);
				String betaHash = IPFSNetwork.add(LOGGER_PATH + "tempbeta", ipfs);
				deleteFile(LOGGER_PATH + "tempbeta");

				writeToFile(LOGGER_PATH + "tempgamma", tid.concat(blockHash), false);
				String gammaHash = IPFSNetwork.add(LOGGER_PATH + "tempgamma", ipfs);
				deleteFile(LOGGER_PATH + "tempgamma");

				quorumArray = getQuorum(senderDidIpfsHash, blockHash, 0);
				break;
			}

			case 2: {
				DataCommitterLogger.debug("quorumlist open");
				quorumArray = new JSONArray(readFile(DATA_PATH + "quorumlist.json"));
				break;
			}
			case 3: {
				quorumArray = detailsObject.getJSONArray("quorum");
				break;
			}
			default: {
				DataCommitterLogger.error("Unknown quorum type input, cancelling transaction");
				APIResponse.put("status", "Failed");
				APIResponse.put("message", "Unknown quorum type input, cancelling transaction");
				return APIResponse;

			}
		}

		DataCommitterLogger.debug("Quorum list " + quorumArray.toString());
		int alphaCheck = 0, betaCheck = 0, gammaCheck = 0;
		JSONArray sanityFailedQuorum = new JSONArray();
		DataCommitterLogger.debug("Getting into Sanity Check for quorum");
		for (int i = 0; i < quorumArray.length(); i++) {
			String quorumPeerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
					quorumArray.getString(i));
			boolean quorumSanityCheck = Functions.sanityCheck("Quorum", quorumPeerID, ipfs, port + 11);
			DataCommitterLogger.debug("quorumSanityCheck is " + quorumSanityCheck);

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
			DataCommitterLogger.debug("Inisde alpha beta gamma check");
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			String message = "Quorum: ".concat(sanityFailedQuorum.toString()).concat(" ");
			APIResponse.put("message", message.concat(Functions.sanityMessage));
			DataCommitterLogger.warn("Quorum: ".concat(message.concat(Functions.sanityMessage)));
			return APIResponse;
		}

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

		// DataCommitterLogger.debug("Alpha node list is " + alphaQuorum.toString());

		alphaPeersList = QuorumCheck(alphaQuorum, alphaSize);
		betaPeersList = QuorumCheck(betaQuorum, 7);
		gammaPeersList = QuorumCheck(gammaQuorum, 7);
		// DataCommitterLogger.debug("Alpha peer list list is " + alphaPeersList);

		endTime = System.currentTimeMillis();
		totalTime = endTime - startTime;
		eventLogger.debug("Quorum Check " + totalTime);

		if (alphaPeersList.size() < minQuorum(alphaSize) || betaPeersList.size() < 5 || gammaPeersList.size() < 5) {
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", "null");
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Quorum Members not available");
			DataCommitterLogger.warn("Quorum Members not available");
			senderMutex = false;
			return APIResponse;
		}

		JSONObject tokenDetails = new JSONObject();
		tokenDetails.put("whole-tokens", wholeTokensListForData);
		tokenDetails.put("whole-tokenChains", wholeTokenForDataChainHash);
		// tokenDetails.put("hashSender", partTokenChainHash);
		// tokenDetails.put("part-tokens", partTokens);
		// tokenDetails.put("part-tokenChains", partTokenChainArrays);
		tokenDetails.put("sender", senderDidIpfsHash);

		// DataCommitterLogger.debug("tokenDetails is " + tokenDetails.toString());

		// DataCommitterLogger.debug("tokenDetails " + tokenDetails.toString());
		String doubleSpendString = tokenDetails.toString();

		String doubleSpend = calculateHash(doubleSpendString, "SHA3-256");
		writeToFile(LOGGER_PATH + "doubleSpend", doubleSpend, false);
		DataCommitterLogger.debug("********Double Spend Hash*********:  " + doubleSpend);
		IPFSNetwork.addHashOnly(LOGGER_PATH + "doubleSpend", ipfs);
		deleteFile(LOGGER_PATH + "doubleSpend");

		JSONObject dataObject = new JSONObject();
		dataObject.put("tid", tid);
		// dataObject.put(TRANS_TYPE, detailsObject.getString(TRANS_TYPE));
		dataObject.put("blockHash", blockHash);
		dataObject.put("pvt", pvt);
		dataObject.put("senderDidIpfs", senderDidIpfsHash);
		dataObject.put("token", wholeTokensListForData.get(0).toString());
		dataObject.put("alphaList", alphaPeersList);
		dataObject.put("betaList", betaPeersList);
		dataObject.put("gammaList", gammaPeersList);

		// DataCommitterLogger.debug("dataobject for Data (Double Spend Hash) " +
		// dataObject.toString());

		DataCommitterLogger.debug("Starting consensusSetUp");
		DatainitiatorProcedure.dataConsensusSetUp(dataObject.toString(), ipfs, SEND_PORT + 100, alphaSize);
		DataCommitterLogger.debug("SENDport is  " + SEND_PORT);
		// InitiatorConsensus.quorumSignature.length() + "response count "
		// + InitiatorConsensus.quorumResponse);
		if (InitiatorConsensus.quorumSignature.length() < (minQuorum(alphaSize) + 2 * minQuorum(7))) {
			DataCommitterLogger.debug("Consensus Failed");
			// senderDetails2Receiver.put("status", "Consensus Failed");
			// output.println(senderDetails2Receiver);
			// executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
			output.close();
			input.close();
			senderSocket.close();
			senderMutex = false;
			updateQuorum(quorumArray, null, false, type);
			APIResponse.put("did", senderDidIpfsHash);
			APIResponse.put("tid", tid);
			APIResponse.put("status", "Failed");
			APIResponse.put("message", "Data commit declined by Quorum");
			return APIResponse;

		}

		DataCommitterLogger.debug("Consensus Reached");
		DataCommitterLogger.debug("Quorum Signatures length " + InitiatorConsensus.quorumSignature.length());
		String senderSign = getSignFromShares(pvt, authSenderByRecHash);
		Iterator<String> keys = InitiatorConsensus.quorumSignature.keys();
		JSONArray signedQuorumList = new JSONArray();

		// TokenSenderLogger.debug("signed quorumlist count is "+keys.toString());

		int ctr = 0;

		while (keys.hasNext()) {
			ctr++;
			signedQuorumList.put(keys.next());

		}
		
		DataCommitterLogger.debug("signed Quorum list count is " + signedQuorumList.length());
		DataCommitterLogger.debug("signed Quorum list is " + signedQuorumList.toString());

		JSONObject blockHashObject = new JSONObject();
		blockHashObject.put("blockHash", blockHash);
		blockHashObject.put("token", dataCommitToken.toString().substring(1, dataCommitToken.toString().length() - 1));
		blockHashObject.put("committer", senderDidIpfsHash);
		JSONArray blockHashArray = new JSONArray();
		blockHashArray.put(blockHashObject);
		String dataTokenNameString = datumTokenFolder + "/" + blockHash + ".json";

		DataCommitterLogger.debug("path is " + dataTokenNameString);
		writeToFile(dataTokenNameString, blockHashArray.toString(), false);
		// writeToFile(datumTokenFolder+ blockHash + ".json" ,
		// blockHashArray.toString(), false);
		String filenameString = add(dataTokenNameString, ipfs);
		DataCommitterLogger.debug("CID is " + filenameString);
		pin(filenameString, ipfs);
		Path renameFile = Paths.get(dataTokenNameString);
		Files.move(renameFile, renameFile.resolveSibling(filenameString));

		/*
		 * try { senderSocket = new Socket("127.0.0.1", 15041); DataOutputStream dout =
		 * new DataOutputStream(senderSocket.getOutputStream());
		 * dout.writeUTF("Hello Server"); dout.flush(); dout.close();
		 * senderSocket.close(); } catch (Exception e) { System.out.println(e); }
		 */

		/*
		 * for (int i = 0; i < signedQuorumList.length(); i++) {
		 * DataCommitterLogger.debug("signed quorum at index " + i + " is " +
		 * signedQuorumList.getString(i));
		 * DataTokenPin.Ping(Dependency.getPIDfromDID(signedQuorumList.getString(i),
		 * dataTableHashMap), 15021, filenameString);
		 * //DataTokenPin.Ping(signedQuorumList.getString(i), port + 11,
		 * filenameString); }
		 */

		APIResponse.put("tid", tid);
		APIResponse.put("committedToken", wholeTokensListForData.getString(0));
		APIResponse.put("blockHash", blockHash);
		APIResponse.put("status", "Success");
		APIResponse.put("did", senderDidIpfsHash);
		APIResponse.put("message", "Data added successfully");
		APIResponse.put("quorumlist", signedQuorumList);
		APIResponse.put("CID", filenameString);
		APIResponse.put("totaltime", totalTime);

		// TokenSenderLogger.debug("API Response is "+APIResponse.toString());

		updateQuorum(quorumArray, signedQuorumList, true, type);

		JSONObject dataBlockRecord = new JSONObject();
		dataBlockRecord.put("role", "DataCommitter");
		dataBlockRecord.put("tokens", dataCommitToken.toString().substring(1, dataCommitToken.toString().length() - 1));
		dataBlockRecord.put("txn", tid);
		dataBlockRecord.put("quorumList", signedQuorumList);
		dataBlockRecord.put("senderDID", senderDidIpfsHash);
		dataBlockRecord.put("receiverDID", senderDidIpfsHash);
		dataBlockRecord.put("blockHash", blockHash);
		dataBlockRecord.put("Date", getCurrentUtcTime());
		dataBlockRecord.put("totalTime", totalTime);
		dataBlockRecord.put("comment", comment);
		dataBlockRecord.put("essentialShare", InitiatorProcedure.essential);
		// requestedAmount = formatAmount(requestedAmount);
		// dataBlockRecord.put("amount-spent", requestedAmount);

		// DataCommitterLogger.debug("data block record is " +
		// dataBlockRecord.toString());
		JSONArray dataBlockEntry = new JSONArray();
		dataBlockEntry.put(dataBlockRecord);
		// DataCommitterLogger.debug("dataBlockRecord being added to json files");
		updateJSON("add", datumFolderPath.concat("datumCommitHistory.json"), dataBlockEntry.toString());
		updateJSON("add", WALLET_DATA_PATH.concat("TransactionHistory.json"), dataBlockEntry.toString());
		// writeToFile(DATUM_CHAIN_PATH + wholeTokensForData.getString(0) + ".json",
		// "[test data]", false);

		// Token receiver part starts here

		// JSONObject tokenObject = new JSONObject(tokenDetails);
		// JSONArray wholeTokens = tokenObject.getJSONArray("whole-tokens");
		// JSONArray wholeTokenChains = tokenObject.getJSONArray("whole-tokenChains");
		// JSONArray previousSendersArray = tokenObject.getJSONArray("previousSender");
		// Double amount = tokenObject.getDouble("amount");

		String hashString = tokens.concat(senderDidIpfsHash);
		String hashForPositions = calculateHash(hashString, "SHA3-256");
		BufferedImage prvt = ImageIO.read(new File(DATA_PATH.concat(senderDidIpfsHash).concat("/PrivateShare.png")));
		String firstPrivate = PropImage.img2bin(prvt);
		int[] privateIntegerArray1 = strToIntArray(firstPrivate);
		String privateBinary = Functions.intArrayToStr(privateIntegerArray1);
		String positions = "";
		for (int j = 0; j < privateIntegerArray1.length; j += 49152) {
			positions += privateBinary.charAt(j);
		}

		String ownerIdentity = hashForPositions.concat(positions);
		String ownerIdentityHash = calculateHash(ownerIdentity, "SHA3-256");

		JSONObject commitChainObject = new JSONObject();
		commitChainObject.put("sender", senderDidIpfsHash);
		commitChainObject.put("senderSign", senderSign);
		commitChainObject.put("comment", comment);
		commitChainObject.put("tid", tid);
		commitChainObject.put("blockHash", blockHash);
		commitChainObject.put("owner", ownerIdentityHash);
		commitChainObject.put("group", JSONObject.NULL);

		JSONArray commitChainEntry = new JSONArray();
		commitChainEntry.put(commitChainObject);

		// writeToFile(DATUM_CHAIN_PATH + wholeTokensListForData.getString(0) + ".json",
		// commitChainObject.toString(), true);
		// writeToFile(TOKENCHAIN_PATH + wholeTokensListForData.getString(0) + ".json",
		// commitChainObject.toString(), true);
		updateJSON("add", DATUM_CHAIN_PATH + wholeTokensListForData.getString(0) + ".json",
				commitChainEntry.toString());
		updateJSON("add", TOKENCHAIN_PATH + wholeTokensListForData.getString(0) + ".json", commitChainEntry.toString());
		add(TOKENS_PATH + wholeTokensListForData.getString(0), ipfs);
		pin(wholeTokensListForData.getString(0), ipfs);
		DataCommitterLogger.debug("IPFS Add & Pin completed");
		// JSONObject amountLedger = tokenObject.getJSONObject("amountLedger");

		DataCommitterLogger.info("Data Block Build Successful");
		// executeIPFSCommands(" ipfs p2p close -t /p2p/" + receiverPeerId);
		// output.close();
		// input.close();
		// senderSocket.close();

		senderMutex = false;
		return APIResponse;

	}
}
