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

            while (ruleNotMatch(DIDs)) {

                iterCount++;
                finalHash = Functions.calculateHash(finalHash, "SHA3-256");
                System.out.println("HashChain at " + iterCount + " is " + finalHash);

            }

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
        int matchRule = DIDs.length;

        for (int i = 0; i < matchRule; i++) {
            if (finalHash.substring(finalHash.length() - matchRule)
                    .equals(DIDs[i].substring(DIDs[i].length() - matchRule))) {
                return false;
            }
        }
        return true;
    }

}
