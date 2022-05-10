package com.rubix.Mining;

import com.rubix.Resources.Functions;

import org.apache.log4j.Logger;

public class HashChain {

    public static Logger HashChainLogger = Logger.getLogger(HashChain.class);

    // write a function which takes a string as TID and string array as DIDs. The
    // function should return the average iterations each DID took to rehash itself
    // to match the last 3 char of TID
    public static String newHashChain(String TID, String[] DIDs, int matchRule) {
        int[] averageIterations = new int[DIDs.length];
        for (int i = 0; i < DIDs.length; i++) {
            averageIterations[i] = averageIterations(TID, DIDs[i], matchRule);
        }
        // get the average of values in the array
        int sum = 0;
        for (int i = 0; i < averageIterations.length; i++) {
            System.out.println("averageIterations[" + i + "] = " + averageIterations[i]);
            sum += averageIterations[i];
        }
        int average = sum / averageIterations.length;
        return Integer.toString(average);
    }

    private static int averageIterations(String TID, String DID, matchRule) {
        int iterations = 0;
        String last3Chars = TID.substring(TID.length() - matchRule);
        String currentDID = DID;
        while (!currentDID.substring(currentDID.length() - matchRule).equals(last3Chars)) {
            currentDID = Functions.calculateHash(currentDID, "SHA3-256");
            iterations++;

        }
        System.out.println(currentDID + " " + iterations);

        return iterations;
    }

    // write a function which takes a string as TID and string array as DIDs. TID
    // will hash itself until last 5 characters are same as any one of the DIDs.
    // If not found, return null.
    // public static String newHashChain(String TID, String[] DIDs) {
    // String TIDHash = TID;

    // while (!Functions.isSame(TIDHash.substring(TIDHash.length() - 5), DIDs)) {
    // TIDHash = Functions.calculateHash(TIDHash, "SHA3-256");
    // }

    // return TIDHash;
    // }

    // write a function which takes a string as TID and string array as DIDs. DIDs
    // will hash itself continuously and return when the last 3 char of hash matches
    // last 3 char of TID
    // public static String newHashChain(String tID, String[] DIDs) {
    // String hashChain = "";
    // int counter = 0;
    // for (String DID : DIDs) {

    // String hash = DID;
    // while (!hash.endsWith(tID.substring(tID.length() - DIDs.length))) {
    // // hashChain += hash;
    // counter++;
    // hash = Functions.calculateHash(hash, "SHA3-256");
    // hashChain = hash;
    // System.out.println(hash + " " + counter);
    // }

    // }

    // return hashChain;

    // }

}
