package com.rubix.TokenTransfer;

import com.rubix.Ping.VerifyStakedToken;
import com.rubix.Resources.IPFSNetwork;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import static com.rubix.Resources.Functions.*;

public class ForkResolution {
    public static Logger ForkResolutionLogger = Logger.getLogger(ForkResolution.class);
    public static String resolutionMessage;
    public static ArrayList pinOwnersArrayTransferToken = new ArrayList();
    public static ArrayList pinOwnersArrayPledgedToken = new ArrayList();

    public static boolean check(JSONObject tokenDetails) throws IOException, InterruptedException, JSONException {
        /**
         *  1. Check number of pins on the token
         *  2. If number of pins < 2 - Pass the token
         *  3. Else check if the token has any tokens pledged for it
         *  4. If Pledged - Check the number of pins on that token
         *  5. Else reject the transfer token
         *  6. If number of pins on the pledged token == 1 - Pass the transfer token
         *  7. Else Reject the transfer token
         *  8. Check if the transfer token is a recently unpledged token
         *  9. If it is - Contact the node owning token [Ct] it pledged for and ask for the tokenchain height
         *  10.Make sure Ct has genuine pins or match the height of the pledging
         */

        boolean resolution = true;

//        ForkResolutionLogger.debug("Token Details:" + tokenDetails);

        ArrayList previousSender = new ArrayList();
        JSONArray ownersReceived = new JSONArray();

        JSONArray previousSendersArray = tokenDetails.getJSONArray("previousSendersArray");
        String token = tokenDetails.getString("token");
        JSONArray tokenChain = tokenDetails.getJSONArray("tokenChain");

        ForkResolutionLogger.debug("Unpledged Token Check: " + token);
        boolean tokenOwners = true;

        ForkResolutionLogger.debug("Checking owners for " + token + " Please wait...");
        pinOwnersArrayTransferToken = IPFSNetwork.dhtOwnerCheck(token);

        if (pinOwnersArrayTransferToken.size() > 2) {
            ForkResolutionLogger.debug("Multiple pins found");
            for (int j = 0; j < previousSendersArray.length(); j++) {
                if (previousSendersArray.getJSONObject(j).getString("token").equals(token))
                    ownersReceived = previousSendersArray.getJSONObject(j).getJSONArray("sender");
            }

            for (int j = 0; j < ownersReceived.length(); j++) {
                previousSender.add(ownersReceived.getString(j));
            }

            for (int j = 0; j < pinOwnersArrayTransferToken.size(); j++) {
                if (!previousSender.contains(pinOwnersArrayTransferToken.get(j).toString())) {
                    ForkResolutionLogger.debug("Multiple pins of " + pinOwnersArrayTransferToken.get(j).toString() + " not contained in history");
                    tokenOwners = false;
                }else{
                    ForkResolutionLogger.debug("Multiple pins of " + pinOwnersArrayTransferToken.get(j).toString() + " contained in history");
                }
            }
        } else
            ForkResolutionLogger.debug("Token " + token + " has not multiple pins. Passed !!!");


        if (!tokenOwners) {
            ForkResolutionLogger.debug("Multiple pins found for token " + token + ". Checking for pledged details ");

            JSONObject lastObject = tokenChain.getJSONObject(tokenChain.length() - 1);
//            ForkResolutionLogger.debug("Last Object of token " + token + ": " + lastObject);
            if (lastObject.has("tokensPledgedWith")) {
                if (lastObject.getJSONArray("tokensPledgedWith") != null) {
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
            } else {
                resolution = false;
                resolutionMessage = "Transfer token has more owner and no token pledged for";
            }
        }

        return resolution;

    }

    public static boolean verifyUnpledgedToken(String transferToken, String pledgedToken) throws IOException, InterruptedException, JSONException {
        ForkResolutionLogger.debug("Pledged Token Check: " + transferToken);

        ArrayList pinListTransferToken = IPFSNetwork.dhtOwnerCheck(transferToken);
        if(pinListTransferToken.size() > 2) {
            ForkResolutionLogger.debug("Transfer token " + transferToken + " has multiple pins. Checking pledged details");
            ArrayList pinListPledgeToken = IPFSNetwork.dhtOwnerCheck(pledgedToken);
            if (pinListPledgeToken.size() > 1) {
                ForkResolutionLogger.debug("Pledged token has multiple pins. Verifying token chain height details");
                JSONArray tokenChainsList = new JSONArray();
                for (int i = 0; i < pinListPledgeToken.size(); i++) {
                    boolean listStatus = VerifyStakedToken.Contact(pinListPledgeToken.get(i).toString(), SEND_PORT + 16, pledgedToken, "", "Get-TokenChain");
                    if (listStatus) {
                        JSONObject chainObject = new JSONObject();
                        chainObject.put("node", pinListPledgeToken.get(i).toString());
                        chainObject.put("chain", VerifyStakedToken.tokenChain);
                        chainObject.put("hash", calculateHash(VerifyStakedToken.tokenChain.toString(), "SHA3-256"));
                        chainObject.put("length", VerifyStakedToken.tokenChain.length());

                        tokenChainsList.put(chainObject);
                    } else {
                        ForkResolutionLogger.debug("Node " + pinListPledgeToken.get(i).toString() + " have not responded with token chain");
                    }
                }
                ForkResolutionLogger.debug("Token chain details from pinned nodes received");

                /**
                 * Remove token chains that do not contain transfer token
                 */
                for (int i = 0; i < tokenChainsList.length(); i++) {
                    String chainString = tokenChainsList.getJSONObject(i).getJSONArray("chain").toString();
                    if(!chainString.contains(transferToken))
                        tokenChainsList.remove(i);
                }

                ForkResolutionLogger.debug("Token chains to be iterated:");
                for (int i = 0; i < tokenChainsList.length(); i++) {
                    ForkResolutionLogger.debug(tokenChainsList.getJSONObject(i).getString("node"));
                }

                /**
                 * Create hashes and verify remaining token chains
                 */

                /**
                 * Iterate through chains until transfer token found in the object with correct receiver
                 */
                boolean includedCheck = true;
                for (int i = 0; i < tokenChainsList.length(); i++) {
                    JSONArray chain = tokenChainsList.getJSONObject(i).getJSONArray("chain");
                    includedCheck = true;
                    for (int j = chain.length() - 1; j > 0; j--) {
                        ForkResolutionLogger.debug("Object " + j);
                        JSONObject chainIndexObject = chain.getJSONObject(j);
                        if (chainIndexObject.has("tokensPledgedWith")) {
                            String tokensPledgedWithString = chainIndexObject.get("tokensPledgedWith").toString();
                            ForkResolutionLogger.debug("tokensPledgedWithString: " + tokensPledgedWithString + " pledgedToken: " + pledgedToken);
                            if (tokensPledgedWithString.contains(transferToken)) {
                                ForkResolutionLogger.debug("tokensPledgedWithString.contains(pledgedToken)");
                                String receiverPID = getValues(DATA_PATH.concat("DataTable.json"), "peerid", "didHash", chainIndexObject.getString("receiver"));
                                ForkResolutionLogger.debug("Receiver: " + receiverPID + " Node: " + tokenChainsList.getJSONObject(i).getString("node"));
                                if(receiverPID.equals(tokenChainsList.getJSONObject(i).getString("node"))) {
                                    ForkResolutionLogger.debug("Receiver IDs match");
                                    if (j < chain.length() - 1) {
                                        resolutionMessage = "<1> Token Pledged correctly in the appropriate level";
                                        ForkResolutionLogger.debug(" <1> Token Pledged correctly in the appropriate level");
                                        includedCheck = true;
                                        break;
                                    } else {
                                        resolutionMessage = "<2> Token Pledged maliciously. Present in the current level";
                                        ForkResolutionLogger.debug(" <2> Token Pledged maliciously. Present in the current level");
                                        includedCheck = false;
                                    }
                                } else{
                                    resolutionMessage = "<3> Wrong pledge object";
                                    ForkResolutionLogger.debug("<3> Wrong pledge object");
                                    includedCheck = false;
                                }
                            } else {
                                resolutionMessage = " <4> Token Chain  object does not contain the pledged token details";
                                ForkResolutionLogger.debug(" <4> Token Chain  object does not contain the pledged token details");
                                includedCheck = false;
                            }
                        } else {
                            resolutionMessage = "<5> Token Chain Object does not have pledge details";
                            ForkResolutionLogger.debug(" <5> Token Chain Object does not have pledge details");
                            includedCheck = false;
                        }
                    }
                    ForkResolutionLogger.debug("Token " + transferToken + " was pledged for " + pledgedToken + " and check status for token chain of " + tokenChainsList.getJSONObject(i).getString("node") + " is " + includedCheck);
                }
                if (includedCheck)
                    return true;
                ForkResolutionLogger.debug("Transfer token " + transferToken + " not found in any pledged token chains");
                return false;
            } else {
                ForkResolutionLogger.debug("Pledged token has no pins. Passed !!!");
                return true;
            }
        }else {
            ForkResolutionLogger.debug("Transfer token " + transferToken + " has no multiple pins. Passed !!!");
            return true;
        }
    }
}
