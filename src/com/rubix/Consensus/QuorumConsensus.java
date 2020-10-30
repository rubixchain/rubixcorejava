package com.rubix.Consensus;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;

public class QuorumConsensus implements Runnable {


    public static Logger QuorumConsensusLogger = Logger.getLogger(QuorumConsensus.class);


    /**
     * This method is used to run a thread for Quorum Members
     * <p>This involves <ol> <li>Verify sender signature</li>
     * <li>Signing the transaction</li>
     * <li>Receiving share from sender</li></ol>
     */


    int port;
    IPFS ipfs;

    // pass username also

    public QuorumConsensus(){
        this.port = QUORUM_PORT;
        this.ipfs=new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
    }


    @Override
    public void run() {
        while (true) {
            PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
            String temp, peerID, transactionID, verifySenderHash, receiverDID, appName, senderPrivatePos, senderDidIpfsHash="", senderPID = "";
            ServerSocket serverSocket = null;
            Socket socket = null;
            try {

                peerID = getPeerID(DATA_PATH + "DID.json");
                String didHash = getValues(DATA_PATH + "DataTable.json", "didHash", "peerid", peerID);
                appName = peerID.concat("consensus");

                listen(appName, port);

                QuorumConsensusLogger.debug("Quorum Listening on " + port);
                 serverSocket = new ServerSocket(port);
                 socket = serverSocket.accept();

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintStream out = new PrintStream(socket.getOutputStream());

                JSONObject readSenderData;
                String getData;
                getData = in.readLine();
                QuorumConsensusLogger.debug("Received Details from initiator: " + getData);
                readSenderData = new JSONObject(getData);
                senderPrivatePos = readSenderData.getString("sign");
                senderDidIpfsHash = readSenderData.getString("senderDID");
                transactionID = readSenderData.getString("Tid");
                verifySenderHash = readSenderData.getString("Hash");
                receiverDID = readSenderData.getString("RID");

                senderPID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", senderDidIpfsHash);
                String senderWidIpfsHash = getValues(DATA_PATH + "DataTable.json", "walletHash", "didHash", senderDidIpfsHash);

                nodeData(senderDidIpfsHash, senderWidIpfsHash, ipfs);
                String quorumHash = calculateHash(verifySenderHash.concat(receiverDID),"SHA3-256");

                JSONObject detailsToVerify = new JSONObject();
                detailsToVerify.put("did", senderDidIpfsHash);

                if (Authenticate.verifySignature(detailsToVerify.toString(), senderPrivatePos)) {
                    QuorumConsensusLogger.debug("Quorum Authenticated Sender");
                    String QuorumSignature = getSignFromShares(DATA_PATH + didHash + "/PrivateShare.png", quorumHash);
                    out.println(QuorumSignature);
                    String share;
                    share = in.readLine();
                    if (!share.equals("null")) { //commented as per test for multiple consensus threads
                        FileWriter shareWriter = new FileWriter(new File("MyShare.txt"), true);
                        shareWriter.write(share);
                        shareWriter.close();
                        File readShare = new File("MyShare.txt");
                        String shareHash = add(readShare.toString(), ipfs);
                        JSONObject storeDetailsQuorum = new JSONObject();
                        storeDetailsQuorum.put("tid", transactionID);
                        storeDetailsQuorum.put("sign", QuorumSignature);
                        storeDetailsQuorum.put("Share", shareHash);
                        JSONArray data = new JSONArray();
                        data.put(storeDetailsQuorum);
                        QuorumConsensusLogger.debug("Quorum Share: " + share);
                        updateJSON("add",WALLET_DATA_PATH + "QuorumSignedTransactions.json", data.toString());
                        deleteFile("MyShare.txt");
                    }
                } else {
                    QuorumConsensusLogger.debug("Sender Authentication Failure - Quorum");
                    out.println("Auth_Failed");
                }
            } catch (IOException e) {
                executeIPFSCommands(" ipfs p2p close -t /ipfs/" + senderPID);
                QuorumConsensusLogger.error("IOException Occurred", e);
                e.printStackTrace();
            } catch (JSONException e) {
                executeIPFSCommands(" ipfs p2p close -t /ipfs/" + senderPID);
                QuorumConsensusLogger.error("JSONException Occurred", e);
                e.printStackTrace();
            }
            finally {
                try {
                    executeIPFSCommands(" ipfs p2p close -t /ipfs/" + senderPID);
                    socket.close();
                    serverSocket.close();
                } catch (IOException e) {
                    executeIPFSCommands(" ipfs p2p close -t /ipfs/" + senderPID);
                    QuorumConsensusLogger.error("IOException Occurred", e);
                    e.printStackTrace();
                }

            }
        }

    }
}
