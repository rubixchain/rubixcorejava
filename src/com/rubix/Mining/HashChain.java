package com.rubix.Mining;

import com.rubix.Resources.Functions;

import org.apache.log4j.Logger;

public class HashChain {
	static String hashChain = "";

    public static Logger HashChainLogger = Logger.getLogger(HashChain.class);

    public static String newHashChain(String tID, String[] DIDs, int rule) {
       long totalTime = 0;
       long currentTime = 0;
       int ctr = 0;
       java.util.Date date = new java.util.Date();    
       while(totalTime<3600000 || rule > tID.length()) {
           
    	   currentTime = hashChainCounter(tID, DIDs,rule+ctr);
    	   totalTime = totalTime + currentTime;
    	   //System.out.println("Counter is "+ctr+" Current time used is "+ totalTime);
    	   ctr++;
    	   currentTime = 0;
    	   

       }
       return hashChain;
       
    }
    
    
    public static long hashChainCounter(String tID, String[] DIDs, int rule) {
        //String hashChain = "";
    	long start = 0;
        long end = 0;
    	start = System.currentTimeMillis();
    	for (String DID : DIDs) {
            int counter = 0;
            String hash = DID;
            while (!hash.endsWith(tID.substring(tID.length() - rule))) {
                counter++;
                hash = Functions.calculateHash(hash, "SHA3-256");
                hashChain = hash;
            }
           
           // System.out.println(hash + " " + counter + " rule is "+ rule);
        }
    	end = System.currentTimeMillis();
       // System.out.println("Current time "+(end - start));
    	return (end - start);
    }

    // using the TID and DIDs,

    // fetch TID and DIDs as inputs. TID will hash itself until the last 3 char of
    // TID matches with any of the DIDs in the list.

    // public static String getTID(String TID, String DIDs) {

    // token is the TID that

    // public static String newHashChain(String TID, String[] DIDs) {
    // String TIDHash = TID;

    // while (!(TIDHash.substring(TIDHash.length() - 5), DIDs)) {
    // TIDHash = Functions.calculateHash(TIDHash, "SHA3-256");
    // }

    // return TIDHash;
    // }

    // write a function which takes a string as TID and string array as DIDs. TID
    // will hash itself until last 5 characters are same as any one of the DIDs.
    // If not found, return null.

    // write a function which takes a string as TID and string array as DIDs. The
    // function should return the average iterations each DID took to rehash itself
    // to match the last 3 char of TID
    // public static String newHashChain(String TID, String[] DIDs, int matchRule) {
    // int[] averageIterations = new int[DIDs.length];
    // for (int i = 0; i < DIDs.length; i++) {
    // averageIterations[i] = averageIterations(TID, DIDs[i], matchRule);
    // }
    // // get the average of values in the array
    // int sum = 0;
    // for (int i = 0; i < averageIterations.length; i++) {
    // System.out.println("averageIterations[" + i + "] = " + averageIterations[i]);
    // sum += averageIterations[i];
    // }
    // int average = sum / averageIterations.length;
    // return Integer.toString(average);
    // }

    // private static int averageIterations(String TID, String DID, int matchRule) {
    // int iterations = 0;
    // String last3Chars = TID.substring(TID.length() - matchRule);
    // String currentDID = DID;
    // while (!currentDID.substring(currentDID.length() -
    // matchRule).equals(last3Chars)) {
    // currentDID = Functions.calculateHash(currentDID, "SHA3-256");
    // iterations++;

    // }
    // System.out.println(currentDID + " " + iterations);

    // return iterations;
    // }

    // public static String newHashChain(String TID, String[] DIDs) {
    // String TIDHash = TID;

    // while (!Functions.isSame(TIDHash.substring(TIDHash.length() - 5), DIDs)) {
    // TIDHash = Functions.calculateHash(TIDHash, "SHA3-256");
    // }

    // return TIDHash;
    // }

    // public static String newHashChain(String tID, String[] DIDs, int matchRule) {
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
