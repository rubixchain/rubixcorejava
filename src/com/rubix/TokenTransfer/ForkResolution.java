package com.rubix.TokenTransfer;

import com.rubix.Resources.IPFSNetwork;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

public class ForkResolution {
    public static Logger ForkResolutionLogger = Logger.getLogger(ForkResolution.class);
    public static String resolutionMessage;
    public static ArrayList pinOwnersArrayTransferToken = new ArrayList();
    public static ArrayList pinOwnersArrayPledgedToken = new ArrayList();
    public static boolean check(JSONObject tokenDetails) throws IOException, InterruptedException, JSONException {
        boolean resolution = true;
        /**
         *  1. Check number of pins on the token
         *  2. If number of pins < 2 - Pass the token
         *  3. Else check if the token has any tokens pledged for it
         *  4. If Pledged - Check the number of pins on that token
         *  5. Else reject the transfer token
         *  6. If number of pins on the pledged token == 1 - Pass the transfer token
         *  7. Else Reject the transfer token
         */

        ForkResolutionLogger.debug("Token Details:" + tokenDetails);

        ArrayList previousSender = new ArrayList();
        JSONArray ownersReceived = new JSONArray();

        JSONArray previousSendersArray = tokenDetails.getJSONArray("previousSendersArray");
        String token = tokenDetails.getString("token");
        JSONArray tokenChain = tokenDetails.getJSONArray("tokenChain");


        boolean tokenOwners = true;

        try {
            ForkResolutionLogger.debug("Checking owners for " + token + " Please wait...");
            pinOwnersArrayTransferToken = IPFSNetwork.dhtOwnerCheck(token);

            if (pinOwnersArrayTransferToken.size() > 2) {

                for (int j = 0; j < previousSendersArray.length(); j++) {
                    if (previousSendersArray.getJSONObject(j).getString("token").equals(token))
                        ownersReceived = previousSendersArray.getJSONObject(j).getJSONArray("sender");
                }

                for (int j = 0; j < ownersReceived.length(); j++) {
                    previousSender.add(ownersReceived.getString(j));
                }

                for (int j = 0; j < pinOwnersArrayTransferToken.size(); j++) {
                    if (!previousSender.contains(pinOwnersArrayTransferToken.get(j).toString()))
                        tokenOwners = false;
                }
            }
            else
                ForkResolutionLogger.debug("Token " + token + " has not multiple pins. Passed !!!");
        } catch (IOException | InterruptedException e) {
            ForkResolutionLogger.debug("Ipfs dht find did not execute");
        }

        if (!tokenOwners) {
           JSONObject lastObject = tokenChain.getJSONObject(tokenChain.length() - 1);
           ForkResolutionLogger.debug("Last Object of token " + token + ": " + lastObject);
           if(lastObject.has("tokensPledgedWith")){
               if(lastObject.getJSONArray("tokensPledgedWith") != null) {
                   JSONArray pledgeTokens = lastObject.getJSONArray("tokensPledgedWith");
                   for (int i = 0; i < pledgeTokens.length(); i++) {
                       ForkResolutionLogger.debug("Checking owners for pledgeToken" + pledgeTokens.getString(i) + " Please wait...");
                       pinOwnersArrayPledgedToken = IPFSNetwork.dhtOwnerCheck(pledgeTokens.getString(i));
                       if (pinOwnersArrayPledgedToken.size() != 1) {
                           resolution = false;
                           resolutionMessage = "Pledge token has more than one owner";
                       } else {
                           ForkResolutionLogger.debug("Pledged Token " + pledgeTokens.getString(i) + " has not multiple pins. Passed !!!");
                           return true;
                       }
                   }
               }
           }
           else {
               resolution = false;
               resolutionMessage = "Transfer token has more owner and no token pledged for";
           }
        }

        return resolution;
    }
}
