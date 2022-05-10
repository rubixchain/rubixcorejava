package com.rubix.Mining;

import com.rubix.Resources.Functions;

import org.apache.log4j.Logger;

public class HashChain {

    public static Logger HashChainLogger = Logger.getLogger(HashChain.class);

    // write a function which takes a string as TID and string array as DIDs. DIDs
    // will hash itself continuously and return when the last 3 char of hash matches
    // last 3 char of TID
    private static String newHashChain(String tID, String[] DIDs) {
        String hashChain = "";
        int counter = 0;
        for (String DID : DIDs) {

            String hash = DID;
            while (!hash.endsWith(tID.substring(tID.length() - DIDs.length))) {
                // hashChain += hash;
                counter++;
                hash = Functions.calculateHash(hash, "SHA3-256");
                hashChain = hash;
                System.out.println(hash + " " + counter);
            }

        }

        return hashChain;

    }
    // private static String finalHash = "";
    // private static int iterCount = 0;

    // public static String newHashChain(String miningTID, String[] DIDs) {

    // finalHash = miningTID;

    // if (finalHash != null && DIDs.length > 0) {

    // while (ruleNotMatch(DIDs)) {

    // iterCount++;
    // finalHash = Functions.calculateHash(finalHash, "SHA3-256");
    // System.out.println("HashChain at " + iterCount + " is " + finalHash);

    // }

    // HashChainLogger.debug("Hash Chain Length for TID: " + miningTID + " is = " +
    // iterCount);
    // }
    // return finalHash;
    // }

    // public static Boolean verifyHashChain(String tokenTID, String challengeHash,
    // String[] DIDs) {

    // String calculatedFinalHash = newHashChain(tokenTID, DIDs);

    // return calculatedFinalHash == challengeHash;
    // }

    // private static Boolean ruleNotMatch(String[] DIDs) {
    // int matchRule = 5;

    // for (int i = 0; i < 3; i++) {
    // if (finalHash.substring(finalHash.length() - matchRule)
    // .equals(DIDs[i].substring(DIDs[i].length() - matchRule))) {
    // System.out.println("Rule " + i + " matched");
    // return false;
    // }
    // }

    // System.out.println("Rule not matched");
    // return true;
    // }

}
