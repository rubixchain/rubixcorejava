package com.rubix.NFT;

import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Consensus.InitiatorConsensus;
import com.rubix.Consensus.InitiatorProcedure;
import com.rubix.PasswordMasking.PasswordField;
import com.rubix.Resources.Functions;
import static com.rubix.Resources.IPFSNetwork.*;
import io.ipfs.api.IPFS;
import java.awt.image.BufferedImage;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.rubix.NFTResources.EnableNft.*;
import static com.rubix.NFTResources.NFTFunctions.*;

import static com.rubix.Resources.Functions.*;

public class NftBuyer {

    private static final Logger nftBuyerLogger = Logger.getLogger(NftBuyer.class);
    private static final String USER_AGENT = "Mozilla/5.0";
    public static BufferedReader serverInput;
    private static PrintStream output;
    private static BufferedReader input;
    private static Socket buyerSocket;
    private static boolean buyerMutex = false;
    public static String sellerPeerID;

    public static JSONObject send(String data, IPFS ipfs, int port) {
        nftPathSet();
        JSONArray quorumArray;
        JSONObject APIResponse = new JSONObject();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONArray alphaQuorum = new JSONArray();
        JSONArray betaQuorum = new JSONArray();
        JSONArray gammaQuorum = new JSONArray();

        BufferedReader sysInput = new BufferedReader(new InputStreamReader(System.in));

        try {
            JSONObject detailsObject = new JSONObject(data);

            String sellerPubKeyIpfsHash, saleContractIpfsHash, buyerPubKeyIpfsHash;

            String buyerDid = detailsObject.getString("buyerDidIpfsHash");
            String sellerDid = detailsObject.getString("sellerDidIpfsHash");
            double requestedAmount;
            int type = detailsObject.getInt("type");
            String comment = detailsObject.getString("comment");
            String nftTokenIpfsHash = detailsObject.getString("nftToken");

            sellerPeerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", sellerDid);
            boolean sanityCheck = sanityCheck("Receiver", sellerPeerID, ipfs, port + 10);
            if (!sanityCheck) {
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", sanityMessage);
                nftBuyerLogger.warn(sanityMessage);

                return APIResponse;
            }

            if (buyerMutex) {
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender busy. Try again later");
                nftBuyerLogger.warn("Sender busy");

                return APIResponse;
            }

            buyerMutex = true;

            // TODO if sellerPeerID returns null we have to exit and call sync
            nftBuyerLogger.debug("Swarm connecting to " + sellerPeerID);
            swarmConnectP2P(sellerPeerID, ipfs);
            nftBuyerLogger.debug("Swarm connected");
            String sellerWidIpfsHash = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "walletHash",
                    "didHash", sellerDid);
            Functions.nodeData(sellerDid, sellerWidIpfsHash, ipfs);
            forward(sellerPeerID + "NFT", port, sellerPeerID);
            nftBuyerLogger.debug("Forwarded to " + sellerPeerID + " on " + port);
            buyerSocket = new Socket("127.0.0.1", port);
            input = new BufferedReader(new InputStreamReader(buyerSocket.getInputStream()));
            output = new PrintStream(buyerSocket.getOutputStream());
            long startTime = System.currentTimeMillis();

            String buyerPeerId = getPeerID(DATA_PATH + "DID.json");
            output.println(buyerPeerId);
            nftBuyerLogger.debug("Buyer PeerID sent to Seller");
            String peerAuth;
            try {
                peerAuth = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! - Sender Auth");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond! - Sender Auth");
                return APIResponse;
            }

            nftBuyerLogger.debug("PeerAuth received from Seller. Code: " + peerAuth);
            if (peerAuth != null && (!peerAuth.equals("200"))) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                nftBuyerLogger.info("Buyer data not available in the network");
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer data not available in the network");
                return APIResponse;
            }

            nftBuyerLogger.debug("sending nft token hash to seller");
            output.println(nftTokenIpfsHash);
            nftBuyerLogger.debug("Buyer waiting to see if Seller has the nft token");

            String nftTokenAuth;

            try {
                nftTokenAuth = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! ");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond!");
                return APIResponse;
            }

            nftBuyerLogger.debug("nftTokenAuth received from Seller : " + nftTokenAuth);

            if (nftTokenAuth != null && (!nftTokenAuth.equals("200"))) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                nftBuyerLogger.info("Seller does not have NFT token" + nftTokenIpfsHash);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller does not have NFT token" + nftTokenIpfsHash);
                return APIResponse;
            }

            nftBuyerLogger.debug("sending p2pflag to seller : " + detailsObject.getInt("p2pFlag"));
            output.println(detailsObject.getInt("p2pFlag"));

            if (detailsObject.has("p2pFlag") && detailsObject.getInt("p2pFlag") == 1) {

                buyerPubKeyIpfsHash = getPubKeyIpfsHash();

                nftBuyerLogger.debug("This is a peer to peer NFT transaction");
                String rbtAmount;
                try {
                    rbtAmount = input.readLine();
                } catch (SocketException e) {
                    nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! ");
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();
                    buyerSocket.close();

                    buyerMutex = false;
                    APIResponse.put("did", buyerDid);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond!");
                    return APIResponse;
                }

                if (rbtAmount != null) {
                    nftBuyerLogger.info(
                            "Received value of NFT in rbt from Seller for sale, value = "
                                    + Double.parseDouble(rbtAmount));
                }

                requestedAmount = Double.parseDouble(rbtAmount);

                nftBuyerLogger.info("Do you agree with the amount " + requestedAmount + "RBT for NFT sale ? [Y/N]");

                String buyerResponse = sysInput.readLine();

                if (buyerResponse != null && (buyerResponse.equals("Y") || buyerResponse.equals("y"))) {
                    nftBuyerLogger.debug("Buyer agreed to the RBT amount asked for the NFT");
                    output.println("200");
                } else {
                    output.println("420");
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();

                    buyerMutex = false;
                    buyerSocket.close();
                    APIResponse.put("did", buyerDid);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Buyer " + buyerDid + " did not agree to RBT asked for NFT");
                    return APIResponse;

                }

                nftBuyerLogger.debug("Buyer confirmed RBT amount. Buyer checking balance RBT for NFT txn");

                Double available = getBalance();
                if (requestedAmount > available) {
                    output.println("420");
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();

                    buyerMutex = false;
                    buyerSocket.close();
                    APIResponse.put("did", buyerDid);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Buyer " + buyerDid + " does not have enough RBT balance for NFT sale");
                    return APIResponse;
                }

                nftBuyerLogger.debug("Buyer has the required RBT balance to purchase NFT");
                output.println("200");

                nftBuyerLogger.debug("Buyer waiting for NFT sale contract");

                String saleContract;

                try {
                    saleContract = input.readLine();
                } catch (SocketException e) {
                    nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! ");
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();
                    buyerSocket.close();

                    buyerMutex = false;
                    APIResponse.put("did", buyerDid);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond!");
                    return APIResponse;
                }

                JSONObject saleContractObj = new JSONObject(saleContract);

                if (saleContractObj.has("auth") && !saleContractObj.getString("auth").equals("200")) {
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();

                    buyerMutex = false;
                    buyerSocket.close();
                    APIResponse.put("did", buyerDid);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Seller " + sellerDid + " was not able to generate sale contract");
                    return APIResponse;
                }

                saleContractIpfsHash = saleContractObj.getString("saleContractIpfsHash");

                nftBuyerLogger.debug("sale contract generated : " + saleContractIpfsHash);

                nftBuyerLogger.debug("Buyer inquires the Seller Public Key ipfs hash");

                try {
                    sellerPubKeyIpfsHash = input.readLine();
                } catch (SocketException e) {
                    nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! ");
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();
                    buyerSocket.close();

                    buyerMutex = false;
                    APIResponse.put("did", buyerDid);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond!");
                    return APIResponse;
                }
                nftBuyerLogger.debug("Buyer recived Sellers public key details " + sellerPubKeyIpfsHash);
            } else {
                requestedAmount = detailsObject.getDouble("amount");
                sellerPubKeyIpfsHash = detailsObject.getString("sellerPubKeyIpfsHash");
                saleContractIpfsHash = detailsObject.getString("saleContractIpfsHash");
                buyerPubKeyIpfsHash = detailsObject.getString("buyerPubKeyIpfsHash");
            }

            PrivateKey pvtKey = null;
            String keyPass = null;
            if (detailsObject.getInt("p2pFlag") == 0) {
                if (detailsObject.has("buyerPvtKey") && detailsObject.getString("buyerPvtKey") != null) {
                    keyPass = detailsObject.getString("buyerPvtKeyPass");
                    pvtKey = getPvtKeyFromStr(detailsObject.getString("buyerPvtKey"), keyPass);
                    detailsObject.remove("buyerPvtKey");
                }
            } else {
                nftBuyerLogger.info("Enter Private Key Password to Sign new Ownership of NFT");

                nftBuyerLogger.info("*******************************************************");

                char[] privateKeyPass = PasswordField.getPassword(System.in, "Enter the privateKey password: ");

                nftBuyerLogger.info("*******************************************************");

                keyPass = String.valueOf(privateKeyPass);

                pvtKey = getPvtKey(keyPass);

            }

            String nftTokenDetails;
            try {
                nftTokenDetails = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Seller " + sellerDid + " is unable to Respond! ");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller " + sellerDid + " is unable to Respond!");
                return APIResponse;
            }

            JSONObject nftDetailsObject = new JSONObject(nftTokenDetails);

            // nft token authenticity check
            String nftTokencontent = get(nftTokenIpfsHash, ipfs);
            JSONObject nftTokenObject = new JSONObject(nftTokencontent);
            String nftPvtSignature = nftTokenObject.getString("pvtKeySign");
            JSONObject tempObject = new JSONObject(nftTokencontent);
            tempObject.remove("pvtKeySign");
            String verifyNftTokenString = tempObject.toString();

            String creatorInput = nftTokenObject.getString("creatorInput");
            JSONObject creatorInputObj = new JSONObject(creatorInput);
            String creatorPublicKeyIpfsHash = creatorInputObj.getString("creatorPubKeyIpfsHash");

            String creatorPubKeyStr = get(creatorPublicKeyIpfsHash, ipfs);
            PublicKey creatorPublicKey = getPubKeyFromStr(creatorPubKeyStr);

            /*
             * Check if NFT Token is of RAC type =1
             */
            int racType = nftTokenObject.getInt("racType");
            if (racType == 1) {
                output.println("419");
                APIResponse.put("did", sellerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message",
                        "NFT Token " + nftTokenIpfsHash + " is of RAC Type " + racType + " which is depricated");
                nftBuyerLogger
                        .info("NFT Token " + nftTokenIpfsHash + " is of RAC Type " + racType + " which is depricated");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                return APIResponse;
            }

            if (!verifySignature(verifyNftTokenString, creatorPublicKey, nftPvtSignature)) {
                output.println("420");
                APIResponse.put("did", sellerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "NFT Token " + nftTokenIpfsHash + " authenticity check Failed");
                nftBuyerLogger.info("NFT Token " + nftTokenIpfsHash + " authenticity check did not pass");
                nftBuyerLogger.debug(
                        "NFT Buyer was not able to verify the creator Signature of the NFT Token " + nftTokenIpfsHash);
                ;
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                return APIResponse;
            }
            nftBuyerLogger.info("NFT Token " + nftTokenIpfsHash + " authenticity check pass");

            String nftConsensusID = nftDetailsObject.getString("nftConsensusID");
            nftBuyerLogger.debug("NFT Consesnsus auth send to seller");
            if (!dhtEmpty(nftConsensusID, ipfs)) {
                output.println("421");
                APIResponse.put("did", sellerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "NFT consensus ID not unique");
                nftBuyerLogger.info("NFT Consensus ID not unique " + nftConsensusID);
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                return APIResponse;
            }
            nftBuyerLogger.info("NFT Consensus ID unique " + nftConsensusID);

            /**
             * NFT token owner ship check/auth
             */
            String nftTokenChain = get(nftDetailsObject.getString("nftTokenChain"), ipfs);

            if (nftTokenChain != null && nftTokenChain.length() != 0) {
                JSONArray nftTokenChainCont = new JSONArray(nftTokenChain);
                JSONObject nftlastObject = nftTokenChainCont.getJSONObject(nftTokenChainCont.length() - 1);

                boolean nftOwnerCheck = true;
                if (nftlastObject.has("nftOwner")) {
                    nftBuyerLogger.debug("Checking NFT token ownership");
                    String owner = nftlastObject.getString("nftOwner");
                    String hashString = nftTokenIpfsHash.concat(sellerDid);
                    String firstHash = calculateHash(hashString, "SHA3-256");
                    String nftHashString = firstHash.concat(sellerPubKeyIpfsHash);
                    String ownerRecalculated = calculateHash(nftHashString, "SHA3-256");

                    PublicKey sellerPubKey = getPubKeyFromStr(get(sellerPubKeyIpfsHash, ipfs));

                    if (!verifySignature(ownerRecalculated, sellerPubKey, owner)) {
                        nftOwnerCheck = false;
                    }
                }

                if (!nftOwnerCheck) {
                    nftBuyerLogger.debug("NFT Ownership Check Failed");
                    String errorMessage = "NFT Ownership Check Failed";
                    output.println("422");
                    APIResponse.put("did", buyerDid);
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", errorMessage);
                    nftBuyerLogger.debug(errorMessage);
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();
                    buyerSocket.close();

                    buyerMutex = false;
                    return APIResponse;
                }
            }
            nftBuyerLogger.debug("NFT Ownership Check Passed");

            /**
             * Checking the Expiry of nft token by getting checking the Exp filed from
             * extracted token metadata
             * 
             */

            if (creatorInputObj.has("expDate") && creatorInputObj.getString("expDate").length() != 0) {
                String nftExpDate = creatorInputObj.getString("expDate");

                nftBuyerLogger.debug("*************************");
                nftBuyerLogger.debug(nftExpDate);
                nftBuyerLogger.debug("*************************");

                Date expDate = formatDate(nftExpDate);

                Date currDate = new Date();

                Date currFormatDate = formatDate(currDate.toString());

                if (expDate.equals(currDate) || expDate.before(currDate)) {
                    output.println("423");
                    APIResponse.put("did", sellerDid);
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message",
                            "NFT " + nftTokenIpfsHash + " has expired. NFT can no longer be transferred.");
                    nftBuyerLogger.info("NFT " + nftTokenIpfsHash + " has expired. NFT can no longer be transferred.");
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();
                    buyerSocket.close();

                    buyerMutex = false;
                    return APIResponse;

                }
                nftBuyerLogger.info("NFT validity check passed");
            }

            output.println("200");

            String saleContractContent = get(saleContractIpfsHash, ipfs);
            nftBuyerLogger.debug("saleContract contetn : " + saleContractContent);
            JSONObject saleConObj = new JSONObject(saleContractContent);
            JSONObject reConObj = new JSONObject();
            reConObj.put("sellerDID", sellerDid);
            reConObj.put("nftToken", nftDetailsObject.getString("nftToken"));
            reConObj.put("rbtAmount", requestedAmount);

            PublicKey sellerPubKey = getPubKeyFromStr(
                    get(sellerPubKeyIpfsHash, ipfs));
            String saleSignature = saleConObj.getString("sign");

            nftBuyerLogger.debug("reconobj for sale contract verification " + reConObj.toString());

            if (!verifySignature(reConObj.toString(), sellerPubKey, saleSignature)) {
                output.println("420");
                APIResponse.put("did", sellerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer was not able to verify the Sale Contract");
                nftBuyerLogger.info("Buyer was not able to verify the Sale Contract");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                return APIResponse;
            }

            nftBuyerLogger.info("Buyer verified NFT sale contract");
            output.println("200");

            /**
             * Section to initiate RBT transfer of the requested amount for NFT sale
             */

            URL rbtTransferApi = new URL("http://localhost:1898/initiateTransaction");
            HttpsURLConnection rbtCon = (HttpsURLConnection) rbtTransferApi.openConnection();

            // Setting basic post request
            rbtCon.setRequestMethod("POST");
            rbtCon.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            rbtCon.setRequestProperty("Accept", "application/json");
            rbtCon.setRequestProperty("Content-Type", "application/json");
            rbtCon.setRequestProperty("Authorization", "null");

            // Serialization
            JSONObject rbtApiPayload = new JSONObject();
            rbtApiPayload.put("receiver", sellerDid);
            rbtApiPayload.put("tokenCount", requestedAmount);
            rbtApiPayload.put("comment", comment);
            rbtApiPayload.put("type", 1);

            String rbtApiPopulate = rbtApiPayload.toString();
            JSONObject tempRbtObject = new JSONObject();
            tempRbtObject.put("inputString", rbtApiPopulate);
            String postRbtJsonData = tempRbtObject.toString();

            // Send post request
            rbtCon.setDoOutput(true);
            DataOutputStream wrRbtAPI = new DataOutputStream(rbtCon.getOutputStream());
            wrRbtAPI.writeBytes(postRbtJsonData);
            wrRbtAPI.flush();
            wrRbtAPI.close();

            int rbtApiResponseCode = rbtCon.getResponseCode();
            nftBuyerLogger.debug("Sending 'POST' request to URL : " + "http://localhost:1898/initiateTransaction");
            nftBuyerLogger.debug("Post Data : " + postRbtJsonData);
            nftBuyerLogger.debug("Response Code : " + rbtApiResponseCode);

            if (rbtApiResponseCode != 200) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + rbtCon.getResponseCode());
            }

            BufferedReader rbtResInp = new BufferedReader(new InputStreamReader(rbtCon.getInputStream()));

            String rbtApiOut;
            StringBuffer rbtApiResstr = new StringBuffer();

            while ((rbtApiOut = rbtResInp.readLine()) != null) {
                rbtApiResstr.append(rbtApiOut);
            }
            rbtResInp.close();

            nftBuyerLogger.info(
                    "Response of RBT transfer API to nft Seller " + sellerDid + " is : " + rbtApiResstr.toString());

            // converting API response back to JSON Object
            JSONObject rbtAPIresponse = new JSONObject(rbtApiResstr);
            String statusStr = rbtAPIresponse.getJSONObject("data").getJSONObject("response").getString("status");

            if (statusStr != null && !statusStr.equals("Success")) {
                output.println("420");
                output.println(rbtApiResstr);
                APIResponse.put("did", sellerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", rbtAPIresponse.getString("message"));
                nftBuyerLogger.info(rbtAPIresponse.getString("message"));
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                return APIResponse;
            }

            output.println("200");

            String rbtTxnId = rbtAPIresponse.getString("tid");

            /**
             * selecting quorum for NFT Consensus
             */
            String tid = calculateHash(nftDetailsObject.toString() + rbtTxnId, "SHA3-256");
            nftBuyerLogger.debug("TID  " + tid);

            nftBuyerLogger.debug("connecting to quorum");
            Functions.writeToFile(Functions.LOGGER_PATH + "tempbeta", tid.concat(buyerDid),
                    Boolean.valueOf(false));
            String betaHash = add(Functions.LOGGER_PATH + "tempbeta", ipfs);
            Functions.deleteFile(Functions.LOGGER_PATH + "tempbeta");
            Functions.writeToFile(Functions.LOGGER_PATH + "tempgamma", tid.concat(sellerDid),
                    Boolean.valueOf(false));
            String gammaHash = add(Functions.LOGGER_PATH + "tempgamma", ipfs);
            Functions.deleteFile(Functions.LOGGER_PATH + "tempgamma");
            switch (type) {
                case 1:
                    quorumArray = Functions.getQuorum(buyerDid, sellerDid, (int) requestedAmount);
                    break;
                case 2:
                    quorumArray = new JSONArray(Functions.readFile(Functions.DATA_PATH + "quorumlist.json"));
                    break;
                case 3:
                    quorumArray = detailsObject.getJSONArray("quorum");
                    break;
                default:
                    nftBuyerLogger.error("Unknown quorum type input, cancelling transaction");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Unknown quorum type input, cancelling transaction");
                    APIResponse.put("did", buyerDid);
                    APIResponse.put("tid", tid);
                    executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();
                    buyerSocket.close();

                    buyerMutex = false;
                    return APIResponse;
            }

            int alphaCheck = 0, betaCheck = 0, gammaCheck = 0;
            JSONArray sanityFailedQuorum = new JSONArray();
            for (int i = 0; i < quorumArray.length(); i++) {
                String quorumPeerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash",
                        quorumArray.getString(i));
                boolean quorumSanityCheck = sanityCheck("Quorum", quorumPeerID, ipfs, port + 11);

                if (!quorumSanityCheck) {
                    sanityFailedQuorum.put(quorumPeerID);
                    if (i <= 6)
                        alphaCheck++;
                    if (i >= 7 && i <= 13)
                        betaCheck++;
                    if (i >= 14 && i <= 20)
                        gammaCheck++;
                }
            }

            if (alphaCheck > 2 || betaCheck > 2 || gammaCheck > 2) {
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                String message = "Quorum: ".concat(sanityFailedQuorum.toString()).concat(" ");
                APIResponse.put("message", message.concat(sanityMessage));
                nftBuyerLogger.warn("Quorum: ".concat(message.concat(sanityMessage)));
                buyerMutex = false;

                return APIResponse;
            }

            long endTime, totalTime;

            Functions.QuorumSwarmConnect(quorumArray, ipfs);
            int alphaSize = quorumArray.length() - 14;
            int j;
            for (j = 0; j < alphaSize; j++)
                alphaQuorum.put(quorumArray.getString(j));
            for (j = 0; j < 7; j++) {
                betaQuorum.put(quorumArray.getString(alphaSize + j));
                gammaQuorum.put(quorumArray.getString(alphaSize + 7 + j));
            }
            nftBuyerLogger.debug("alphaquorum " + alphaQuorum + " size " + alphaQuorum.length());
            nftBuyerLogger.debug("betaquorum " + betaQuorum + " size " + betaQuorum.length());
            nftBuyerLogger.debug("gammaquorum " + gammaQuorum + " size " + gammaQuorum.length());
            ArrayList alphaPeersList = Functions.QuorumCheck(alphaQuorum, alphaSize);
            ArrayList betaPeersList = Functions.QuorumCheck(betaQuorum, 7);
            ArrayList gammaPeersList = Functions.QuorumCheck(gammaQuorum, 7);
            nftBuyerLogger.debug("alphaPeersList size " + alphaPeersList.size());
            nftBuyerLogger.debug("betaPeersList size " + betaPeersList.size());
            nftBuyerLogger.debug("gammaPeersList size " + gammaPeersList.size());
            nftBuyerLogger.debug("minQuorumAlpha size " + Functions.minQuorum(alphaSize));
            if (alphaPeersList.size() < Functions.minQuorum(alphaSize) || betaPeersList.size() < 5
                    || gammaPeersList.size() < 5) {
                Functions.updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Quorum Members not available");
                nftBuyerLogger.warn("Quorum Members not available");
                buyerMutex = false;

                return APIResponse;
            }

            // prepare dataObject to be used to set up and calling consensus
            JSONObject consensusDataObject = new JSONObject();

            consensusDataObject.put("message", "");
            consensusDataObject.put("tid", tid);
            consensusDataObject.put("receiverDidIpfs", "");
            consensusDataObject.put("senderDidIpfs", "");
            consensusDataObject.put("nftTokenDetails", nftDetailsObject);
            consensusDataObject.put("token", "");
            // consensusDataObject.put("rbtTokenDetails", "");
            consensusDataObject.put("pvt", DATA_PATH + buyerDid + "/PrivateShare.png"); // add buyer pvt share
            consensusDataObject.put("sellerPubKeyIpfsHash", sellerPubKeyIpfsHash); // add seller pub key
            consensusDataObject.put("saleContractIpfsHash", saleContractIpfsHash); // contract created for sale of nft
            consensusDataObject.put("tokenAmount", requestedAmount);// value of nft in rbt
            // transfer created by seller and
            // sent to buyer
            consensusDataObject.put("alphaList", alphaPeersList);
            consensusDataObject.put("betaList", betaPeersList);
            consensusDataObject.put("gammaList", gammaPeersList);

            nftBuyerLogger.debug(consensusDataObject.toString());
            nftBuyerLogger.debug("NFT and RBT transfer Consensus setup begins");

            InitiatorProcedure.consensusSetUp(consensusDataObject.toString(), ipfs, SELLER_PORT + 225, alphaSize,
                    "NFT");

            nftBuyerLogger.debug("NFT and RBT transfer Consensus Done");

            nftBuyerLogger.debug("came back to NftBuyer class ");

            nftBuyerLogger.debug("quorum signature length for NFT : " + InitiatorConsensus.nftQuorumSignature.length()
                    + " Response count " + InitiatorConsensus.nftQuorumResponse);

            nftBuyerLogger.debug("quorum signature length for RBT : " + InitiatorConsensus.quorumSignature.length()
                    + " Response count " + InitiatorConsensus.quorumResponse);

            /**
             * signature to verify buyer by seller
             */
            String verifyBuyerbySeller = calculateHash(tid + nftTokenDetails + buyerDid, "SHA3-256");
            String buyerBySellerSign = getSignFromShares(DATA_PATH + buyerDid + "/PrivateShare.png",
                    verifyBuyerbySeller);
            JSONObject consensusDetails = new JSONObject();
            consensusDetails.put("tid", tid);
            consensusDetails.put("comment", comment);
            consensusDetails.put("sign", buyerBySellerSign);
            consensusDetails.put("rbtAmount", requestedAmount);
            if (InitiatorConsensus.quorumSignature
                    .length() > ((Functions.minQuorum(alphaSize) + 2 * Functions.minQuorum(7)))
                    && InitiatorConsensus.nftQuorumSignature.length() < ((minQuorum(alphaSize) + 2 * minQuorum(7)))) {
                nftBuyerLogger.debug("Consensus Failed");
                consensusDetails.put("status", "Consensus Failed");
                output.println(consensusDetails);
                APIResponse.put("message", " Consensus Failed");
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                Functions.updateQuorum(quorumArray, null, false, type);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                return APIResponse;
            }
            nftBuyerLogger.debug("Consensus Reached");

            consensusDetails.put("status", "Consensus Reached");
            consensusDetails.put(("nftQuorumSign"), InitiatorConsensus.nftQuorumSignature.toString());
            nftBuyerLogger.debug("sent consensus details to seller");
            output.println(consensusDetails);

            String signatureAuth;
            try {
                signatureAuth = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Receiver " + sellerDid + " is unable to Respond! - Signature Auth");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver " + sellerDid + "is unable to respond! - Signature Auth");

                return APIResponse;
            }
            nftBuyerLogger.info("signatureAuth : " + signatureAuth);

            endTime = System.currentTimeMillis();
            totalTime = endTime - startTime;
            if (signatureAuth != null && (!signatureAuth.equals("200"))) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                nftBuyerLogger.info("Sender / Quorum not verified");
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Sender / Quorum not verified");
                return APIResponse;

            }

            nftBuyerLogger.debug("Sending RBT Txn ID to Seller to confirm ");
            output.println(rbtTxnId);

            String rbtTxnAuth;
            try {
                rbtTxnAuth = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Receiver " + sellerDid + " is unable to Respond! - Signature Auth");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver " + sellerDid + "is unable to respond! - Signature Auth");

                return APIResponse;
            }

            if (rbtTxnAuth != null && rbtTxnAuth.startsWith("4")) {

                switch (nftTokenAuth) {
                    case "420":
                        nftBuyerLogger.info("Seller Did not get correct RBT Txn details");
                        APIResponse.put("message", "Seller Did not get correct RBT Txn details");
                        break;
                    case "421":
                        nftBuyerLogger.info("Mismatch between RBT Txn details amount and Request NFT amount");
                        APIResponse.put("message", "Mismatch between RBT Txn details amount and Request NFT amount");
                        break;
                }
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);

                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                return APIResponse;
            }

            nftBuyerLogger.debug("Waiting for NFT Seller to Unpin NFT");

            String nftUnpin;

            try {
                nftUnpin = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Receiver " + sellerDid + " is unable to Respond! - nftUnpin Auth");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver " + sellerDid + "is unable to respond! - nftUnpin Auth");

                return APIResponse;
            }
            nftBuyerLogger.debug("NFT Seller Response  " + nftUnpin);
            if (!nftUnpin.equals("NFT-UnPinned")) {
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                nftBuyerLogger.info("Seller unpin NFT Failed");
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller unpin NFT Failed");
                return APIResponse;
            }

            FileWriter fileWriter;
            fileWriter = new FileWriter(NFT_TOKENS_PATH + nftTokenIpfsHash);
            fileWriter.write(get(nftTokenIpfsHash, ipfs));
            fileWriter.close();
            add(NFT_TOKENS_PATH + nftTokenIpfsHash, ipfs);
            pin(nftTokenIpfsHash, ipfs);

            String respAuth;
            try {
                respAuth = input.readLine();
            } catch (SocketException e) {
                nftBuyerLogger.warn("Receiver " + sellerDid + " is unable to Respond! - Share Confirmation");
                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver " + sellerDid + "is unable to respond! - Share Confirmation");

                return APIResponse;
            }

            if (respAuth != null && (!respAuth.equals("Send Response"))) {

                executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                buyerSocket.close();

                buyerMutex = false;
                updateQuorum(quorumArray, null, false, type);
                APIResponse.put("did", buyerDid);
                APIResponse.put("tid", tid);
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Receiver process not over");
                nftBuyerLogger.info("Incomplete Transaction");
                return APIResponse;

            }

            nftBuyerLogger.debug("Operation over");

            Iterator<String> nftKeys = InitiatorConsensus.nftQuorumSignature.keys();
            JSONArray nftSignedQuorumList = new JSONArray();
            while (nftKeys.hasNext())
                nftSignedQuorumList.put(nftKeys.next());

            nftBuyerLogger.debug(
                    "\n*******************NFT Signed Quorum List**************** \n" + nftSignedQuorumList.toString());

            APIResponse.put("tid", tid);
            APIResponse.put("status", "Success");
            APIResponse.put("did", buyerDid);
            APIResponse.put("message", "Tokens transferred successfully!");
            APIResponse.put("quorumlist", nftSignedQuorumList);
            APIResponse.put("receiver", sellerDid);
            APIResponse.put("totaltime", totalTime);

            // updating quorum credit for signing nft txn
            updateQuorum(quorumArray, nftSignedQuorumList, true, type);

            JSONObject nftTransactionRecord = new JSONObject();
            nftTransactionRecord.put("role", "Buyer");
            nftTransactionRecord.put("nftToken", nftTokenIpfsHash);
            nftTransactionRecord.put("txn", tid);
            nftTransactionRecord.put("quorumList", nftSignedQuorumList);
            nftTransactionRecord.put("senderDID", sellerDid);
            nftTransactionRecord.put("receiverDID", buyerDid);
            nftTransactionRecord.put("Date", getCurrentUtcTime());
            nftTransactionRecord.put("totalTime", totalTime);
            nftTransactionRecord.put("comment", comment);
            // nftTransactionRecord.put("essentialShare", InitiatorProcedure.essential);
            requestedAmount = formatAmount(requestedAmount);
            nftTransactionRecord.put("amount-spent", requestedAmount);

            JSONArray nftTransactionHistoryEntry = new JSONArray();
            nftTransactionHistoryEntry.put(nftTransactionRecord);
            updateJSON("add", WALLET_DATA_PATH + "nftTransactionHistory.json", nftTransactionHistoryEntry.toString());

            // adding ownership for NFTToken
            nftBuyerLogger.debug("Adding new Owner details to nft tokenchain");

            String nftString = nftTokenIpfsHash.concat(buyerDid);
            String nftFirstHash = calculateHash(nftString, "SHA3-256");
            String nftHashString = nftFirstHash.concat(buyerPubKeyIpfsHash);
            String nftSignString = calculateHash(nftHashString, "SHA3-256");

            String nftOwnerIdentity = pvtKeySign(nftSignString, pvtKey);

            // update recived nfttokenchain
            saleContractContent = get(saleContractIpfsHash, ipfs);
            JSONObject saleContractObject = new JSONObject(saleContractContent);
            String nftTokenChainContent = get(nftDetailsObject.getString("nftTokenChain"), ipfs);
            JSONArray currentNftTokenChain = new JSONArray(nftTokenChainContent);
            JSONObject newRecord = new JSONObject();
            newRecord.put("sellerDID", sellerDid);
            newRecord.put("BuyerDID", buyerDid);
            newRecord.put("sellerSign", saleContractObject.getString("sign"));
            newRecord.put("comment", comment);
            newRecord.put("tid", tid);
            newRecord.put("sellerPubKeyIpfsHash", sellerPubKeyIpfsHash);
            newRecord.put("buyerPubKeyIpfsHash", buyerPubKeyIpfsHash);
            newRecord.put("nftOwner", nftOwnerIdentity);
            newRecord.put("amount", requestedAmount);
            newRecord.put("role", "Buyer");
            currentNftTokenChain.put(newRecord);
            Functions.writeToFile(NFT_TOKENCHAIN_PATH + nftTokenIpfsHash + ".json", currentNftTokenChain.toString(),
                    Boolean.valueOf(false));

            // Populating data to explorer
            if (!EXPLORER_IP.contains("127.0.0.1")) {
                String url = EXPLORER_IP + "/CreateOrUpdateRubixTransaction";
                URL obj = new URL(url);
                HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

                // Setting basic post request
                con.setRequestMethod("POST");
                con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
                con.setRequestProperty("Accept", "application/json");
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("Authorization", "null");

                // Serialization
                JSONObject dataToSend = new JSONObject();
                dataToSend.put("transaction_type", "NFT");
                dataToSend.put("transaction_id", tid);
                dataToSend.put("sender_did", buyerDid);
                dataToSend.put("receiver_did", sellerDid);
                // dataToSend.put("token_id", tokenList);
                dataToSend.put("token_time", (int) totalTime);
                dataToSend.put("amount", requestedAmount);
                dataToSend.put("nftToken", nftTokenIpfsHash);
                dataToSend.put("nftBuyer", buyerDid);
                dataToSend.put("nftSeller", sellerDid);
                dataToSend.put("nftCreatorInput", creatorInput);
                dataToSend.put("totalSupply", nftTokenObject.getLong("totalSupply"));
                dataToSend.put("editionNumber", nftTokenObject.getLong("tokenCount"));
                String populate = dataToSend.toString();

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("inputString", populate);
                String postJsonData = jsonObject.toString();

                // Send post request
                con.setDoOutput(true);
                DataOutputStream wr = new DataOutputStream(con.getOutputStream());
                wr.writeBytes(postJsonData);
                wr.flush();
                wr.close();

                int responseCode = con.getResponseCode();
                nftBuyerLogger.debug("Sending 'POST' request to URL : " + url);
                nftBuyerLogger.debug("Post Data : " + postJsonData);
                nftBuyerLogger.debug("Response Code : " + responseCode);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String output;
                StringBuffer response = new StringBuffer();

                while ((output = in.readLine()) != null) {
                    response.append(output);
                }
                in.close();

                nftBuyerLogger.debug(response.toString());
            }

            /*
             * nftBuyerLogger.info("Transaction Successful");
             * executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
             * output.close();
             * input.close();
             * buyerSocket.close();
             * //senderMutex = false;
             * return APIResponse;
             */
            nftBuyerLogger.info("Transaction ID: " + tid + " NFT Transaction Successful");
            output.println("Send Response");
            APIResponse.put("did", buyerDid);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Success");
            APIResponse.put("nfttoken", nftTokenIpfsHash);
            APIResponse.put("comment", comment);
            APIResponse.put("message", "NFT Transaction Successful");
        } catch (JSONException e) {
            nftBuyerLogger.error("JSONEXception at reading data", e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ParseException e) {
        }
        nftBuyerLogger.info("NFT Transaction Successful");
        executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
        try {
            buyerSocket.close();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        buyerMutex = false;
        return APIResponse;
    }

}
