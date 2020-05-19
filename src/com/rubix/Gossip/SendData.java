package com.rubix.Gossip;

import com.rubix.Resources.Functions;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;


import static com.rubix.Constants.Ports.*;
import static com.rubix.Resources.Functions.getPeerID;
import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.listen;

public class SendData implements Runnable {

    String username;

    public SendData(String username){
        this.username=username;
    }



    /**
     * This thread responds to requests from other nodes, that is in case a node wants to request dataTable info from this node, this
     * method will handle that request and return back the data which the requesting node doesn't have. This method also accepts the
     * broadcasted data from new node and adds that it's own dataTable
     */
    @Override
    public void run() {
        try {
            pathSet(username);
        } catch (IOException | JSONException | InterruptedException e) {
            e.printStackTrace();
        }
        int i;
        boolean exists;
        String readServer, dataTableFromFile, peerID, myPeerID;
        JSONArray dataReceived, dataTable, dataToBeSent;
        ArrayList recievedKnown;
        try {
            myPeerID = getPeerID(DATA_PATH + "did.json");
            myPeerID = myPeerID.concat("com/rubix/Gossip");

            listen(myPeerID, sendDataPort,username);

            while (true) {
                System.out.println("[SendData] ================================DATA SENDER CODE=====================================");
                Socket socket;
                ServerSocket serverSocket = new ServerSocket(sendDataPort);
                System.out.println("[SendData] Listening...");
                dataTableFromFile = Functions.readFile(DATA_PATH + "DataTable.json");
                dataTable = new JSONArray(dataTableFromFile);
                dataToBeSent = new JSONArray();
                socket = serverSocket.accept();
                System.out.println("[SendData] Accepted");

                ObjectOutputStream objectOutput = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream objectInput = new ObjectInputStream(socket.getInputStream());
                readServer = (String) objectInput.readObject();

                if (readServer.equals("Request")) {
                    recievedKnown = (ArrayList) objectInput.readObject();
                    for (i = 0; i < dataTable.length(); i++) {
                        exists = false;
                        peerID = dataTable.getJSONObject(i).getString("peer-id");
                        for (Object o : recievedKnown) {
                            if (o.equals(peerID)) {
                                exists = true;
                                break;
                            }
                        }

                        if (!exists)
                            dataToBeSent.put(dataTable.getJSONObject(i));
                    }
                    if (!(dataToBeSent.length() == 0))
                        objectOutput.writeObject(dataToBeSent.toString());
                    else
                        objectOutput.writeObject("NA");

                }
                if(readServer.equals("Broadcast")) {
                    String received;
                    exists = false;
                    received = (String) objectInput.readObject();

                    dataReceived = new JSONArray(received);
                    for (i = 0; i < dataTable.length(); i++) {
                        if(dataTable.getJSONObject(i).getString("peer-id").equals(dataReceived.getJSONObject(0).getString("peer-id"))){
                            exists = true;
                            break;
                        }
                    }
                    if(!exists) {
                    }
                    else{
                        dataTable.remove(i);
                    }
                    Functions.updateJSON("add", DATA_PATH +"DataTable.json",dataReceived.toString());
                }
                objectInput.close();
                objectOutput.close();
                socket.close();
                serverSocket.close();
            }
        } catch (IOException | ClassNotFoundException | JSONException | InterruptedException | NullPointerException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        }
    }
}