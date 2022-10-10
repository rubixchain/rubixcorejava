package com.rubix.Datum;

import static com.rubix.Resources.Functions.DATA_PATH;
import static com.rubix.Resources.Functions.DATUM_CHAIN_PATH;
import static com.rubix.Resources.Functions.PAYMENTS_PATH;
import static com.rubix.Resources.Functions.TOKENCHAIN_PATH;
import static com.rubix.Resources.Functions.TOKENS_PATH;
import static com.rubix.Resources.Functions.getOsName;
import static com.rubix.Resources.Functions.getSystemUser;
import static com.rubix.Resources.Functions.readFile;
import static com.rubix.Resources.Functions.writeToFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.rubix.Resources.Functions;

public class Dependency {

	private static final Logger DependencyLogger = Logger.getLogger(DataCommitter.class);
	private static final Logger eventLogger = Logger.getLogger("eventLogger");

	public static HashMap<String, String> dataTableHashMap() {
		String dataTableContent = Functions.readFile(
				DATA_PATH + "DataTable.json");
		JSONArray dataTableArray = null;
		try {
			dataTableArray = new JSONArray(dataTableContent);
		} catch (JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		HashMap<String, String> dataTableMap = new HashMap<String, String>();
		for (int ctr = 0; ctr < dataTableArray.length(); ctr++) {
			try {
				dataTableMap.put(dataTableArray.getJSONObject(ctr).get("didHash").toString(),
						dataTableArray.getJSONObject(ctr).get("peerid").toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return dataTableMap;

	}

	public static HashMap<String, String> widDataTableHashMap() {
		String dataTableContent = Functions.readFile(
				DATA_PATH + "DataTable.json");
		JSONArray dataTableArray = null;
		try {
			dataTableArray = new JSONArray(dataTableContent);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		HashMap<String, String> dataTableMap = new HashMap<String, String>();
		for (int ctr = 0; ctr < dataTableArray.length(); ctr++) {
			try {
				dataTableMap.put(dataTableArray.getJSONObject(ctr).get("peerid").toString(),
						dataTableArray.getJSONObject(ctr).get("walletHash").toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return dataTableMap;

	}

	public static String getPIDfromDID(String did, HashMap<String, String> dataTable) {
		String pidString = "";
		if (dataTable.containsKey(did)) {
			pidString = dataTable.get(did);
		} else {
			pidString = "Not Found";
		}
		return pidString;
	}

	public static String getDIDfromPID(String pid, HashMap<String, String> dataTable) {
		String didString = "Not Found";
		for (Entry<String, String> entry : dataTable.entrySet()) {
			if (entry.getValue().equals(pid)) {
				didString = entry.getKey();
				break;
			}
		}
		return didString;
	}

	public static String getWIDfromPID(String pid, HashMap<String, String> dataTable) {
		String widString = "";
		if (dataTable.containsKey(pid)) {
			widString = dataTable.get(pid);
		} else {
			widString = "Not Found";
		}
		return widString;
	}

	public static void checkDatumPath() {
		boolean status = false;
		String configPath = "";
		String OSName = getOsName();
		if (OSName.contains("Windows")) {
			configPath = ("C:\\Rubix\\");
		} else if (OSName.contains("Mac")) {
			configPath = "/Applications/Rubix/";
		} else if (OSName.contains("Linux")) {
			configPath = "/home/" + getSystemUser() + "/Rubix/";
		}
		String configContentString = readFile(configPath + "config.json");
		JSONArray configContentArray = null;
		try {
			configContentArray = new JSONArray(configContentString);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		if (!configContentString.contains("DATUM_CHAIN_PATH")) {
			// DependencyLogger.debug("Datum chain path is being appended");
			try {
				configContentArray.getJSONObject(0).put("DATUM_CHAIN_PATH", configPath + "DATUM/");
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			writeToFile(configPath + "config.json", configContentArray.toString(), false);
		}

		configContentString = readFile(configPath + "config.json");
		if (configContentString.contains("DATUM_CHAIN_PATH")) {
			status = true;
			// DependencyLogger.debug("DATUM_CHAIN_PATH is found in "+configPath);
		}

	}

	public static String tokenToCommit() throws JSONException {
		String tokenResult = "Insufficent token to commit data";
		String bankFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
		JSONArray bankArray = new JSONArray(bankFile);
		DependencyLogger.debug("bank array length " + bankArray.length());
		if (bankArray.length() < 1) {
			tokenResult = "Insufficent Balance";
			DependencyLogger.debug(tokenResult);
			return tokenResult;
		} else {
			for (int i = 0; i < bankArray.length(); i++) {
				DependencyLogger.debug(i + " is " + bankArray.getJSONObject(i).getString("tokenHash"));
				File token = new File(TOKENS_PATH + bankArray.getJSONObject(i).getString("tokenHash"));
				File tokenchain = new File(
						TOKENCHAIN_PATH + bankArray.getJSONObject(i).getString("tokenHash") + ".json");
				if (!(token.exists() && tokenchain.exists())) {
					tokenResult = "Tokens Not Verified " + bankArray.getJSONObject(i).getString("tokenHash");
					DependencyLogger.debug(tokenResult);
					return tokenResult;
				}

				String tokenChainFileContent = readFile(
						TOKENCHAIN_PATH + bankArray.getJSONObject(i).getString("tokenHash") + ".json");
				JSONArray tokenChainFileArray = new JSONArray(tokenChainFileContent);
				JSONObject lastObject = tokenChainFileArray.getJSONObject(tokenChainFileArray.length() - 1);

				if (!lastObject.has("mineID")) {
					tokenResult = bankArray.getJSONObject(i).getString("tokenHash");
					DependencyLogger.debug(tokenResult);
					return tokenResult;
				}

			}
		}
		return tokenResult;
	}

	public static void checkDatumFolder() throws IOException {
		String datumFolderPath = DATUM_CHAIN_PATH;
		if(datumFolderPath.length()>0) {
			checkDatumPath();
		}
		datumFolderPath = DATUM_CHAIN_PATH;
		File datumFolder = new File(datumFolderPath);
		File datumCommitHistory = new File(datumFolderPath.concat("datumCommitHistory.json"));
		File datumCommitToken = new File(PAYMENTS_PATH.concat("dataToken.json"));
		File datumTokenFolder = new File(datumFolderPath + "DatumTokens/");


		// File datumCommitChain =
		if (!datumFolder.exists()) {
			datumFolder.mkdir();
		}
		if (!datumTokenFolder.exists()) {
			datumTokenFolder.mkdir();
		}
		if (!datumCommitHistory.exists()) {
			datumCommitHistory.createNewFile();
			writeToFile(datumCommitHistory.toString(), "[]", false);
		}
		if (!datumCommitToken.exists()) {
			datumCommitToken.createNewFile();
			writeToFile(datumCommitToken.toString(), "[]", false);
		}

	}

}
