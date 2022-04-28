package com.rubix.Mining;

import com.rubix.Resources.Functions;

import org.apache.log4j.Logger;

public class HashChain {

    public static Logger HashChainLogger = Logger.getLogger(HashChain.class);

    private static String finalHash = "";
    private static int iterCount = 0;

    public static String newHashChain(String miningTID, String[] DIDs) {

        finalHash = miningTID;

        if (finalHash != null && DIDs.length > 0) {
            System.out.println(miningTID + " " + DIDs);

            do {

                iterCount++;
                finalHash = Functions.calculateHash(finalHash, "SHA3-256");
                System.out.println("HashChain at " + iterCount + " is " + finalHash);

            } while (ruleNotMatch(DIDs));

            HashChainLogger.debug("Hash Chain Length for TID: " + miningTID + " is = " + iterCount);
        }
        return finalHash;
    }

    public static Boolean verifyHashChain(String tokenTID, String finalHash, String[] DIDs) {

        String calculatedFinalHash = newHashChain(tokenTID, DIDs);

        return calculatedFinalHash == finalHash;
    }

    private static Boolean ruleNotMatch(String[] DIDs) {
        // HashChainLogger.debug("Hash Chain " + iterCount + " > " + finalHash);
        int MATCH_RULE = DIDs.length;
        System.out.println("MATCH_RULE = " + MATCH_RULE);

        for (int i = 0; i < DIDs.length; i++) {
            if (finalHash.substring(finalHash.length() - MATCH_RULE)
                    .equals(DIDs[i].substring(DIDs[i].length() - MATCH_RULE))) {
                return false;
            }
        }
        return true;
    }

}
