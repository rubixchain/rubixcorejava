package com.rubix.Consensus;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Constants.Ports;
import com.rubix.Resources.Functions;
import io.ipfs.api.IPFS;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;

public class QuorumConsensus implements Runnable {


    /**
     * This method is used to run a thread for Quorum Members
     * <p>This involves <ol> <li>Verify sender signature</li>
     * <li>Signing the transaction</li>
     * <li>Receiving share from sender</li></ol>
     */

    String threadNum;
    int port;
    String username;
    IPFS ipfs;

    // pass username also

    public QuorumConsensus(String username, String threadNum, int port, IPFS ipfs){
        this.username=username;
        this.threadNum = threadNum;
        this.port=port;
        this.ipfs=ipfs;
    }


    @Override
    public void run() {
        while (true) {
            try {

                //pathset based on username


                Functions.pathSet(username);
            } catch (IOException | InterruptedException | JSONException e) {
                e.printStackTrace();
            }
            String pvt = Functions.SHARES_PATH + "PvtShares.json";
            String temp, peerID, transactionID, verifySenderHash, receiverPeerID, appName, senderPrivatePos, senderPeerID;

            try {

                peerID = getPeerID(DATA_PATH + "did.json");
                appName = peerID.concat("consensus");
//                System.out.println("[QuorumConsensus] Quorum WID: " + getValues(JSON_PATH + "did.json", "wid", "peer-id", peerID));
//                System.out.println("[QuorumConsensus] Quorum Peer ID: " + peerID);

                listen(appName+threadNum, port,username);


                System.out.println(username + "[QuorumConsensus] running"+threadNum+" Quorum Listening on " + port);
                ServerSocket serverSocket = new ServerSocket(port);
                Socket socket = serverSocket.accept();
                System.out.println("[QuorumConsensus] Accepted");
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintStream out = new PrintStream(socket.getOutputStream());

                JSONObject readSenderData;
                String getData;
                while ((getData = in.readLine()) == null) {}
                System.out.println("[QuorumConsensus] Got details from initiator: " + getData);
                readSenderData = new JSONObject(getData);
                senderPrivatePos = readSenderData.getString("sign");
                senderPeerID = readSenderData.getString("senderPID");
                transactionID = readSenderData.getString("Tid");
                verifySenderHash = readSenderData.getString("Hash");
                receiverPeerID = readSenderData.getString("RID");

                String quorumHash = calculateHash(verifySenderHash.concat(receiverPeerID),"SHA3-256");
                System.out.println("[QuorumConsensus] "+"Hash to verify Quorum: " + quorumHash);

                String walletID = getValues(DATA_PATH + "DataTable.json", "wid", "peer-id", senderPeerID);
                String decentralisedID = getValues(DATA_PATH + "DataTable.json", "did", "peer-id", senderPeerID);

                if (Authenticate.verifySignature(decentralisedID, walletID, verifySenderHash, senderPrivatePos)) {
                    String QuorumSignature = getSignFromShares(pvt, quorumHash);
//                    System.out.println("[QuorumConsensus] "+"Quorum Signature: " + QuorumSignature);
                    out.println(QuorumSignature);

                    String share;
                    while ((share = in.readLine())==null){ }
                    System.out.println("[QuorumConsensus] "+"Response: "+share);
                    if (!share.equals("null")) { //commented as per test for multiple consensus threads

//                        FileWriter shareWriter = new FileWriter(new File("MyShare.txt"), true);
//                        shareWriter.write(share);
//                        shareWriter.close();
//                        File readShare = new File("MyShare.txt");
//                        String shareHash = add(readShare.toString());
//                        JSONObject storeDetailsQuo = new JSONObject();
//                        storeDetailsQuo.put("tid", transactionID);
//                        storeDetailsQuo.put("sign", QuorumSignature);
//                        storeDetailsQuo.put("Share", shareHash);
//                        JSONArray data = new JSONArray();
//                        data.put(storeDetailsQuo);
//                        updateJSON("add",JSON_PATH + "QuorumSignedTransactions.json", data.toString());
//                        deleteFile("MyShare.txt");
                    }

                } else {
                    System.out.println("[QuorumConsensus] "+"Sender Authentication Failed w.r.t Quorum Members");
                    out.println("Auth_Failed");
                    System.exit(0);
                }
                socket.close();
                serverSocket.close();
            } catch (IOException | JSONException | NoSuchAlgorithmException | InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
