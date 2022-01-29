//package com.rubix.Gossip;
//
//import com.rubix.Resources.Functions;
//import io.ipfs.api.IPFS;
//import org.json.JSONArray;
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import java.io.IOException;
//import java.io.ObjectInputStream;
//import java.io.ObjectOutputStream;
//import java.net.Socket;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.Random;
//
//import static com.rubix.Constants.Ports.getDataPort;
//import static com.rubix.Resources.Functions.*;
//import static com.rubix.Resources.Functions.pathSet;
//import static com.rubix.Resources.IPFSNetwork.*;
//
//
//
//public class GetData implements Runnable {
//
//    String username;
//
//    public GetData(String username){
//        this.username=username;
//    }
//
//    private static List<String> offlinePeers = new ArrayList<>();
//    private static List broadCastList = new ArrayList<String>();
//
//    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
//
//    /**
//     * This method broadcasts the node's own data to the peers currently live in the network, only new nodes invoke this method
//     * @param status New node data to be broadcasted
//     * @throws IOException handles IOException
//     * @throws InterruptedException handles InterruptedException
//     */
//    public static void broadCast(JSONObject status,String username) throws IOException, InterruptedException, JSONException {
//        pathSet(username);
//        System.out.println("[com.rubix.Gossip] -------------------------Broadcasting---------------------------------");
//        String peerID, myPeerID;
//        status.remove("status");
//        for (Object o : broadCastList) {
//            peerID = (String) o;
//
//            myPeerID = peerID.concat("com/rubix/Gossip");
//
//            swarmConnect(peerID,ipfs);
//
//            forward(myPeerID, getDataPort, peerID,username);
//            Socket socket = new Socket("127.0.0.1", getDataPort);
//            ObjectOutputStream objectOutput = new ObjectOutputStream(socket.getOutputStream());
//            objectOutput.writeObject("Broadcast");
//            JSONArray myData = new JSONArray();
//            myData.put(status);
//            objectOutput.writeObject(myData.toString());
//            objectOutput.close();
//            socket.close();
//            System.out.println(executeIPFSCommands("IPFS_PATH=~/.ipfs"+username+"ipfs p2p  close -t /ipfs/" + peerID));
//        }
//    }
//
//    /**
//     * This method sends all the data that is in it's own dataTable to another so that the other node can check what all data it has
//     * which this node doesn't have and sends that data back as a response to this node, which then adds that data to its own table, in
//     * case the other node doesn't respond it selects another new user to communicate with.This method also checks if the node is a
//     * new node and invokes broadCast() in case this is a new node
//     * @param peerID is the of peerID of node to whom communication is to be initiated
//     * @throws IOException handles IOException
//     * @throws InterruptedException handles InterruptedException
//     * @throws ClassNotFoundException handles ClassNotFoundException
//     * @throws JSONException handles JSONException
//     */
//    public static void communicate(String peerID,String username) throws IOException, InterruptedException, ClassNotFoundException, JSONException {
//        pathSet(username);
//        String dataTableFromFile, status, myPeerID;
//        JSONArray dataTable;
//        JSONObject stat;
//        ArrayList existingData = new ArrayList<String>();
//        myPeerID = peerID.concat("com/rubix/Gossip");
//        swarmConnect(peerID,ipfs);
//
//        forward(myPeerID, getDataPort, peerID,username);
//        Socket socket = new Socket("127.0.0.1", getDataPort);
//        try {
//            ObjectOutputStream objectOutput = new ObjectOutputStream(socket.getOutputStream());
//
//            dataTableFromFile = Functions.readFile(DATA_PATH + "DataTable.json");
//
//            objectOutput.writeObject("Request");
//
//            if (dataTableFromFile.equals("")) {
//                objectOutput.writeObject("");
//            } else {
//                dataTable = new JSONArray(dataTableFromFile);
//                for (int i = 0; i < dataTable.length(); i++)
//                    existingData.add(dataTable.getJSONObject(i).get("peer-id"));
//
//                objectOutput.writeObject(existingData);
//            }
//            ObjectInputStream objectInput = new ObjectInputStream(socket.getInputStream());
//            String gossipedData = (String) objectInput.readObject();
//            objectOutput.close();
//            objectInput.close();
//            socket.close();
//            if (gossipedData == null) {
//                System.out.println("[GetData] response: " + null);
//                offlinePeers.add(peerID);
//                System.out.println("[GetData] "+executeIPFSCommands("IPFS_PATH=~/.ipfs"+username+"ipfs p2p  close -t /ipfs/" + peerID));
//                selectReceiver(username);
//            } else {
//                if ((gossipedData.equals("NA")))
//                    System.out.println("[GetData] No Data to Sync");
//                else
//                    Functions.updateJSON("add", DATA_PATH + "DataTable.json",gossipedData);
//
//            }
//
//            System.out.println("[GetData] response: " + gossipedData);
//
//        } catch (java.net.SocketException e) {
//            System.out.println("[GetData] SocketException caught ");
//            System.out.println("[GetData] This node is not running gossip -> " + peerID);
//            offlinePeers.add(peerID);
//            System.out.println("[GetData] "+executeIPFSCommands("IPFS_PATH=~/.ipfs"+username+"ipfs p2p close -t /ipfs/" + peerID));
//            selectReceiver(username);
//        }
//
//        System.out.println(executeIPFSCommands("[GetData] "+"IPFS_PATH=~/.ipfs"+username+"ipfs p2p close -t /ipfs/" + peerID));
//        status = Functions.readFile(DATA_PATH + "Status.json");
//        stat = new JSONObject(status);
//        if (stat.getString("status").equals("true")) {
//            stat.put("status", "false");
//            Functions.writeToFile(DATA_PATH + "Status.json", stat.toString(),true);
//            broadCast(stat,username);
//        }
//
//    }
//
//    /**
//     * This method selects a node with whom it should perform communication via communicate()
//     * @throws IOException handles IOException
//     * @throws InterruptedException handles InterruptedException
//     * @throws ClassNotFoundException handles ClassNotFoundException
//     * @throws JSONException handles JSONException
//     */
//    public static void selectReceiver(String username) throws IOException, InterruptedException, ClassNotFoundException, JSONException {
//        int j;
//        String peerID;
//        Random random = new Random();
//        random.setSeed(123456);
//        List swarmList = ipfs.swarm.peers();
//        broadCastList = swarmList;
//
//        if (swarmList.size() > 0) {
//            Collections.shuffle(swarmList);
//            j = random.nextInt(swarmList.size());
//
//            peerID = String.valueOf(swarmList.get(j)).substring(0, 46);
//
//            while (offlinePeers.contains(peerID)) {
//                broadCastList.remove(String.valueOf(swarmList.get(j)));
//                Collections.shuffle(swarmList);
//                j = random.nextInt(swarmList.size());
//                peerID = String.valueOf(swarmList.get(j)).substring(0, 46);
//            }
//
//            System.out.println("[GetData] Gossiping to " + peerID);
//            communicate(peerID,username);
//        } else
//            System.out.println("[GetData] No nodes available");
//
//    }
//
//    /**
//     * The Thread's run method which invokes selectReciever()
//     */
//    @Override
//    public void run() {
//        try {
//            selectReceiver(username);
//        } catch (IOException | ClassNotFoundException | JSONException | InterruptedException | NullPointerException | ArrayIndexOutOfBoundsException e) {
//            e.printStackTrace();
//        }
//    }
//}