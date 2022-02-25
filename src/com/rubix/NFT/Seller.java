package com.rubix.NFT;

import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Consensus.InitiatorConsensus;
import com.rubix.Consensus.InitiatorProcedure;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import javax.imageio.ImageIO;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONObject;

public class Seller {
    private static final Logger SellerLogger = Logger.getLogger(Seller.class);

    private static final String USER_AGENT = "Mozilla/5.0";

    public static BufferedReader serverInput;

    private static PrintStream output;

    private static BufferedReader input;

    private static Socket sellerSocket;

    private static boolean sellerMutex = false;

    public static JSONObject Send(String data, IPFS ipfs, int port) throws Exception {
        JSONArray quorumArray;
        JSONObject APIResponse = new JSONObject();
        PropertyConfigurator.configure(Functions.LOGGER_PATH + "log4jWallet.properties");
        JSONObject detailsObject = new JSONObject(data);
        String buyerDidIpfsHash = detailsObject.getString("buyerDidIpfsHash");
        String pvt = detailsObject.getString("pvt");
        int amount = detailsObject.getInt("amount");
        int type = detailsObject.getInt("type");
        String comment = detailsObject.getString("comment");
        //String eKey = detailsObject.getString("eKey");
        //String dKey = detailsObject.getString("dKey");
        String nftTokenIpfsHash = detailsObject.getString("nftToken");
        JSONArray alphaQuorum = new JSONArray();
        JSONArray betaQuorum = new JSONArray();
        JSONArray gammaQuorum = new JSONArray();
        String sellerPeerId = Functions.getPeerID(Functions.DATA_PATH + "DID.json");
        SellerLogger.debug("Seller Peer ID : " + sellerPeerId);
        String sellerDidIpfsHash = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "didHash", "peerid", sellerPeerId);
        SellerLogger.debug("Seller DID IPFS Hash : " + sellerDidIpfsHash);
        SellerLogger.debug("Path is : " + Functions.DATA_PATH + sellerDidIpfsHash);
        File folder = new File(Functions.DATA_PATH);
        File[] listOfFiles = folder.listFiles();
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                System.out.println("File " + listOfFiles[i].getName());
            } else if (listOfFiles[i].isDirectory()) {
                System.out.println("Directory " + listOfFiles[i].getName());
            }
        }
        BufferedImage senderWidImage = ImageIO.read(new File(Functions.DATA_PATH + sellerDidIpfsHash + "/PublicShare.png"));
        String senderWidBin = PropImage.img2bin(senderWidImage);
        if (sellerMutex) {
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Sender busy. Try again later");
            SellerLogger.warn("Sender busy");
            return APIResponse;
        }
        sellerMutex = true;
        //String nftTokenChainIpfsHash = null;
        APIResponse = new JSONObject();
        File nfttoken = new File(Functions.NFT_TOKENS_PATH+nftTokenIpfsHash);
        File nfttokenchain = new File(Functions.NFT_TOKENCHAIN_PATH + nftTokenIpfsHash +".json");
        SellerLogger.debug("NFT Token : " + nfttoken + " and NFT TokenChain : " + nfttokenchain);
        if (!nfttoken.exists() || !nfttokenchain.exists() ) {
            SellerLogger.info("NFT Token Not Verified");
            sellerMutex = false;
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Invalid NFT token");
            return APIResponse;
        }
        IPFSNetwork.add(Functions.NFT_TOKENS_PATH + nftTokenIpfsHash, ipfs);
        String nftTokenChainIpfsHash = IPFSNetwork.add(Functions.NFT_TOKENCHAIN_PATH + nftTokenIpfsHash + ".json", ipfs);
        String authSellerByBuyerHash = Functions.calculateHash(nftTokenIpfsHash + nftTokenChainIpfsHash+ amount + buyerDidIpfsHash, "SHA3-256");
        String tid = Functions.calculateHash(authSellerByBuyerHash, "SHA3-256");
        SellerLogger.debug("Hash for Seller authentication to Buyer : " + authSellerByBuyerHash);
        SellerLogger.debug("TID on seller " + tid);
        Functions.writeToFile(Functions.LOGGER_PATH + "tempbeta", tid.concat(sellerDidIpfsHash), Boolean.valueOf(false));
        String betaHash = IPFSNetwork.add(Functions.LOGGER_PATH + "tempbeta", ipfs);
        Functions.deleteFile(Functions.LOGGER_PATH + "tempbeta");
        Functions.writeToFile(Functions.LOGGER_PATH + "tempgamma", tid.concat(buyerDidIpfsHash), Boolean.valueOf(false));
        String gammaHash = IPFSNetwork.add(Functions.LOGGER_PATH + "tempgamma", ipfs);
        Functions.deleteFile(Functions.LOGGER_PATH + "tempgamma");
        switch (type) {
            case 1:
                quorumArray = Functions.getQuorum(betaHash, gammaHash, sellerDidIpfsHash, buyerDidIpfsHash, amount);
                break;
            case 2:
                quorumArray = new JSONArray(Functions.readFile(Functions.DATA_PATH + "quorumlist.json"));
                break;
            case 3:
                quorumArray = detailsObject.getJSONArray("quorum");
                break;
            default:
                SellerLogger.error("Unknown quorum type input, cancelling transaction");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Unknown quorum type input, cancelling transaction");
                return APIResponse;
        }
        Functions.QuorumSwarmConnect(quorumArray, ipfs);
        int alphaSize = quorumArray.length() - 14;
        int j;
        for (j = 0; j < alphaSize; j++)
            alphaQuorum.put(quorumArray.getString(j));
        for (j = 0; j < 7; j++) {
            betaQuorum.put(quorumArray.getString(alphaSize + j));
            gammaQuorum.put(quorumArray.getString(alphaSize + 7 + j));
        }
        SellerLogger.debug("alphaquorum " + alphaQuorum + " size " + alphaQuorum.length());
        SellerLogger.debug("betaquorum " + betaQuorum + " size " + betaQuorum.length());
        SellerLogger.debug("gammaquorum " + gammaQuorum + " size " + gammaQuorum.length());
        ArrayList alphaPeersList = Functions.QuorumCheck(alphaQuorum, alphaSize);
        ArrayList betaPeersList = Functions.QuorumCheck(betaQuorum, 7);
        ArrayList gammaPeersList = Functions.QuorumCheck(gammaQuorum, 7);
        SellerLogger.debug("alphaPeersList size " + alphaPeersList.size());
        SellerLogger.debug("betaPeersList size " + betaPeersList.size());
        SellerLogger.debug("gammaPeersList size " + gammaPeersList.size());
        SellerLogger.debug("minQuorumAlpha size " + Functions.minQuorum(alphaSize));
        if (alphaPeersList.size() < Functions.minQuorum(alphaSize) || betaPeersList.size() < 5 || gammaPeersList.size() < 5) {
            Functions.updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Quorum Members not available");
            SellerLogger.warn("Quorum Members not available");
            sellerMutex = false;
            return APIResponse;
        }
        String sellerSign = Functions.getSignFromShares(pvt, authSellerByBuyerHash);
        JSONObject sellerDetails2Buyer = new JSONObject();
        sellerDetails2Buyer.put("sign", sellerSign);
        sellerDetails2Buyer.put("tid", tid);
        sellerDetails2Buyer.put("comment", comment);
        JSONObject nftTokenDetails = new JSONObject();
        nftTokenDetails.put("nftToken", nftTokenIpfsHash);
        nftTokenDetails.put("nftTokenChain", nftTokenChainIpfsHash);
        String message = nftTokenDetails.toString();
        String nftID = Functions.calculateHash(message, "SHA3-256");
        Functions.writeToFile(Functions.LOGGER_PATH + "nftID", nftID, Boolean.valueOf(false));
        String nftIdIpfsHash = IPFSNetwork.addHashOnly(Functions.LOGGER_PATH + "nftID", ipfs);
        Functions.deleteFile(Functions.LOGGER_PATH + "nftID");
        SellerLogger.debug("nftID hash " + nftIdIpfsHash + " unique owner " + IPFSNetwork.dhtEmpty(nftIdIpfsHash, ipfs));
        JSONObject assetDetails = new JSONObject();
        assetDetails.put("amount", amount);
        String buyerPeerId = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "peerid", "didHash", buyerDidIpfsHash);
        SellerLogger.debug("Swarm connecting to " + buyerPeerId);
        IPFSNetwork.swarmConnectP2P(buyerPeerId, ipfs);
        SellerLogger.debug("Swarm connected");
        String buyerWidIpfsHash = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "walletHash", "didHash", buyerDidIpfsHash);
        Functions.nodeData(buyerDidIpfsHash, buyerWidIpfsHash, ipfs);
        IPFSNetwork.forward(buyerPeerId + "NFT", port, buyerPeerId);
        SellerLogger.debug("Forwarded to " + buyerPeerId + " on " + port);
        sellerSocket = new Socket("127.0.0.1", port);
        input = new BufferedReader(new InputStreamReader(sellerSocket.getInputStream()));
        output = new PrintStream(sellerSocket.getOutputStream());
        long startTime = System.currentTimeMillis();
        output.println(sellerPeerId);
        SellerLogger.debug("PeerID sent to Buyer");
        String peerAuth = input.readLine();
        SellerLogger.debug("PeerAuth received from Buyer. Code: " + peerAuth);
        if (!peerAuth.equals("200")) {
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
            SellerLogger.info("Seller data not available in the network");
            output.close();
            input.close();
            sellerSocket.close();
            sellerMutex = false;
            Functions.updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Seller data not available in the network");
            return APIResponse;
        }
        output.println(nftTokenDetails);
        SellerLogger.debug("NFT Token details sent to Buyer");
        String nftTokenAuth = input.readLine();
        SellerLogger.debug("nftTokenAuth received from Buyer. Code: " + nftTokenAuth);
        if (!nftTokenAuth.equals("200")) {
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
            output.close();
            input.close();
            sellerSocket.close();
            sellerMutex = false;
            if (nftTokenAuth.equals("421")) {
                SellerLogger.info("Invalid NFT Token");
                APIResponse.put("message", "Invalid NFT Token");
            } else {
                SellerLogger.info("NFT ID not unique");
                APIResponse.put("message", "NFT ID not unique");
            }
            Functions.updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            return APIResponse;
        }
        output.println(assetDetails);
        SellerLogger.debug("Sent asset details to Buyer");
        String rbxTokenAuth = input.readLine();
        SellerLogger.debug("Received rbxTokenAuth from Buyer");
        if (rbxTokenAuth.equals("421")) {
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
            output.close();
            input.close();
            sellerSocket.close();
            sellerMutex = false;
            SellerLogger.info("Invalid RBX tokens at buyer end");
            APIResponse.put("message", "Invalid RBX tokens at buyer end");
            Functions.updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            return APIResponse;
        }
        if (rbxTokenAuth.equals("420")) {
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
            output.close();
            input.close();
            sellerSocket.close();
            sellerMutex = false;
            SellerLogger.info("Insufficient RBX tokens at buyer end");
            APIResponse.put("message", "Insufficient RBX tokens at buyer end");
            Functions.updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            return APIResponse;
        }
        JSONObject rbxData = new JSONObject(rbxTokenAuth);
        String buyerSign = rbxData.getString("authBuyerBySeller");
        JSONObject rbxTokenDetails = rbxData.getJSONObject("rbxTokenDetails");
        JSONArray rbxTokens = rbxTokenDetails.getJSONArray("rbxTokens");
        JSONArray rbxTokenChains = rbxTokenDetails.getJSONArray("rbxTokenChains");
        JSONArray rbxTokenHeader = rbxTokenDetails.getJSONArray("rbxTokenHeader");
        String messageContent = rbxTokenDetails.toString();
        String consensusID = Functions.calculateHash(messageContent, "SHA3-256");
        Functions.writeToFile(Functions.LOGGER_PATH + "consensusID", consensusID, Boolean.valueOf(false));
        String consensusIdIpfsHash = IPFSNetwork.addHashOnly(Functions.LOGGER_PATH + "consensusID", ipfs);
        Functions.deleteFile(Functions.LOGGER_PATH + "consensusID");
        int ipfsGetFlag = 0;
        ArrayList<String> allRbxTokenContent = new ArrayList<>();
        ArrayList<String> allRbxTokenChainContent = new ArrayList<>();
        for (int k = 0; k < amount; k++) {
            String TokenChainContent = IPFSNetwork.get(rbxTokenChains.getString(k), ipfs);
            allRbxTokenChainContent.add(TokenChainContent);
            String TokenContent = IPFSNetwork.get(rbxTokens.getString(k), ipfs);
            allRbxTokenContent.add(TokenContent);
            ipfsGetFlag++;
        }
        IPFSNetwork.repo(ipfs);
        if (!IPFSNetwork.dhtEmpty(consensusIdIpfsHash, ipfs)) {
            SellerLogger.debug("consensus ID not unique" + consensusIdIpfsHash);
            output.println("420");
            APIResponse.put("did", buyerDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Consensus ID not unique");
            SellerLogger.info("Consensus ID not unique");
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
            output.close();
            input.close();
            sellerSocket.close();
            return APIResponse;
        }
        if (ipfsGetFlag != amount) {
            output.println("421");
            APIResponse.put("did", buyerDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Tokens not verified");
            SellerLogger.info("Tokens not verified");
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
            output.close();
            input.close();
            sellerSocket.close();
            return APIResponse;
        }
        JSONObject dataObject = new JSONObject();
        dataObject.put("tid", tid);
        dataObject.put("message", message);
        dataObject.put("messageRbx", messageContent);
        dataObject.put("buyerDidIpfsHash", buyerDidIpfsHash);
        dataObject.put("pvt", pvt);
        dataObject.put("sellerDidIpfsHash", sellerDidIpfsHash);
        dataObject.put("nftToken", nftTokenIpfsHash);
        dataObject.put("rbxTokens", rbxTokens);
        dataObject.put("alphaList", alphaPeersList);
        dataObject.put("betaList", betaPeersList);
        dataObject.put("gammaList", gammaPeersList);
        SellerLogger.debug("dataobject " + dataObject.toString());
        SellerLogger.debug("nftConsensus Setup Begins");
        InitiatorProcedure.nftConsensusSetUp(dataObject.toString(), ipfs, Functions.SELLER_PORT + 225, alphaSize);
        SellerLogger.debug("nftConsensus Done");
        SellerLogger.debug("length on seller " + InitiatorConsensus.quorumSignature.length() + " response count " + InitiatorConsensus.quorumResponse);
        if (InitiatorConsensus.quorumSignature.length() < Functions.minQuorum(alphaSize) + 2 * Functions.minQuorum(7)) {
            SellerLogger.debug("Consensus Failed");
            sellerDetails2Buyer.put("status", "Consensus Failed");
            sellerDetails2Buyer.put("quorumsign", InitiatorConsensus.quorumSignature.toString());
            output.println(sellerDetails2Buyer);
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
            output.close();
            input.close();
            sellerSocket.close();
            sellerMutex = false;
            Functions.updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Transaction declined by Quorum");
            return APIResponse;
        }
        SellerLogger.debug("Consensus Reached");
        sellerDetails2Buyer.put("status", "Consensus Reached");
        sellerDetails2Buyer.put("quorumsign", InitiatorConsensus.quorumSignature.toString());
        output.println(sellerDetails2Buyer);
        SellerLogger.debug("Sent Seller/Consensus Details to Buyer");
        SellerLogger.debug("Quorum Signatures length " + InitiatorConsensus.quorumSignature.length());
        String signatureAuth = input.readLine();
        long endAuth = System.currentTimeMillis();
        long totalTime = endAuth - startTime;
        if (!signatureAuth.equals("200")) {
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
            SellerLogger.info("Authentication Failed");
            output.close();
            input.close();
            sellerSocket.close();
            sellerMutex = false;
            Functions.updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Quorum/Seller not authenticated");
            return APIResponse;
        }
        IPFSNetwork.unpin(nftTokenIpfsHash, ipfs);
        IPFSNetwork.repo(ipfs);
        SellerLogger.debug("Unpinned NFT Token"); 
        output.println("Unpinned NFT");
        String rbxUnpinStatus = input.readLine();
        if (!rbxUnpinStatus.equals("Unpinned RBX")) {
            SellerLogger.warn("Buyer failed to pin NFT Token"); 
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
            SellerLogger.info("Buyer failed to pin NFT Token"); 
            output.close();
            input.close();
            sellerSocket.close();
            sellerMutex = false;
            Functions.updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Buyer failed to pin NFT Token"); 
            return APIResponse;
        }
        int count = 0;
        int m;
        for (m = 0; m < amount; m++) {
            FileWriter fileWriter = new FileWriter(Functions.TOKENS_PATH + allRbxTokenContent.get(m));
            fileWriter.write(allRbxTokenContent.get(m));
            fileWriter.close();
            IPFSNetwork.add(Functions.TOKENS_PATH + allRbxTokenContent.get(m), ipfs);
            IPFSNetwork.pin(rbxTokens.getString(m), ipfs);
            count++;
        }
        if (count == amount) {
            SellerLogger.debug("Pinned All RBX Tokens");
            for (m = 0; m < amount; m++) {
                ArrayList<String> groupTokens = new ArrayList<>();
                for (int i1 = 0; i1 < amount; i1++) {
                    if (!rbxTokens.getString(m).equals(rbxTokens.getString(i1)))
                        groupTokens.add(rbxTokens.getString(i1));
                }
                JSONArray arrToken = new JSONArray();
                JSONObject objectToken = new JSONObject();
                objectToken.put("tokenHash", rbxTokens.getString(m));
                arrToken.put(objectToken);
                JSONArray arr1 = new JSONArray(allRbxTokenChainContent.get(m));
                JSONObject obj2 = new JSONObject();
                obj2.put("buyerSign", buyerSign);
                obj2.put("buyerDID", buyerDidIpfsHash);
                obj2.put("group", groupTokens);
                obj2.put("comment", comment);
                obj2.put("tid", tid);
                arr1.put(obj2);
                Functions.writeToFile(Functions.TOKENCHAIN_PATH + allRbxTokenChainContent.get(m) + ".json", arr1.toString(), Boolean.valueOf(false));
            }
            JSONObject rbxPinData = new JSONObject();
            rbxPinData.put("status", "Pinned RBX");
            rbxPinData.put("essentialShare", InitiatorProcedure.essential);
            output.println(rbxPinData.toString());
        } else {
            JSONObject rbxPinData = new JSONObject();
            rbxPinData.put("status", "Failed to pin RBX");
            rbxPinData.put("essentialShare", "null");
            output.println(rbxPinData.toString());
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
            output.close();
            input.close();
            sellerSocket.close();
            sellerMutex = false;
            Functions.updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Seller Failed to pin RBX");
            SellerLogger.info("Incomplete Transaction");
            return APIResponse;
        }
        String respAuth = input.readLine();
        if (!respAuth.equals("Send Response")) {
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
            output.close();
            input.close();
            sellerSocket.close();
            sellerMutex = false;
            Functions.updateQuorum(quorumArray, null, false, type);
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", tid);
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Receiver process not over");
            SellerLogger.info("Incomplete Transaction");
            return APIResponse;
        }
        Iterator<String> keys = InitiatorConsensus.quorumSignature.keys();
        JSONArray signedQuorumList = new JSONArray();
        while (keys.hasNext())
            signedQuorumList.put(keys.next());
        APIResponse.put("tid", tid);
        APIResponse.put("status", "Success");
        APIResponse.put("sellerDID", sellerDidIpfsHash);
        APIResponse.put("message", "NFT transferred successfully!");
        APIResponse.put("quorumlist", signedQuorumList);
        APIResponse.put("buyerDID", buyerDidIpfsHash);
        APIResponse.put("totaltime", totalTime);
        Functions.updateQuorum(quorumArray, signedQuorumList, true, type);
        JSONObject nftTransactionRecord = new JSONObject();
        nftTransactionRecord.put("role", "Seller");
        nftTransactionRecord.put("sellerDID", sellerDidIpfsHash);
        nftTransactionRecord.put("buyerDID", buyerDidIpfsHash);
        nftTransactionRecord.put("amount", amount);
        nftTransactionRecord.put("rbxTokens", rbxTokens);
        nftTransactionRecord.put("nftToken", nftTokenIpfsHash);
        //nftTransactionRecord.put("eKey", eKey);
        //nftTransactionRecord.put("dKey", dKey);
        nftTransactionRecord.put("txn", tid);
        nftTransactionRecord.put("quorumList", signedQuorumList);
        nftTransactionRecord.put("Date", Functions.getCurrentUtcTime());
        nftTransactionRecord.put("totalTime", totalTime);
        nftTransactionRecord.put("comment", comment);
        nftTransactionRecord.put("essentialShare", InitiatorProcedure.essential);
        JSONArray nftTransactionHistoryEntry = new JSONArray();
        nftTransactionHistoryEntry.put(nftTransactionRecord);
        Functions.updateJSON("add", Functions.WALLET_DATA_PATH + "nftTransactionHistory.json", nftTransactionHistoryEntry.toString());
        Files.deleteIfExists(Paths.get(Functions.NFT_TOKENS_PATH + nftTokenIpfsHash, new String[0]));
        for (int n = 0; n < rbxTokens.length(); n++) {
            String bank = rbxTokenHeader.getString(n);
            String bankFile = Functions.readFile(Functions.PAYMENTS_PATH + bank + ".json");
            JSONArray bankArray = new JSONArray(bankFile);
            JSONObject tokenObject = new JSONObject();
            tokenObject.put("tokenHash", rbxTokens.getString(n));
            bankArray.put(tokenObject);
            Functions.writeToFile(Functions.PAYMENTS_PATH + "TokenMap.json", bankArray.toString(), Boolean.valueOf(false));
        }
        SellerLogger.info("Transaction Successful");
        IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerId);
        output.close();
        input.close();
        sellerSocket.close();
        sellerMutex = false;
        return APIResponse;
    }
}
