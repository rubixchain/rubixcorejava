package com.rubix.NFT;


import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Resources.FractionChooser;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
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

public class Buyer {
    public static Logger BuyerLogger = Logger.getLogger(Buyer.class);

    private static final JSONObject APIResponse = new JSONObject();

    private static final IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + Functions.IPFS_PORT);

    public static String receive() {
        Functions.pathSet();
        ServerSocket ss = null;
        Socket sk = null;
        String sellerPeerID = null;
        try {
            int quorumSignVerifyCount = 0;
            JSONObject quorumSignatures = null;
            ArrayList<String> quorumDID = new ArrayList<>();
            PropertyConfigurator.configure(Functions.LOGGER_PATH + "log4jWallet.properties");
            String buyerPeerID = Functions.getPeerID(Functions.DATA_PATH + "DID.json");
            String buyerDidIpfsHash = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "didHash", "peerid", buyerPeerID);
            IPFSNetwork.listen(buyerPeerID + "NFT", Functions.BUYER_PORT);
            ss = new ServerSocket(Functions.BUYER_PORT);
            BuyerLogger.debug("Listening on " + Functions.BUYER_PORT + " with app name " + buyerPeerID + "NFT");
            sk = ss.accept();
            BufferedReader input = new BufferedReader(new InputStreamReader(sk.getInputStream()));
            PrintStream output = new PrintStream(sk.getOutputStream());
            long startTime = System.currentTimeMillis();
            sellerPeerID = input.readLine();
            BuyerLogger.debug("Seller PeerID received");
            String sellerDidIpfsHash = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "didHash", "peerid", sellerPeerID);
            String sellerWidIpfsHash = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "walletHash", "peerid", sellerPeerID);
            if (!sellerDidIpfsHash.contains("Qm") || !sellerWidIpfsHash.contains("Qm")) {
                output.println("420");
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Invalid Seller DID.");
                BuyerLogger.info("Seller details not available in datatable");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            Functions.nodeData(sellerDidIpfsHash, sellerWidIpfsHash, ipfs);
            File sellerDIDFile = new File(Functions.DATA_PATH + sellerDidIpfsHash + "DID.png");
            if (!sellerDIDFile.exists()) {
                output.println("420");
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller details not available in network , please sync");
                BuyerLogger.info("Sender details not available");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            output.println("200");
            BuyerLogger.debug("PeerAuth sent to Seller");
            String nftData = input.readLine();
            BuyerLogger.debug("NFT Token details received from Seller");
            JSONObject nftTokenDetails = new JSONObject(nftData);
            String nftToken = nftTokenDetails.getString("nftToken");
            String nftTokenChain = nftTokenDetails.getString("nftTokenChain");
            String message = nftTokenDetails.toString();
            String nftID = Functions.calculateHash(message, "SHA3-256");
            Functions.writeToFile(Functions.LOGGER_PATH + "nftID", nftID, Boolean.valueOf(false));
            String nftIdIpfsHash = IPFSNetwork.addHashOnly(Functions.LOGGER_PATH + "nftID", ipfs);
            Functions.deleteFile(Functions.LOGGER_PATH + "nftID");
            String nftTokenChainContent = IPFSNetwork.get(nftTokenChain, ipfs);
            String nftTokenContent = IPFSNetwork.get(nftToken, ipfs);
            BuyerLogger.debug("NFT token content extracted");
            IPFSNetwork.repo(ipfs);
            if (!IPFSNetwork.dhtEmpty(nftIdIpfsHash, ipfs)) {
                BuyerLogger.debug("NFT ID not unique" + nftIdIpfsHash);
                output.println("420");
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "NFT ID not unique");
                BuyerLogger.info("NFT ID not unique");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            if (nftTokenContent.equals("")) {
                output.println("421");
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Invalid NFT Token");
                BuyerLogger.info("Invalid NFT Token");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            output.println("200");
            BuyerLogger.debug("NftTokenAuth: 200. Sent to Seller");
            String assetData = input.readLine();
            BuyerLogger.debug("Received asset details from Seller");
            JSONObject assetDetails = new JSONObject(assetData);
            int amount = assetDetails.getInt("amount");
            JSONArray rbxTokens = FractionChooser.calculate(amount);
            JSONArray rbxTokenHeader = FractionChooser.tokenHeader;
            ArrayList<String> rbxTokensChainsPushed = new ArrayList<>();
            if (rbxTokens.length() == 0) {
                output.println("420");
                BuyerLogger.debug("Insufficient balance with buyer");
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Insufficient balance");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            for (int i = 0; i < rbxTokens.length(); i++) {
                File rbxtoken = new File(Functions.TOKENS_PATH+rbxTokens.get(i));
                File rbxtokenchain = new File(Functions.TOKENCHAIN_PATH+rbxTokens.get(i)+".json");
                BuyerLogger.debug("" + rbxtoken + " and " + rbxtoken);
                if (!rbxtoken.exists() || !rbxtokenchain.exists()) {
                    output.println("421");
                    BuyerLogger.info("Tokens Not Verified");
                    APIResponse.put("did", sellerDidIpfsHash);
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Invalid token(s)");
                    IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();
                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }
                IPFSNetwork.add(Functions.TOKENS_PATH+rbxTokens.get(i), ipfs);
                String tokenChainHash = IPFSNetwork.add(Functions.TOKENCHAIN_PATH+rbxTokens.get(i)+".json", ipfs);
                rbxTokensChainsPushed.add(tokenChainHash);
            }
            JSONObject rbxTokenDetails = new JSONObject();
            rbxTokenDetails.put("rbxTokens", rbxTokens);
            rbxTokenDetails.put("rbxTokenChains", rbxTokensChainsPushed);
            rbxTokenDetails.put("rbxTokenHeader", rbxTokenHeader);
            String pvt = Functions.DATA_PATH+ buyerDidIpfsHash + "PrivateShare.png";
            String buyerSign = Functions.getSignFromShares(pvt, Functions.calculateHash(rbxTokens.toString() + rbxTokenHeader.toString() + rbxTokensChainsPushed.toString(), "SHA3-256"));
            JSONObject rbxData = new JSONObject();
            rbxData.put("rbxTokenDetails", rbxTokenDetails);
            rbxData.put("authBuyerBySeller", buyerSign);
            output.println(rbxData);
            BuyerLogger.debug("Sent rbxData and Buyer sign for authentication to seller.");
            String rbxTokensAuth = input.readLine();
            if (rbxTokensAuth.equals("421")) {
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "RBX tokens not verified at seller node");
                BuyerLogger.info("RBX tokens not verified at sender node");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            if (rbxTokensAuth.equals("420")) {
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Consensus ID not Unique");
                BuyerLogger.info("Consensus ID not Unique");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            BuyerLogger.debug("consensus/seller details received from Seller");
            JSONObject consensusDetails = new JSONObject(rbxTokensAuth);
            String sellerSignature = consensusDetails.getString("sign");
            String tid = consensusDetails.getString("tid");
            String comment = consensusDetails.getString("comment");
            String Status = consensusDetails.getString("status");
            String QuorumDetails = consensusDetails.getString("quorumsign");
            BuyerLogger.debug("Consensus Status:  " + Status);
            if (!Status.equals("Consensus Failed")) {
                boolean yesQuorum = false;
                if (Status.equals("Consensus Reached")) {
                    BuyerLogger.debug("Quorum Signatures: " + QuorumDetails);
                    quorumSignatures = new JSONObject(QuorumDetails);
                    BuyerLogger.debug("Quorum Signatures length : " + quorumSignatures.length());
                    Iterator<String> dids = quorumSignatures.keys();
                    while (dids.hasNext()) {
                        String did = dids.next();
                        quorumDID.add(did);
                    }
                    for (String quorumDidIpfsHash : quorumDID) {
                        String quorumWidIpfsHash = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "walletHash", "didHash", quorumDidIpfsHash);
                        File quorumDataFolder = new File(Functions.DATA_PATH + "/");
                        if (!quorumDataFolder.exists()) {
                            quorumDataFolder.mkdirs();
                            IPFSNetwork.getImage(quorumDidIpfsHash, ipfs, Functions.DATA_PATH + quorumDidIpfsHash + "DID.png");
                            IPFSNetwork.getImage(quorumWidIpfsHash, ipfs, Functions.DATA_PATH + quorumDidIpfsHash + "PublicShare.png");
                            BuyerLogger.debug("Quorum Data " + quorumDidIpfsHash + " Added");
                            continue;
                        }
                        BuyerLogger.debug("Quorum Data " + quorumDidIpfsHash + " Available");
                    }
                    for (int j = 0; j < quorumSignatures.length(); j++) {
                        JSONObject jSONObject = new JSONObject();
                        jSONObject.put("did", quorumDID.get(j));
                        jSONObject.put("signature", quorumSignatures.getString(quorumDID.get(j)));
                        boolean val = Authenticate.verifySignature(jSONObject.toString());
                        if (val)
                            quorumSignVerifyCount++;
                    }
                    BuyerLogger.debug("Verified Quorum Count " + quorumSignVerifyCount);
                    yesQuorum = (quorumSignVerifyCount >= quorumSignatures.length());
                }
                String hash = Functions.calculateHash(nftToken + nftTokenChain + amount + buyerDidIpfsHash, "SHA3-256");
                JSONObject detailsForVerify = new JSONObject();
                detailsForVerify.put("did", sellerDidIpfsHash);
                detailsForVerify.put("hash", hash);
                detailsForVerify.put("signature", sellerSignature);
                boolean yesSender = Authenticate.verifySignature(detailsForVerify.toString());
                BuyerLogger.debug("Seller Auth Hash" + hash);
                BuyerLogger.debug("Quorum Auth : " + yesQuorum + "Seller Auth : " + yesSender);
                if (!yesSender || !yesQuorum) {
                    output.println("420");
                    APIResponse.put("did", sellerDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Seller/Quorum not verified");
                    BuyerLogger.info("Seller/Quorum not verified");
                    IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();
                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }
                IPFSNetwork.repo(ipfs);
                BuyerLogger.debug("Seller and Quorum Verified");
                output.println("200");
                String nftUnpinStatus = input.readLine();
                if (nftUnpinStatus.equals("Unpinned NFT")) {
                    FileWriter fileWriter = new FileWriter(Functions.NFT_TOKENS_PATH+nftToken);
                    fileWriter.write(nftTokenContent);
                    fileWriter.close();
                    IPFSNetwork.add(Functions.NFT_TOKENS_PATH + nftToken, ipfs);
                    IPFSNetwork.pin(nftToken, ipfs);
                
                    try {
                        BuyerLogger.debug("Successfully Pinned NFT Token"); 
                        for (int j = 0; j < rbxTokens.length(); j++)
                            IPFSNetwork.unpin(String.valueOf(rbxTokens.get(j)), ipfs);
                        IPFSNetwork.repo(ipfs);
                        BuyerLogger.debug("Unpinned RBX Tokens");
                        output.println("Unpinned RBX");
                        String rbxPinData = input.readLine();
                        JSONObject rbxPinDetails = new JSONObject(rbxPinData);
                        String status = rbxPinDetails.getString("status");
                        if (status.equals("Failed to pin RBX")) {
                            APIResponse.put("did", sellerDidIpfsHash);
                            APIResponse.put("tid", "null");
                            APIResponse.put("status", "Failed");
                            APIResponse.put("message", "Seller Failed to pin RBX Tokens");
                            BuyerLogger.info(" Transaction failed");
                            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                            output.close();
                            input.close();
                            sk.close();
                            ss.close();
                            return APIResponse.toString();
                        }
                        String essentialShare = rbxPinDetails.getString("essentialShare");
                        long endTime = System.currentTimeMillis();
                        JSONArray currentNftTokenChain = new JSONArray(nftTokenChainContent);
                        JSONObject newRecord = new JSONObject();
                        newRecord.put("sellerDID", sellerDidIpfsHash);
                        newRecord.put("sellerSign", sellerSignature);
                        newRecord.put("comment", comment);
                        newRecord.put("tid", tid);
                        currentNftTokenChain.put(newRecord);
                        Functions.writeToFile(Functions.NFT_TOKENCHAIN_PATH+nftTokenChain+".json", currentNftTokenChain.toString(), Boolean.valueOf(false));
                        Iterator<String> keys = quorumSignatures.keys();
                        JSONArray quorumList = new JSONArray();
                        while (keys.hasNext())
                            quorumList.put(keys.next());
                        JSONObject nftTransactionRecord = new JSONObject();
                        nftTransactionRecord.put("role", "Buyer");
                        nftTransactionRecord.put("sellerDID", sellerDidIpfsHash);
                        nftTransactionRecord.put("buyerDID", buyerDidIpfsHash);
                        nftTransactionRecord.put("amount", amount);
                        nftTransactionRecord.put("rbxTokens", rbxTokens);
                        nftTransactionRecord.put("nftToken", nftToken);
                        nftTransactionRecord.put("txn", tid);
                        nftTransactionRecord.put("quorumList", quorumList);
                        nftTransactionRecord.put("Date", Functions.getCurrentUtcTime());
                        nftTransactionRecord.put("totalTime", endTime - startTime);
                        nftTransactionRecord.put("comment", comment);
                        nftTransactionRecord.put("essentialShare", essentialShare);
                        JSONArray nftTransactionHistoryEntry = new JSONArray();
                        nftTransactionHistoryEntry.put(nftTransactionRecord);
                        Functions.updateJSON("add", Functions.WALLET_DATA_PATH + "nftTransactionHistory.json", nftTransactionHistoryEntry.toString());
                        int k;
                        for (k = 0; k < rbxTokens.length(); k++)
                            Files.deleteIfExists(Paths.get(Functions.TOKENS_PATH+rbxTokens.get(k), new String[0]));
                        for (k = 0; k < amount; k++)
                            Functions.updateJSON("remove", Functions.PAYMENTS_PATH+rbxTokenHeader.getString(k), rbxTokens.getString(k));
                        BuyerLogger.info("Transaction ID: " + tid + "Transaction Successful");
                        output.println("Send Response");
                        APIResponse.put("sellerDID", sellerDidIpfsHash);
                        APIResponse.put("buyerDID", buyerDidIpfsHash);
                        APIResponse.put("tid", tid);
                        APIResponse.put("status", "Success");
                        APIResponse.put("tokens", nftToken);
                        APIResponse.put("comment", comment);
                        APIResponse.put("message", "NFT Transaction Successful");
                        BuyerLogger.info("Transaction Successful");
                    } catch (Exception e) {
                        output.println("Failed pinning on Buyer Node");
                        APIResponse.put("did", sellerDidIpfsHash);
                        APIResponse.put("tid", "null");
                        APIResponse.put("status", "Failed");
                        APIResponse.put("message", "Failed in pinning NFT token");
                        BuyerLogger.info(" Transaction failed", e);
                    }
                    IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                    output.close();
                    input.close();
                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Seller Failed to unpin");
                BuyerLogger.info(" Transaction failed");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            APIResponse.put("did", sellerDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Consensus failed at Sender side");
            BuyerLogger.info(" Transaction failed");
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
            output.close();
            input.close();
            sk.close();
            ss.close();
            return APIResponse.toString();
        } catch (Exception e) {
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
            BuyerLogger.error("Exception Occurred", e);
            return APIResponse.toString();
        } finally {
            try {
                ss.close();
                sk.close();
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
            } catch (Exception e) {
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + sellerPeerID);
                BuyerLogger.error("Exception Occurred", e);
            }
        }
    }
}
