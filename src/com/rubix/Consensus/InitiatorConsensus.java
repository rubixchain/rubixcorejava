package com.rubix.Consensus;

import com.rubix.AuthenticateNode.Authenticate;
import com.rubix.Resources.IPFSNetwork;
import io.ipfs.api.IPFS;
import javafx.util.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

import static com.rubix.Resources.Functions.*;

import static com.rubix.Resources.IPFSNetwork.forward;
import static com.rubix.Resources.IPFSNetwork.swarmConnect;


public class InitiatorConsensus {


    private static Socket[] qSocket = new Socket[7];
    private static PrintStream[] qOut = new PrintStream[7];
    private static BufferedReader[] qIn = new BufferedReader[7];
    private static String[] qResponse = new String[7];
    static int quorumCount = 7;
    private static JSONObject quorumSignature = new JSONObject();

    private static final Object lock = new Object();
    private static String[] quorumID = new String[7];
    public static ArrayList<String> quorumWithShares = new ArrayList<>();
    private static volatile int quorumResponse = 0;


    /**
     * This method returns consensus status
     * @return consensus status
     */
    public static String consensusStatus() {
        if (quorumResponse >= 5) {
            return "com.rubix.Consensus Reached";
        } else
            return "com.rubix.Consensus Failed";
    }

    /**
     * This method increments the quorumResponse variable
     */
    private static synchronized void voteNCount() {
        synchronized (lock) {
            quorumResponse++;
            if (quorumResponse >= 5) {
                System.out.println("[QuorumConsensus] "+"com.rubix.Consensus Reached");
            }
        }
    }

    /**
     * This method runs the consensus
     * 1. Contact quorum with sender signatures and details
     * 2. Verify quorum signatures
     * 3. If consensus reached , sends shares to Quorum
     * @param hash Data to be signed on
     * @param details details to be shared with Quorum
     * @return quorum peerID , Signature pairs
     * @throws IOException handles IO Exception
     * @throws JSONException handles JSON Exception
     */
    public static JSONObject start(String appExt, String hash, JSONArray details,String username,ArrayList<String> quorumPEER,IPFS ipfs, int PORT) throws IOException, JSONException, InterruptedException {

        pathSet(username);

        JSONArray tokenDetails = new JSONArray(details.toString());
        JSONObject detailsToken = tokenDetails.getJSONObject(0);
        JSONObject sharesToken = tokenDetails.getJSONObject(1);

        String[] shares = new String[4];
        for(int i = 0; i < shares.length; i++){
            int p = i + 1;
            shares[i] = sharesToken.getString("Share" + p);
        }

        for (int j = 0; j < quorumPEER.size(); j++)
            quorumID[j] = quorumPEER.get(j);


        Thread[] quorumThreads = new Thread[quorumCount];
        for (int i = 0; i < quorumCount; i++) {
            int j = i;
            int k = i+1;
            quorumThreads[i] = new Thread(() -> {

                try {
                    //swarmConnect(quorumID[j],ipfs);
                    String appName = quorumID[j].concat("consensus");
                    appName = appName.concat(appExt);
                    System.out.println("[InitiatorConsensus]Connecting with " + quorumID[j] + "on AppName" + appName);
                    forward(appName, PORT+j, quorumID[j],username);
                    System.out.println("[InitiatorConsensus]Forwarded to " + quorumID[j]);
                    qSocket[j] = new Socket("127.0.0.1", PORT+j);
                    qIn[j] = new BufferedReader(new InputStreamReader(qSocket[j].getInputStream()));
                    qOut[j] = new PrintStream(qSocket[j].getOutputStream());
                    qOut[j].println(detailsToken);

                    while ((qResponse[j] = qIn[j].readLine()) == null) {
                    }
                    System.out.println("[InitiatorConsensus]Got Sign from  " + quorumID[j] + " " + qResponse[j]);

                    if (quorumResponse > 5) {
                        qOut[j].println("null");
                        IPFSNetwork.executeIPFSCommands("IPFS_PATH=~/.ipfs"+username+"ipfs p2p close -t /ipfs/"+ quorumID[j]);
                    }
                    else
                    {
                        String walletID = getValues(DATA_PATH + "DataTable.json", "wid", "peer-id", quorumID[j]);
                        String decentralisedID = getValues(DATA_PATH + "DataTable.json", "did", "peer-id", quorumID[j]);
//                        System.out.println("WID of " + quorumID[j] + " " + walletID);
//                        System.out.println("DID of " + quorumID[j] + " " + decentralisedID);
                        if (Authenticate.verifySignature(decentralisedID, walletID, hash, qResponse[j])) {
                            System.out.println("[InitiatorConsensus]Authenticated node " + quorumID[j]);
                            voteNCount();
                            if (quorumResponse < 5) {
                                String shareToQuorum;
                                shareToQuorum=shares[quorumResponse -1];
                                while (quorumResponse < 5) {
                                    Thread.onSpinWait();
                                }
                                quorumSignature.put("Q" + k, qResponse[j]);
                                quorumWithShares.add(quorumPEER.get(j));
                                qOut[j].println(shareToQuorum);
                                IPFSNetwork.executeIPFSCommands("IPFS_PATH=~/.ipfs"+username+"ipfs"+username+" ipfs p2p close -t "+ quorumID[j]);

                            } else if (quorumResponse == 5) {
                                quorumSignature.put("Q" + k, qResponse[j]);
                                qOut[j].println("null");
                                IPFSNetwork.executeIPFSCommands("IPFS_PATH=~/.ipfs"+username+"ipfs"+username+"ipfs p2p close -t /ipfs/"+ quorumID[j]);

                            } else {
                                qOut[j].println("null");
                                IPFSNetwork.executeIPFSCommands("IPFS_PATH=~/.ipfs"+username+"ipfs"+username+"ipfs p2p close -t /ipfs/"+ quorumID[j]);
                            }
                        }
                    }
                } catch (IOException | InterruptedException | JSONException e) {
                    e.printStackTrace();
                }
            });
            quorumThreads[j].start();
        }
        return quorumSignature;
    }

}
