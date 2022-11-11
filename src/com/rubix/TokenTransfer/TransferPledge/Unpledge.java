package com.rubix.TokenTransfer.TransferPledge;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

import com.rubix.Resources.Functions;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;

public class Unpledge {
	static String hashChain = "";
	public static Logger UnpledgeLogger = Logger.getLogger(Unpledge.class);

	public static boolean generateProof(String trnxId, List<String> hashMatch, List<String> tokenList)
			throws NoSuchAlgorithmException, IOException, JSONException {
		boolean proofGenerated = false;
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		int ctr = 0;
		String hash = "";
		int powLevel = Functions.calculatePoW();
		List<String> proofSet = new ArrayList<String>();
		String hashValueString = null;
		boolean loopStatus = true;
		for(int i=0;i<tokenList.size();i++) {
			hash = hashMatch.get(i);
			UnpledgeLogger.info("Initating proof generation for token "+tokenList.get(i));
			hash = Functions.calculateHash(hash, "SHA-256");
			/*
			UnpledgeLogger.debug("Input did is " + hashMatch.get(0) + " and sha256 hash is " + hash);
			UnpledgeLogger.debug("pow level is " + powLevel);
			UnpledgeLogger.debug("trnx id is " + trnxId);
			UnpledgeLogger.debug("hashMatch is " + hashMatch.get(0));
			UnpledgeLogger.debug("hash length " + hashMatch.get(0).length());
			UnpledgeLogger.debug("hashMatch.get(0).length() - powLevel " + (hashMatch.get(0).length() - powLevel));
			UnpledgeLogger
					.debug("Value to match for " + (hashMatch.get(0).substring(hashMatch.get(0).length() - powLevel)));
			UnpledgeLogger.debug("unhashed value in sha256" + hash);
			*/
			int counter = 0;
			long startTime = System.currentTimeMillis();

			// proofSet.add(hash);

			while (!trnxId.endsWith(hash.substring(hash.length() - powLevel))) {
				counter++;
				hash = Functions.calculateHash(hash, "SHA3-256");
				if (counter % 1000 == 0) {
					proofSet.add(hash);
				}
				
				hashChain = hash;
			}
			proofSet.add(hashChain);
			UnpledgeLogger.debug("Proof generated completed for "+tokenList.get(i));
			long endTime = System.currentTimeMillis();
			long timeElapsed = endTime - startTime;
			long timeElapsedMinutes = timeElapsed / 60000;
			UnpledgeLogger.debug("Proof length is " + proofSet.size());
			/*
			 * if (!proofSet.isEmpty()) { Iterator<String> hashSetIterator =
			 * proofSet.iterator(); String pathString = Functions.TOKENCHAIN_PATH+"Proof/";
			 * UnpledgeLogger.debug("Folder path is "+pathString); File proofFolder = new
			 * File(pathString); if(!proofFolder.exists()) { proofFolder.mkdir(); }
			 * UnpledgeLogger.debug(proofFolder+"/"+ tokenList.get(0) + ".proof");
			 * 
			 * PrintWriter writer = new PrintWriter( new OutputStreamWriter(new
			 * FileOutputStream(proofFolder+"/"+ tokenList.get(0) + ".proof"), "UTF-8"));
			 * while (hashSetIterator.hasNext()) { String o = hashSetIterator.next();
			 * UnpledgeLogger.debug("hash to proof is "+o); writer.println(o+","); }
			 * 
			 * proofGenerated = true; }
			 */
			if (!proofSet.isEmpty()) {
				String pathString = Functions.TOKENCHAIN_PATH + "Proof/";
				int currentLevel = Functions.getCurrentLevel();
				File proofFolder = new File(pathString);
				if(!proofFolder.exists()) {
					proofFolder.mkdir();
				}
				Path out = Paths.get(pathString + "/" + tokenList.get(i) + ".proof");
				movePledgedToken(tokenList.get(i));
				try {
					Files.write(out, proofSet, Charset.defaultCharset());
					System.out.println(Files.setAttribute(out, "level", currentLevel, LinkOption.NOFOLLOW_LINKS));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		

		return proofGenerated;

	}

	public static List<String> readData(String tokenProofName) {
		List<String> data = new ArrayList<String>();		
		try {
			Scanner scanner = new Scanner(new File(tokenProofName));
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String[] split = line.split(",");
				for (String s : split) {
					data.add(s);
				}
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return data;
	}
	
	public static List<String> readDataUsingNIO(String tokenProofName) throws IOException{
	    List<String> lines = new ArrayList<>(Files.readAllLines(Paths.get(tokenProofName)));
	    UnpledgeLogger.debug(Files.readAttributes(Paths.get(tokenProofName), "level", LinkOption.NOFOLLOW_LINKS));
		return lines;
	}

	public static int[] pickIndexForValidation(int maxSize) {
		int[] randomIndex = new int[5];
		randomIndex[0] = 0;
		randomIndex[1] = (int) (Math.random() * (maxSize / 2));
		randomIndex[2] = maxSize / 2;
		randomIndex[3] = (int) (Math.random() * (maxSize / 2) + maxSize / 2);
		randomIndex[4] = maxSize - 2;
		return randomIndex;

	}

	public static boolean hashMatch(List<String> tokenProof, int[] index, String did, String trnxID) {
		int proofSize = tokenProof.size();
		String hash = "";
		int powLevel = 6;
		//UnpledgeLogger.debug("trnxid is " + trnxID);
		String didHashString = Functions.calculateHash(did, "SHA-256");
		//UnpledgeLogger.debug("Input did is " + did + " and sha256 hash is " + didHashString);
		boolean hashMatch = true;
		int[] hashMatchScore = new int[index.length];
		for (int i = 0; i < index.length - 1; i++) {
			hash = tokenProof.get(index[i]);
			//UnpledgeLogger.debug("init hash: " + hash + " index: " + index[i]);
			for (int j = 0; j < 1000; j++) {
				hash = Functions.calculateHash(hash, "SHA3-256");
			}
			//UnpledgeLogger.debug("final hash: " + hash + " expected hash: " + tokenProof.get(index[i] + 1));
			if (hash.equals(tokenProof.get(index[i] + 1))) {
				hashMatchScore[i] = 1;
				//UnpledgeLogger.debug("hashMatchScore updated for " + i);
			} else {
				return hashMatch;
			}
		}
		// hash = tokenProof.get(tokenProof.size()-1);
		String hashValueString = tokenProof.get(tokenProof.size() - 1);
		//UnpledgeLogger.debug("Checking last hash value to match");
		//UnpledgeLogger.debug("last value in proofSet is " + hashValueString);
		// for(int i=0;i<1000;i++) {
		// check whats happening
		int ctr = 0;
		hash = tokenProof.get(tokenProof.size() - 2);
		//UnpledgeLogger.debug("2nd last hash value is " + hash);
		while (!trnxID.endsWith(hash.substring(hash.length() - powLevel))) {
			ctr++;
			hash = Functions.calculateHash(hash, "SHA3-256");
			//if (ctr % 100 == 0) {
			//	UnpledgeLogger.debug("hash at " + ctr + " is " + hash);
			//}
			// UnpledgeLogger.debug("Hash match value from hash is "+hash);

			// }
			// hashValueString = hash;
		}
		//UnpledgeLogger.debug(
		//		"Hash vlaue string after while loop" + hashValueString + " hash is " + hash + " at index " + ctr);

		if (hash.equals(tokenProof.get(tokenProof.size() - 1))) {
			hashMatchScore[index.length - 1] = 1;
		}

		//UnpledgeLogger.debug(Arrays.toString(hashMatchScore));

		for (int i = 0; i < index.length; i++) {
			if (hashMatchScore[i] != 1) {
				UnpledgeLogger.debug("Proof verification failed");
				hashMatch = false;
				return hashMatch;
			}
		}
		return hashMatch;

	}

	public static boolean verifyProof(String tokenName, String did, String trnxid) {
		List<String> data = readData(Functions.TOKENCHAIN_PATH + "Proof/" + tokenName + ".proof");
		boolean hashMatchStatus = hashMatch(data, pickIndexForValidation(data.size()), did, trnxid);
		UnpledgeLogger.debug("Hashmatch status is " + hashMatchStatus);
		return hashMatchStatus;
	}
	
	public static boolean movePledgedToken(String tokenHash) throws JSONException {
		boolean status = false;
		
		String pledgedTokenListString = Functions.readFile(Functions.PAYMENTS_PATH.concat("PledgedTokens.json"));
		JSONArray listArray = new JSONArray(pledgedTokenListString);
		JSONArray newPledgedTokenListArray = new JSONArray();
		String bnkLiString = Functions.readFile(Functions.PAYMENTS_PATH.concat("BNK00.json"));
		JSONArray bnkListArray = new JSONArray(bnkLiString);
		
		UnpledgeLogger.debug("old BNK is "+Functions.readFile(Functions.PAYMENTS_PATH.concat("BNK00.json")));
		UnpledgeLogger.debug("old pledged is "+Functions.readFile(Functions.PAYMENTS_PATH.concat("PledgedTokens.json")));
		
		for(int i=0;i<listArray.length();i++) {
			if(listArray.getJSONObject(i).toString().contains(tokenHash)) {
				UnpledgeLogger.debug("removing "+listArray.getJSONObject(i).toString()+" from staked token");
				Functions.updateJSON("remove", Functions.PAYMENTS_PATH.concat("PledgedTokens.json"), listArray.getJSONObject(i).toString());
				Functions.updateJSON("add", Functions.PAYMENTS_PATH.concat("BNK00.json"), listArray.getJSONObject(i).toString());
			}
		}
		
		UnpledgeLogger.debug("updated BNK is "+Functions.readFile(Functions.PAYMENTS_PATH.concat("BNK00.json")));
		UnpledgeLogger.debug("updated pledged is "+Functions.readFile(Functions.PAYMENTS_PATH.concat("PledgedTokens.json")));

		
		
		return status;
		
	}

}
