package com.rubix.Mining;

import com.rubix.Resources.Functions;

import org.apache.log4j.Logger;

public class HashChain {

    public static Logger HashChainLogger = Logger.getLogger(HashChain.class);

    private static String finalHash = "";
    private static int iterCount = 0;

    public static String newHashChain(String miningTID, String[] DIDs) {

        finalHash = miningTID;

        do {

            iterCount++;
            finalHash = Functions.calculateHash(finalHash, "SHA3-256");

        } while (!matchParameter(DIDs));

        HashChainLogger.trace("Hash Chain Length for TID: " + miningTID + " is = " + iterCount);
        return finalHash;
    }

    public static Boolean verifyHashChain(String tokenTID, String finalHash, String[] DIDs) {

        String calculatedFinalHash = newHashChain(tokenTID, DIDs);

        return calculatedFinalHash == finalHash;
    }

    private static Boolean matchParameter(String[] DIDs) {
        HashChainLogger.trace("Hash Chain " + iterCount + " > " + finalHash);
        int MATCH_RULE = 3;

        for (int i = 0; i < DIDs.length; i++) {
            if (finalHash.substring(finalHash.length() - MATCH_RULE)
                    .equals(DIDs[i].substring(DIDs[i].length() - MATCH_RULE))) {
                return true;
            }
        }
        return true;

    }

}
