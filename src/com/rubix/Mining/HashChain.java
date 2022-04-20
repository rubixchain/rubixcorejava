package com.rubix.Mining;

import com.rubix.Resources.Functions;

public class HashChain {

    String finalHash = "";

    public String newHashChain(String miningTID, String[] DIDs) {

        String finalHash = miningTID;
        int iterCount = 0;

        do {

            iterCount++;
            finalHash = Functions.calculateHash(finalHash, "SHA3-256");

        } while (!matchParameter(DIDs));

        System.out.println("Hash Chain Iteration Count: " + iterCount);
        return finalHash;
    }

    public Boolean verifyHashChain(String tokenTID, String finalHash, String[] DIDs) {

        String calculatedFinalHash = newHashChain(tokenTID, DIDs);

        return calculatedFinalHash == finalHash;
    }

    private Boolean matchParameter(String[] DIDs) {

        int MATCH_RULE = 3;
        String[] matchSubstrings = new String[DIDs.length + 1];
        for (int i = 0; i < DIDs.length; i++) {
            matchSubstrings[i] = DIDs[i].substring(DIDs[i].length() - MATCH_RULE, DIDs[i].length());
        }
        matchSubstrings[-1] = finalHash.substring(finalHash.length() - MATCH_RULE, finalHash.length());

        // check if all the strings in the array are the same
        for (int i = 0; i < matchSubstrings.length - 1; i++) {
            if (!matchSubstrings[i].equals(matchSubstrings[i + 1])) {
                return false;
            }
        }

        return true;

    }

}
