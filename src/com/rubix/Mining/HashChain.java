package com.rubix.Mining;

public class HashChain {

    public String newHashChain(String miningTID, String[] DIDs) {

        String finalHash = null;

        do {

            finalHash = calculateHash(miningTID, "SHA3-256");

        } while (!matchParameter(finalHash, DIDs));

        return finalHash;
    }

    public Boolean verifyHashChain(String tokenTID, String finalHash, String[] DIDs) {

        String calculatedFinalHash = newHashChain(tokenTID, DIDs);

        return calculatedFinalHash == finalHash;
    }

    private Boolean matchParameter(String finalHash, String[] DIDs) {

        int MATCH_RULE = 3;
        String[] matchSubstrings = new String[DIDs.length + 1];

        return false;

    }

}
