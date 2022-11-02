package com.rubix.TokenTransfer.TransferPledge;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

import com.rubix.Resources.Functions;

import org.apache.log4j.Logger;

public class Unpledge {
	static String hashChain = "";
	public static Logger UnpledgeLogger = Logger.getLogger(Unpledge.class);

	public static boolean generateProof(String trnxId, List<String> hashMatch, int powLevel, List<String> tokenList)
			throws NoSuchAlgorithmException, UnsupportedEncodingException, FileNotFoundException {
		boolean proofGenerated = false;
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		int ctr = 0;
		List<String> proofSet = new ArrayList<String>();
		String hashValueString = null;
		boolean loopStatus = true;
		String hash = hashMatch.get(0); 
		UnpledgeLogger.debug("pow level is "+powLevel);
		UnpledgeLogger.debug("trnx id is "+trnxId);
		UnpledgeLogger.debug("hashMatch is "+hashMatch.get(0));
		UnpledgeLogger.debug("hash length "+hashMatch.get(0).length());
		UnpledgeLogger.debug("hashMatch.get(0).length() - powLevel "+ (hashMatch.get(0).length() - powLevel));
		UnpledgeLogger.debug("hashMatch.get(0).substring(hashMatch.get(0).length() - powLevel))"+ (hashMatch.get(0).substring(hashMatch.get(0).length() - powLevel)));
		UnpledgeLogger.debug(hash);



		int counter = 0;
        long startTime = System.currentTimeMillis();
        while (!trnxId.endsWith(hash.substring(hash.length() - powLevel))) {
            counter++;
            hash = Functions.calculateHash(hash, "SHA3-256");
            if(counter==1) {
            	proofSet.add(hash);
            }
            if(counter % 1000 == 0) {
                proofSet.add(hash);
            }
            if (counter % 100000 == 0) {
                UnpledgeLogger.debug("Counter is " + counter + " " + hash + " " + trnxId);
            }
            hashChain = hash;
        }
        proofSet.add(hashChain);
        
		long endTime = System.currentTimeMillis();
        long timeElapsed = endTime - startTime;
        long timeElapsedMinutes = timeElapsed / 60000;
        UnpledgeLogger.debug("Execution time in minutes: " + timeElapsedMinutes);
        
        UnpledgeLogger.debug("final hash is "+hashChain+" at iteration "+counter);

			

			UnpledgeLogger.debug("ProofSet Length is " + proofSet.size());
			if (!proofSet.isEmpty()) {
				Iterator<String> hashSetIterator = proofSet.iterator();
				String pathString = Functions.TOKENCHAIN_PATH+"Proof/";
				UnpledgeLogger.debug("Folder path is "+pathString);
				File proofFolder = new File(pathString);
				if(!proofFolder.exists()) {
					proofFolder.mkdir();
				}
				UnpledgeLogger.debug(proofFolder+"/"+ tokenList.get(0) + ".proof");
				
				PrintWriter writer = new PrintWriter(
						new OutputStreamWriter(new FileOutputStream(proofFolder+"/"+ tokenList.get(0) + ".proof"), "UTF-8"));
				while (hashSetIterator.hasNext()) {
					String o = hashSetIterator.next();
					writer.println(o+",");
				}
				proofGenerated = true;
			}

		
		return proofGenerated;

	}
	
	
	public static List<String> readData(String tokenProofName) {
        List<String> data = new ArrayList<String>();
        try {
            Scanner scanner = new Scanner(new File(
                    tokenProofName));
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
	
	public static int[] pickIndexForValidation(int maxSize) {
        int[] randomIndex = new int[5];
        randomIndex[0] = 0;
        randomIndex[1] = (int) (Math.random() * (maxSize / 2));
        randomIndex[2] = maxSize / 2;
        randomIndex[3] = (int) (Math.random() * (maxSize / 2) + maxSize / 2);
        randomIndex[4] = maxSize -2;
        return randomIndex;

    }
	
	public static boolean hashMatch(List<String> tokenProof, int[] index, String did) {
        int proofSize = tokenProof.size();
        String hash = "";
        String didHashString = Functions.calculateHash(did, "SHA-256");
        UnpledgeLogger.debug("Input did is "+did+" and sha256 hash is "+didHashString);
        boolean hashMatch = false;
        int[] hashMatchScore = new int[index.length];
        for (int i = 0; i < index.length; i++) {
            hash = tokenProof.get(index[i]);
            UnpledgeLogger.debug("init hash: " + hash + " index: " + index[i]);
            for (int j = 0; j < 1000; j++) {
                hash = Functions.calculateHash(hash, "SHA3-256");
            }
			UnpledgeLogger.debug("final hash: " + hash + " expected hash: " + tokenProof.get(index[i] + 1));
            if (hash.equals(tokenProof.get(index[i] + 1))) {
                hashMatchScore[i] = 1;
            } else {
                return hashMatch;
            }
        }

        if (hashMatchScore[0] == 1 && hashMatchScore[1] == 1 && hashMatchScore[2] == 1) {
            hashMatch = true;
        }
        
        if(hashMatch) {
        	UnpledgeLogger.debug("Second last value is "+ tokenProof.get(tokenProof.size()-2));
        	UnpledgeLogger.debug("Expected final hash value is "+ tokenProof.get(tokenProof.size()-1));
        	hash = Functions.calculateHash(tokenProof.get(tokenProof.size()-1), "SHA3-256");
        	if(!hash.equals(tokenProof.get(tokenProof.size()))) {
        		hashMatch = false;
        	}
        }
        return hashMatch;

    }
	
	public static boolean verifyProof(String tokenName,String did) {
		List<String> data = readData(Functions.TOKENCHAIN_PATH+"Proof/"+tokenName+".proof");
        boolean hashMatchStatus = hashMatch(data, pickIndexForValidation(data.size()),did);
        UnpledgeLogger.debug("Hashmatch status is "+hashMatchStatus);
		return hashMatchStatus;
	}
	
	

}
