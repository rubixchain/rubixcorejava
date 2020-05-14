package com.rubix.AuthenticateNode;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;

import static com.rubix.Resources.Functions.*;
import static com.rubix.AuthenticateNode.SplitShares.recombine;


public class Authenticate {

    /**
     * This method is used to authenticate a node in Rubix implementing text based two level NLSS.
     * <P>It is customized for 32 positions verification. The position can be changed by
     * modifying the numberofpositions for integer array sizes accordingly
     * @param decentralizedID is the Decentralized Identity of the node
     * @param walletID is the wallet Identity share of the node
     * @param hashForPositions is the hash derived using the deterministic function
     * @param signature is the private share of the node
     * @return boolean returns true if verified and false if not verified
     * @throws IOException handles IO Exception
     */

    public static boolean verifySignature(String decentralizedID, String walletID, String hashForPositions, String signature) throws IOException, JSONException, InterruptedException {
        String firstPrivate = signature.substring(0, 2048);
        String secondPrivate = signature.substring(2048, 4096);
        String thirdPrivate = signature.substring(4096, 6144);


        int[] positionsLevelTwo=  randomPositions(hashForPositions, 32);
        int[] positionsLevelTwoTrails = finalPositions(positionsLevelTwo, 32);


        StringBuilder senderWalletID = new StringBuilder();

        for (int positionsLevelTwoTrail : positionsLevelTwoTrails)
            senderWalletID.append(walletID.charAt(positionsLevelTwoTrail));


        ArrayList<String> authenticateDetailsList = new ArrayList<>();

        authenticateDetailsList.add(senderWalletID.toString());
        authenticateDetailsList.add(firstPrivate);
        authenticateDetailsList.add(secondPrivate);
        authenticateDetailsList.add(thirdPrivate);

        String recombinedResult = (String) recombine(authenticateDetailsList).get(0);

       int[] positionsLevelTwoDID=  randomPositions(hashForPositions, 32);

        int[] positionsLevelZero = new int[32];

        for (int k = 0; k < 32; k++)
            positionsLevelZero[k] = ((positionsLevelTwoDID[k]) / 64);


        byte[] decentralizedIDBytes = decentralizedID.getBytes();

        StringBuilder decentralizedIDBinary = new StringBuilder();


        for (byte b : decentralizedIDBytes) {
            int n = b;
            for (int i = 0; i < 8; i++) {
                decentralizedIDBinary.append((n & 128) == 0 ? 0 : 1);
                n <<= 1;
            }
        }

        String decentralizedIDString = decentralizedIDBinary.toString();
        StringBuilder decentralizedIDForAuth = new StringBuilder();


        for (int value : positionsLevelZero) decentralizedIDForAuth.append(decentralizedIDString.charAt(value));


        if(recombinedResult.equals(decentralizedIDForAuth.toString())){
            System.out.println("[Authenticate] Verification True");
            return true;
        }

        else{
            System.out.println("[Authenticate] Verification Failed");
            return false;
        }

    }

}

