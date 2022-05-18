package com.rubix.NFT;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Resources.Functions;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;


import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.rubix.NFTResources.EnableNft.*;
import static com.rubix.Resources.Functions.*;
import com.rubix.PasswordMasking.PasswordField;

import static com.rubix.NFTResources.NFTFunctions.*;

public class NftSeller {
    public static Logger nftSellerLogger = Logger.getLogger(NftSeller.class);

    private static final JSONObject APIResponse = new JSONObject();

    private static final IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + Functions.IPFS_PORT);

    // token limit for each level


    // public static JSONObject APIResponse = new JSONObject();
    public static String receive() {

        Functions.pathSet();
        nftPathSet();
        ServerSocket ss = null;
        Socket sk = null;
        String buyerPeerID = null;


        BufferedReader sysInput = new BufferedReader(new InputStreamReader(System.in));

        try {
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
            int quorumSignVerifyCount = 0;
            JSONObject quorumSignatures = null;
            ArrayList<String> quorumDID = new ArrayList<>();
            String sellerPeerID = Functions.getPeerID(Functions.DATA_PATH + "DID.json");
            String sellerDidIpfsHash = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "didHash", "peerid",
                    sellerPeerID);
            IPFSNetwork.listen(sellerPeerID + "NFT", SELLER_PORT);
            ss = new ServerSocket(SELLER_PORT);
            nftSellerLogger.debug("Listening on " + SELLER_PORT + "with app name " + sellerPeerID + "NFT");
            sk = ss.accept();
            BufferedReader input = new BufferedReader(new InputStreamReader(sk.getInputStream()));
            PrintStream output = new PrintStream(sk.getOutputStream());
            long startTime = System.currentTimeMillis();

            try {
                buyerPeerID = input.readLine();
            } catch (SocketException e) {
                nftSellerLogger.warn("Buyer Stream Null - Buyer Details");
                APIResponse.put("did", "");
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer Stream Null - Buyer Detail");

                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            nftSellerLogger.debug("Buyer PeerID received");
            String buyerDidIpfsHash = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "didHash", "peerid",
                    buyerPeerID);
            String buyerWidIpfsHash = Functions.getValues(Functions.DATA_PATH + "DataTable.json", "walletHash",
                    "peerid", buyerPeerID);
            if (!buyerDidIpfsHash.contains("Qm") || !buyerWidIpfsHash.contains("Qm")) {
                output.println("420");
                APIResponse.put("did", buyerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Invalid Buyer DID.");
                nftSellerLogger.info("Buyer details not available in datatable");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                output.close();
                input.close();

                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            nodeData(buyerDidIpfsHash, buyerWidIpfsHash, ipfs);
            File buyerDIDFile = new File(Functions.DATA_PATH + buyerDidIpfsHash + "/DID.png");
            if (!buyerDIDFile.exists()) {
                output.println("420");
                APIResponse.put("did", buyerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer details not available in network , please sync");
                nftSellerLogger.info("Buyer details not available");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                output.close();
                input.close();

                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            output.println("200");
            nftSellerLogger.debug("PeerAuth sent to Seller");

            String nftTokenIpfsHash;

            try {
                nftTokenIpfsHash = input.readLine();
            } catch (SocketException e) {
                nftSellerLogger.warn("Buyer Stream Null - Buyer Details");
                APIResponse.put("did", "");
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer Stream Null - Buyer Detail");

                output.close();
                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }

            nftSellerLogger.debug("Seller recived the nft token for sale : " + nftTokenIpfsHash);
            nftSellerLogger.debug("Seller Checking if he has NFT token : " + nftTokenIpfsHash);
            File nfttoken = new File(NFT_TOKENS_PATH + nftTokenIpfsHash);
            File nfttokenchain = new File(NFT_TOKENCHAIN_PATH + nftTokenIpfsHash + ".json");

            // nftSellerLogger.debug("Seller Checking if he has NFT token");
            if (!nfttoken.exists() || !nfttokenchain.exists()) {
                output.println("420");
                nftSellerLogger.debug("NFT Token " + nftTokenIpfsHash + " and NFT Token chain " + nftTokenIpfsHash
                        + ".json files does not exist");
                APIResponse.put("message", "NFT Token " + nftTokenIpfsHash + " and NFT Token chain " + nftTokenIpfsHash
                        + ".json files does not exist");
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                output.close();
                input.close();

                sk.close();
                ss.close();
                return APIResponse.toString();
            }
            nftSellerLogger.debug("Seller has nft token " + nftTokenIpfsHash);
            output.println("200");

            String p2pFlagstr;
            try {
                p2pFlagstr = input.readLine();
            } catch (SocketException e) {
                nftSellerLogger.warn("Buyer Stream Null - Buyer Details");
                APIResponse.put("did", "");
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer Stream Null - Buyer Detail");

                output.close();
                input.close();

                sk.close();
                ss.close();
                return APIResponse.toString();
            }

            int p2pFlag = Integer.parseInt(p2pFlagstr);

            if (p2pFlag == 1) {
                nftSellerLogger.debug("this is a Peer to Peer NFT Transaction");
                nftSellerLogger.info("Please enter the value of NFT in rbt to be shared to seller for sale");
                String rbtAmount = sysInput.readLine();
                nftSellerLogger.debug("Seller sending value of NFT to Buyer to approve ");

                output.println(rbtAmount);

                String saleAmountAuth;

                try {
                    saleAmountAuth = input.readLine();
                } catch (SocketException e) {
                    nftSellerLogger.warn("Buyer Stream Null - Buyer Details");
                    APIResponse.put("did", "");
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Buyer Stream Null - Buyer Detail");

                    output.close();
                    input.close();
                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }

                if (saleAmountAuth != null && !saleAmountAuth.equals("200")) {
                    nftSellerLogger.debug("Buyer did not agree to RBT amount for NFT ");
                    APIResponse.put("message", "Buyer did not agree to RBT amount for NFT ");
                    APIResponse.put("did", sellerDidIpfsHash);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                    output.close();
                    input.close();

                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }

                nftSellerLogger.debug("Buyer agreed to RBT amount for NFT");

                String balanceAuth;

                try {
                    balanceAuth = input.readLine();
                    nftSellerLogger.debug("Balamnce Auth sent from seller: " + balanceAuth);
                } catch (SocketException e) {
                    nftSellerLogger.warn("Buyer Stream Null - Buyer Details");
                    APIResponse.put("did", "");
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Buyer Stream Null - Buyer Detail");

                    output.close();
                    input.close();
                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }

                if (balanceAuth != null && !balanceAuth.equals("200")) {
                    nftSellerLogger.debug("Buyer does not have enough RBT balance for Purchase of NFT");
                    APIResponse.put("message", "Buyer does not have enough RBT balance for Purchase of NFT");
                    APIResponse.put("did", sellerDidIpfsHash);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                    output.close();
                    input.close();

                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }

                nftSellerLogger.debug("Buyer has required balance for NFT purchase");

                nftSellerLogger.debug("Seller Creating sale contract");

                nftSellerLogger.info("*******************************************************");

                char[] privateKeyPass = PasswordField.getPassword(System.in, "Enter the privateKey password: ");

                nftSellerLogger.info("*******************************************************");
                JSONObject data = new JSONObject();

                data.put("sellerDID", sellerDidIpfsHash);
                data.put("nftToken", nftTokenIpfsHash);
                data.put("rbtAmount", Double.parseDouble(rbtAmount));
                data.put("sellerPvtKeyPass", String.valueOf(privateKeyPass));
                String saleContract = createNftSaleContract(data.toString());

                JSONObject responseObj = new JSONObject(saleContract);

                JSONObject saleObject = new JSONObject();
                if (responseObj.getString("status").equals("Failed")) {
                    saleObject.put("saleContractIpfsHash", "");
                    saleObject.put("auth", "420");
                    output.println(saleObject.toString());
                    nftSellerLogger.debug("NFT Sale contract creation failed");
                    APIResponse.put("message",
                            "NFT Sale contract creation Failed : " + responseObj.getString("message"));
                    APIResponse.put("did", sellerDidIpfsHash);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                    output.close();
                    input.close();

                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }

                saleObject.put("saleContractIpfsHash", responseObj.getString("saleContractIpfsHash"));
                saleObject.put("auth", "200");

                nftSellerLogger.debug("sending sale contract details to buyer");
                output.println(saleObject.toString());

                nftSellerLogger.debug("sending sellers public key details to buyer");
                String sellerPublicKeyIpfsHash = getPubKeyIpfsHash();

                output.println(sellerPublicKeyIpfsHash);
            }

            nftSellerLogger.debug("Seller sending NFT token details to Buyer to verify");

            IPFSNetwork.add(NFT_TOKENS_PATH + nftTokenIpfsHash, ipfs);
            String nftTokenChainIpfsHash = IPFSNetwork.add(NFT_TOKENCHAIN_PATH + nftTokenIpfsHash + ".json", ipfs);

            JSONObject nftTokenDetails = new JSONObject();
            nftTokenDetails.put("nftToken", nftTokenIpfsHash);
            nftTokenDetails.put("nftTokenChain", nftTokenChainIpfsHash);
            nftTokenDetails.put("sellerDid", sellerDidIpfsHash);
            String nftID = calculateHash(nftTokenDetails.toString(), "SHA3-256");

            writeToFile(LOGGER_PATH + "nftID", nftID, false);
            String nftConsensusID = IPFSNetwork.addHashOnly(LOGGER_PATH + "nftID", ipfs);
            deleteFile(LOGGER_PATH + "nftID");

            nftSellerLogger.debug("Sending nft ConsensusID " + nftConsensusID + "to Buyer");
            nftTokenDetails.put("nftConsensusID", nftConsensusID);
            output.println(nftTokenDetails.toString());

            String nftTokenAuth;

            try {
                nftTokenAuth = input.readLine();
            } catch (SocketException e) {
                nftSellerLogger.warn("Buyer Stream Null - Buyer Details");
                APIResponse.put("did", "");
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer Stream Null - Buyer Detail");

                output.close();

                input.close();
                sk.close();
                ss.close();
                return APIResponse.toString();
            }

            if (nftTokenAuth != null && nftTokenAuth.startsWith("4")) {
                switch (nftTokenAuth) {
                    case "419":
                        nftSellerLogger.info("NFT Token " + nftTokenIpfsHash + " is of RAC Type 1 which is depricated");
                        APIResponse.put("message",
                                "NFT Token " + nftTokenIpfsHash + " is of RAC Type 1 which is depricated");
                    case "420":
                        nftSellerLogger.info("NFT Token Authenticity check Failed");
                        APIResponse.put("message", "NFT Token " + nftTokenIpfsHash + "Authenticity check Failed.");
                        break;
                    case "421":
                        nftSellerLogger.info("NFT Consensus ID not unique.");
                        APIResponse.put("message", "NFT Consensus ID not unique.");
                        break;
                    case "422":
                        nftSellerLogger.info("NFT Token ownership failed for " + nftTokenIpfsHash);
                        APIResponse.put("message", "TNFT Token ownership failed for " + nftTokenIpfsHash);
                        break;
                    case "423":
                        nftSellerLogger.info("NFT Token " + nftTokenIpfsHash + " has Expired");
                        APIResponse.put("message", "NFT Token " + nftTokenIpfsHash + " has Expired");

                }
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                output.close();
                input.close();

                sk.close();
                ss.close();
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                return APIResponse.toString();
            }
            nftSellerLogger.debug("NFT token auth code " + nftTokenAuth);

            String saleContractAuth;
            try {
                saleContractAuth = input.readLine();
            } catch (SocketException e) {
                nftSellerLogger.warn("Buyer Stream Null - Buyer Details");
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer Stream Null - Buyer Detail");

                output.close();
                input.close();

                sk.close();
                ss.close();
                return APIResponse.toString();
            }

            if (saleContractAuth != null && !saleContractAuth.equals("200")) {
                nftSellerLogger.info("Buyer was not able to verify sale contract");
                APIResponse.put("message", "Buyer " + buyerDidIpfsHash + " was not able to verify sale contract");
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "");
                APIResponse.put("status", "Failed");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                output.close();
                input.close();

                sk.close();
                ss.close();
                return APIResponse.toString();
            }

            nftSellerLogger.info("Buyer verified NFT sale contract");

            // NFT Seller waiting for Buyer Side confirmation of RBT Txn
            /* String rbtTxnAuth, rbtTxnres;
            
 */
            String consensusDetails;

            try {
                consensusDetails = input.readLine();
            } catch (SocketException e) {
                nftSellerLogger.warn("Buyer Stream Null - Buyer Details");
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", "null");
                APIResponse.put("status", "Failed");
                APIResponse.put("message", "Buyer Stream Null - Buyer Detail");

                output.close();
                input.close();

                sk.close();
                ss.close();
                return APIResponse.toString();
            }

            nftSellerLogger.debug("Recived quorum consesnsus data");

            JSONObject SenderDetails = new JSONObject(consensusDetails);
            String verifyBuyerBySeller = SenderDetails.getString("sign");
            String tid = SenderDetails.getString("tid");
            String comment = SenderDetails.getString("comment");
            String Status = SenderDetails.getString("status");
            String nftQuorumDetails = SenderDetails.getString("nftQuorumSign");

            JSONObject nftQuorumSig = new JSONObject(nftQuorumDetails);

            BufferedImage senderWidImage = ImageIO.read(new File(DATA_PATH + buyerDidIpfsHash + "/PublicShare.png"));
            String SenWalletBin = PropImage.img2bin(senderWidImage);

            nftSellerLogger.debug("Verifying Quorum ...  ");
            nftSellerLogger.debug("Please wait, this might take a few seconds");

            if (!Status.equals("Consensus Failed")) {
                boolean yesQuorum = false;
                nftSellerLogger.debug("Consensus Status got from nftBuyer " + Status);
                if (Status.equals("Consensus Reached")) {
                    quorumSignatures = new JSONObject(nftQuorumDetails);

                    String nftHash = calculateHash(nftTokenDetails.toString(), "SHA3-256");
                    String verifyQuorumHash = calculateHash(nftHash.concat(buyerDidIpfsHash), "SHA3-256");

                    Iterator<String> keys = quorumSignatures.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        quorumDID.add(key);
                    }

                    for (String quorumDidIpfsHash : quorumDID) {
                        syncDataTable(quorumDidIpfsHash, null);
                        String quorumWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash",
                                quorumDidIpfsHash);

                        nodeData(quorumDidIpfsHash, quorumWidIpfsHash, ipfs);
                    }

                    for (int i = 0; i < quorumSignatures.length(); i++) {

                        JSONObject detailsForVerify = new JSONObject();
                        detailsForVerify.put("did", quorumDID.get(i));
                        detailsForVerify.put("hash", verifyQuorumHash);
                        detailsForVerify.put("signature", quorumSignatures.getString(quorumDID.get(i)));
                        boolean val = Authenticate.verifySignature(detailsForVerify.toString());
                        if (val)
                            quorumSignVerifyCount++;
                    }
                    nftSellerLogger.debug("Verified Quorum Count " + quorumSignVerifyCount);

                    yesQuorum = quorumSignVerifyCount >= quorumSignatures.length();
                }

                String hash = calculateHash(tid + nftTokenDetails.toString() + buyerDidIpfsHash, "SHA3-256");
                nftSellerLogger.debug("Hash to verify Sender: " + hash);
                JSONObject detailsForVerify = new JSONObject();
                detailsForVerify.put("did", buyerDidIpfsHash);
                detailsForVerify.put("hash", hash);
                detailsForVerify.put("signature", verifyBuyerBySeller);

                boolean yesSender = Authenticate.verifySignature(detailsForVerify.toString());
                nftSellerLogger.debug("Quorum Auth : " + yesQuorum + " Sender Auth : " + yesSender);
                if (!(yesSender && yesQuorum)) {
                    output.println("420");
                    APIResponse.put("did", sellerDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Sender / Quorum not verified");
                    nftSellerLogger.info("Sender / Quorum not verified");
                    IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                    output.close();
                    input.close();

                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }

                // IPFSNetwork.repo(ipfs);
                nftSellerLogger.debug("Sender and Quorum Verified");
                output.println("200");

                nftSellerLogger.debug("Seller Waiting for Buyer to transfer RBT");
                String rbtTxnId,rbtTxnAuth, rbtTxnRes;
                try {
                    rbtTxnAuth = input.readLine();
                    rbtTxnRes = input.readLine();
                } catch (SocketException e) {
                    nftSellerLogger.warn("Buyer Stream Null - Buyer Details");
                    APIResponse.put("did", sellerDidIpfsHash);
                    APIResponse.put("tid", "null");
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Buyer Stream Null - Buyer Detail");
    
                    output.close();
                    input.close();
    
                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }
                if (rbtTxnAuth != null && !rbtTxnAuth.equals("200")) {
                    JSONObject rbtTxnresponse = new JSONObject(rbtTxnRes);
                    nftSellerLogger.info(rbtTxnresponse.getString("message"));
                    APIResponse.put("message", rbtTxnresponse.getString("message"));
                    APIResponse.put("did", sellerDidIpfsHash);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                    output.close();
                    input.close();
    
                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }
    
                nftSellerLogger.debug("RBT txn for NFT Auth code "+rbtTxnAuth);
                nftSellerLogger.debug("message "+ rbtTxnRes);

                
                try {
                    rbtTxnId = input.readLine();
                } catch (SocketException e) {
                    nftSellerLogger.warn("Buyer Stream Null - Buyer Details");
                    APIResponse.put("did", sellerDidIpfsHash);
                    APIResponse.put("tid", tid);
                    APIResponse.put("status", "Failed");
                    APIResponse.put("message", "Buyer Stream Null - Buyer Detail");

                    output.close();
                    input.close();

                    sk.close();
                    ss.close();
                    return APIResponse.toString();
                }

                nftSellerLogger.debug("Received RBT Txn ID " + rbtTxnId);

                nftSellerLogger.debug("Seller Verifying RBT txn from Buyer for NFT");

                /**
                 * NFT SEller Checks RBT Transaction to see if RBT is transfered seuccessfully
                 */
                URL rbtTransferApi = new URL("http://localhost:1898/getTxnDetails");
                HttpURLConnection rbtCon = (HttpURLConnection) rbtTransferApi.openConnection();

                // Setting basic post request
                rbtCon.setRequestMethod("POST");
                rbtCon.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
                rbtCon.setRequestProperty("Accept", "application/json");
                rbtCon.setRequestProperty("Content-Type", "application/json");
                rbtCon.setRequestProperty("Authorization", "null");

                // Serialization
                JSONObject rbtApiPayload = new JSONObject();
                rbtApiPayload.put("transactionID", rbtTxnId);

                // Send post request
                rbtCon.setDoOutput(true);
                DataOutputStream wrRbtAPI = new DataOutputStream(rbtCon.getOutputStream());
                wrRbtAPI.writeBytes(rbtApiPayload.toString());
                wrRbtAPI.flush();
                wrRbtAPI.close();

                int rbtApiResponseCode = rbtCon.getResponseCode();
                nftSellerLogger.debug("Sending 'POST' request to URL : " + "http://localhost:1898/getTxnDetails");
                nftSellerLogger.debug("Post Data : " + rbtApiPayload);
                nftSellerLogger.debug("Response Code : " + rbtApiResponseCode);

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

                nftSellerLogger.info("Response of RBT Txn Details API call  is : " + rbtApiResstr.toString());

                JSONObject rbtTxnDetObj = new JSONObject(rbtApiResstr.toString());
                String status = rbtTxnDetObj.getString("status");

                if (!status.equals("true")) {
                    output.println("420");
                    APIResponse.put("message", rbtTxnDetObj.getJSONObject("data").getString("message"));
                    APIResponse.put("did", sellerDidIpfsHash);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                    output.close();
                    input.close();

                    sk.close();
                    ss.close();
                    return APIResponse.toString();

                }
                nftSellerLogger.debug("RBT Txn Status from Buyer to Seller is : "+ status);

                JSONObject rbtTxnData = rbtTxnDetObj.getJSONObject("data");
                JSONArray rbtTxnDataRes = rbtTxnData.getJSONArray("response");

                JSONObject rbtTxnDataResObj = rbtTxnDataRes.getJSONObject(0);

                double rbtReceived = rbtTxnDataResObj.getDouble("amount-received");

                if (SenderDetails.getDouble("rbtAmount") != rbtReceived) {
                    output.println("421");
                    APIResponse.put("message",
                            "Mismatch between RBT Txn details token amount and request token amount for NFT");
                    APIResponse.put("did", sellerDidIpfsHash);
                    APIResponse.put("tid", "");
                    APIResponse.put("status", "Failed");
                    IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                    output.close();
                    input.close();

                    sk.close();
                    ss.close();
                    return APIResponse.toString();

                }

                nftSellerLogger.debug("Buyer Sent "+SenderDetails.getDouble("rbtAmount")+"RBT");
                output.println("200");


                nftSellerLogger.debug("Seller preparing to unpin NFT");
                IPFSNetwork.unpin(nftTokenIpfsHash, ipfs);
                // IPFSNetwork.repo(ipfs);
                nftSellerLogger.debug("Seller unpinned NFT");


                output.println("NFT-UnPinned");

                /*
                 * String essentialShare;
                 * try {
                 * essentialShare = input.readLine();
                 * } catch (SocketException e) {
                 * nftSellerLogger.warn("Sender Stream Null - EShare Details");
                 * APIResponse.put("did", "");
                 * APIResponse.put("tid", "null");
                 * APIResponse.put("status", "Failed");
                 * APIResponse.put("message", "Sender Stream Null - EShare Details");
                 * 
                 * output.close();
                 * input.close();
                 * 
                 * sk.close();
                 * ss.close();
                 * return APIResponse.toString();
                 * 
                 * }
                 */
                long endTime = System.currentTimeMillis();

                // Delete nft token from NFTSeller once traansfered
                deleteFile(NFT_TOKENS_PATH + nftTokenIpfsHash);

                JSONObject nftTransactionRecord = new JSONObject();
                nftTransactionRecord.put("role", "Seller");
                nftTransactionRecord.put("tokens", nftTokenIpfsHash);
                nftTransactionRecord.put("txn", tid);
                nftTransactionRecord.put("rbtTxn", rbtTxnId);
                nftTransactionRecord.put("quorumList", nftQuorumSig.keys());
                nftTransactionRecord.put("senderDID", sellerDidIpfsHash);
                nftTransactionRecord.put("receiverDID", buyerDidIpfsHash);
                nftTransactionRecord.put("Date", getCurrentUtcTime());
                nftTransactionRecord.put("totalTime", (endTime - startTime));
                nftTransactionRecord.put("comment", comment);
                // nftTransactionRecord.put("essentialShare", essentialShare);
                nftTransactionRecord.put("amount-received", SenderDetails.getDouble("rbtAmount"));

                JSONArray nftTransactionHistoryEntry = new JSONArray();
                nftTransactionHistoryEntry.put(nftTransactionRecord);
                updateJSON("add", WALLET_DATA_PATH + "nftTransactionHistory.json",
                        nftTransactionHistoryEntry.toString());

                nftSellerLogger.info("Transaction ID: " + tid + "NFT Transaction Successful");
                output.println("Send Response");
                APIResponse.put("did", sellerDidIpfsHash);
                APIResponse.put("tid", tid);
                APIResponse.put("rbtTid", rbtTxnId);
                APIResponse.put("status", "Success");
                APIResponse.put("comment", comment);
                APIResponse.put("message", "NFT Transaction Successful");
                nftSellerLogger.info("NFT Transaction Successful");
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                output.close();
                input.close();

                sk.close();
                ss.close();
                return APIResponse.toString();

            }
            APIResponse.put("did", buyerDidIpfsHash);
            APIResponse.put("tid", "null");
            APIResponse.put("status", "Failed");
            APIResponse.put("message", "Consensus failed at Sender side");
            nftSellerLogger.info(" Transaction failed");
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
            output.close();
            input.close();

            sk.close();
            ss.close();
            return APIResponse.toString();

        } catch (Exception e) {
            IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
            nftSellerLogger.error("Exception Occurred", e);
            return APIResponse.toString();
        } finally {
            try {
                ss.close();
                sk.close();
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
            } catch (Exception e) {
                IPFSNetwork.executeIPFSCommands(" ipfs p2p close -t /p2p/" + buyerPeerID);
                nftSellerLogger.error("Exception Occurred", e);
            }

        }
    }
}
