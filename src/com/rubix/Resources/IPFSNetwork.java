package com.rubix.Resources;


import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multiaddr.MultiAddress;
import io.ipfs.multihash.Multihash;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.json.JsonArray;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.rubix.Constants.IPFSConstants.*;
import static com.rubix.Resources.Functions.*;


public class IPFSNetwork {
    public static int swarmAttempt = 0;

    /**
     * This method create libp2p service and forward connections made to target-address.
     * target-address here is /ip4/127.0.0.1/tcp/port
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-p2p-listen"> ipfs p2p listen </a> for more
     */


    public static Logger IPFSNetworkLogger = Logger.getLogger(IPFSNetwork.class);

    public static void listen(String application, int port) {
        String IPFSListen = " ipfs p2p listen /x/" + application + "/1.0 /ip4/127.0.0.1/tcp/" + port;
        executeIPFSCommands(IPFSListen);
    }

    /**
     * This method forward connections made to listen-address to target-address
     *
     * @param application specifies the libp2p protocol name to use for libp2p
     *                    connections and/or handlers. It must be prefixed with '/x/'.
     *                    See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-p2p-forward"> ipfs p2p forward </a> for more
     * @param port        port where the connections are forwarded
     * @param peerid      ipfs peerid for the target address
     */

    public static void forward(String application, int port, String peerid) {

        String IPFSForward = " ipfs p2p forward /x/" + application + "/1.0 /ip4/127.0.0.1/tcp/" + port + " /p2p/" + peerid;
        executeIPFSCommands(IPFSForward);
    }


    public static String checkSwarmConnect()
    {
        IPFSNetworkLogger.debug("check swarm peers request");
        String response =  executeIPFSCommandsResponse("ipfs swarm peers");
        IPFSNetworkLogger.debug(response + "is the response ");
        return  response;
    }


    public static void swarmConnector(String peerid, IPFS ipfs) throws JSONException{
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String bootNode;
        int j;

        try {
            if (!checkSwarmConnect().contains(peerid)) {
                Random ran = new Random();

                List bootStrapList = ipfs.bootstrap.list();
                Collections.shuffle(bootStrapList);
                ran.setSeed(123456);
                int bootstrapSize = bootStrapList.size();

                j = ran.nextInt(bootstrapSize);
                bootNode = String.valueOf(bootStrapList.get(j));
                bootNode = bootNode.substring(bootNode.length() - 46);
                IPFSNetworkLogger.debug(bootNode);
                while (!checkSwarmConnect().contains(bootNode)) {
                    j = (j + 1) % bootstrapSize;
                    bootNode = String.valueOf(bootStrapList.get(j));
                    bootNode = bootNode.substring(bootNode.length() - 46);
                    IPFSNetworkLogger.debug("trying to connect: " + bootNode);
                }
                MultiAddress multiAddress = new MultiAddress("/p2p/" + bootNode + "/p2p-circuit/p2p/" + peerid);
                String output = swarmConnectProcess(multiAddress);
                if (!output.contains("success"))
                    swarmConnect(peerid, ipfs);
                else
                    IPFSNetworkLogger.debug("Connected via bootstrap node: " + bootNode);
            } else {
                IPFSNetworkLogger.debug("Connecting to Receiver directly");

            }
        } catch (IOException e) {
            IPFSNetworkLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }

    }


    /**
     * This method opens a new direct connection to a peer address.
     * The address format is an IPFS multiaddr.
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-swarm-connect"> ipfs swarm connect </a> for more
     *
     * @param peerid is the multiaddr of the node
     * @param ipfs   IPFS instance
     */



    public static void swarmConnect(String peerid, IPFS ipfs) throws JSONException{
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String bootNode;
        int j;

        IPFSNetworkLogger.debug("at  swarmconnect " + peerid);
        if (!checkSwarmConnect().contains(peerid)) {
            Random ran = new Random();

//                List bootStrapList = ipfs.bootstrap.list();
//                Collections.shuffle(bootStrapList);
            ran.setSeed(123456);
            int bootstrapSize = BOOTSTRAPS.length();

            IPFSNetworkLogger.debug("Bootstraps  " + BOOTSTRAPS + "size " + bootstrapSize);

            j = ran.nextInt(bootstrapSize);
            bootNode = String.valueOf(BOOTSTRAPS.get(j));
            bootNode = bootNode.substring(bootNode.length() - 46);
            IPFSNetworkLogger.debug("bootnode is " + bootNode);
            IPFSNetworkLogger.debug(bootNode);
            while (!checkSwarmConnect().contains(bootNode)) {
                j = (j + 1) % bootstrapSize;
                bootNode = String.valueOf(BOOTSTRAPS.get(j));
                bootNode = bootNode.substring(bootNode.length() - 46);
                IPFSNetworkLogger.debug("trying to connect: " + bootNode);
            }
            MultiAddress multiAddress = new MultiAddress("/ipfs/" + bootNode + "/p2p-circuit/ipfs/" + peerid);
            String output = swarmConnectProcess(multiAddress);
            if (!output.contains("success"))
            {
                if (swarmAttempt < 25) {
                    IPFSNetworkLogger.debug("swarm attempt round " + swarmAttempt);
                    swarmAttempt++;
                    swarmConnect(peerid, ipfs);
                }
                else {
                    IPFSNetworkLogger.debug("swarm attempt failed");
                    swarmAttempt=0;
                }
        }
            else {
                IPFSNetworkLogger.debug("Connected via bootstrap node: " + bootNode);
                swarmAttempt=0;
            }
        } else {
            IPFSNetworkLogger.debug("Connecting to Receiver directly");
            swarmAttempt=0;

        }

    }


    /**
     * This function connects the peer node through the private swarm
     *
     * @param multiAddress Peer Identity of the node
     * @return Connection status
     */
    public static String swarmConnectProcess(MultiAddress multiAddress) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String OS = getOsName();
        String command = "ipfs swarm connect " + multiAddress;
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            Process P = Runtime.getRuntime().exec(command);
            BufferedReader br = new BufferedReader(new InputStreamReader(P.getInputStream()));
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
            IPFSNetworkLogger.debug(command + " output: " + sb.toString());
            if (!OS.contains("Windows"))
                P.waitFor();
            br.close();
            P.destroy();
        } catch (IOException e) {
            IPFSNetworkLogger.error("IOException Occurred", e);
            e.printStackTrace();
        } catch (InterruptedException e) {
            IPFSNetworkLogger.error("Interrupted Exception Occurred", e);
            e.printStackTrace();
        }
        return sb.toString();
    }

    /**
     * This method adds contents of path to ipfs. Use -r to add directories.
     * Note that directories are added recursively, to form the ipfs MerkleDAG
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-add"> ipfs add </a> for more
     *
     * @param fileName is the the path to a file to be added to ipfs
     * @param ipfs     IPFS instance
     * @return String ipfs hash of the file added
     */


    public static String add(String fileName, IPFS ipfs) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        NamedStreamable file = new NamedStreamable.FileWrapper(new File(fileName));
        MerkleNode response = null;
        try {
            response = ipfs.add(file).get(0);
        } catch (IOException e) {

            IPFSNetworkLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }

        if (response != null)
            return response.hash.toBase58();
        else
            return null;
    }




    public static String addHashOnly(String fileName, IPFS ipfs) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        NamedStreamable file = new NamedStreamable.FileWrapper(new File(fileName));
        MerkleNode response = null;
        try {
            //response = ipfs.add(file).get(0);
            response = ipfs.add(file,false,true).get(0);
        } catch (IOException e) {

            IPFSNetworkLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }

        if (response != null)
            return response.hash.toBase58();
        else
            return null;
    }


    /**
     * This method pin objects to local storage
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-pin-add"> ipfs pin add</a> for more
     *
     * @param MultiHash ipfspath of object to be pinned
     * @param ipfs      IPFS instance
     */


    public static void pin(String MultiHash, IPFS ipfs) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        Multihash filePointer = Multihash.fromBase58(MultiHash);
        List<Multihash> fileContents = null;
        try {
            fileContents = ipfs.pin.add(filePointer);
        } catch (IOException e) {

            IPFSNetworkLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }

        if (fileContents == null) {
        }
    }

    /**
     * This method removes pinned objects from local storage
     * ee <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-pin-rm"> ipfs pin rm </a> for more
     *
     * @param MultiHash ipfspath of object to be pinned
     * @param ipfs      IPFS instance
     */

    public static void unpin(String MultiHash, IPFS ipfs) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        Multihash filePointer = Multihash.fromBase58(MultiHash);
        List<Multihash> fileContents = null;
        try {
            fileContents = ipfs.pin.rm(filePointer, true);
        } catch (IOException e) {

            IPFSNetworkLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }
        if (fileContents == null) {
        }
    }


    /**
     * This method stores to disk the data contained an IPFS or IPNS object(s) at the given path.
     * By default, the output will be stored at './ipfs-path'. The content is returned
     * as string.
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-get"> ipfs get </a> for more
     *
     * @param MultiHash The path to the IPFS object(s) to be outputted
     * @param ipfs      IPFS instance
     * @return the contents inside the file in string format
     */

    public static String get(String MultiHash, IPFS ipfs) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        Multihash filePointer = Multihash.fromBase58(MultiHash);
        byte[] fileContents = new byte[0];
        try {
            fileContents = ipfs.cat(filePointer);
        } catch (IOException e) {
            IPFSNetworkLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }
        return new String(fileContents);
    }

    public static boolean dhtFindProvs(String MultiHash,String previousOwner, IPFS ipfs) throws IOException{
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        Multihash dhtMultihash = Multihash.fromBase58(MultiHash);
        List dhtlist = ipfs.dht.findprovs(dhtMultihash);
        IPFSNetworkLogger.debug("dht list " + dhtlist);
        if(dhtlist.size()==1&&dhtlist.toString().contains(previousOwner))
        return true;
        return false;
    }

    public static boolean dhtEmpty(String MultiHash, IPFS ipfs) throws IOException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        Multihash dhtMultihash = Multihash.fromBase58(MultiHash);
        List dhtlist = ipfs.dht.findprovs(dhtMultihash);
        if (dhtlist.toString().contains("Type=4"))
            return false;
        return true;
    }


    /**
     * IPFS get for images
     *
     * @param MultiHash IPFS hash of the image
     * @param ipfs      IPFS instance
     * @param path      Path to save the image
     * @throws IOException handles IO Exception
     */
    public static void getImage(String MultiHash, IPFS ipfs, String path) throws IOException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        Multihash filePointer = Multihash.fromBase58(MultiHash);
        byte[] fileContents = new byte[0];
        try {
            fileContents = ipfs.cat(filePointer);
        } catch (IOException e) {
            IPFSNetworkLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileContents);
        BufferedImage bis = ImageIO.read(byteArrayInputStream);
        ImageIO.write(bis, "png", new File(path));
    }


    /**
     * This method is a plumbing command that will sweep the local
     * set of stored objects and remove ones that are not pinned in
     * order to reclaim hard disk space
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-repo-gc"> ipfs repo gc </a> for more
     *
     * @param ipfs IPFS instance
     */

    public static void repo(IPFS ipfs) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        try {
            Object fileContents = ipfs.repo.gc();
        } catch (IOException e) {
            IPFSNetworkLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }
    }


    public static String executeIPFSCommandsResponse(String command) {
        IPFSNetworkLogger.debug("executeIPFSCommandsResponse for command "+ command);
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String OS = getOsName();
        String[] commands = new String[3];
        if(OS.contains("Mac") || OS.contains("Linux")){

            commands[0] = "bash";
            commands[1] = "-c";
            commands[2] = "export PATH=/usr/local/bin:$PATH &&" + command;
        }else if(OS.contains("Windows")){
            commands[0] = "cmd.exe";
            commands[1] = "/c";
            commands[2] = command;
        }
        ProcessBuilder p;

        try {
            Process process;
            if (command.contains(daemon)) {
                p = new ProcessBuilder(commands);
                process = p.start();
                Thread.sleep(7000);
                IPFSNetworkLogger.debug("Daemon is running");

            }

            if (command.contains(listen) || command.contains(forward) || command.contains("swarm") || command.contains(p2p) || command.contains(shutdown)) {
                IPFSNetworkLogger.debug("executing command " + command);
                p = new ProcessBuilder(commands);
                process = p.start();

                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line = null;
                while ( (line = reader.readLine()) != null) {
                    builder.append(line);
                    builder.append(System.getProperty("line.separator"));
                }
                String result = builder.toString();

                System.out.println("result "+result+ "for process "+ command);

                if(OS.contains("Mac") || OS.contains("Linux"))
                    process.waitFor();

                return result;
            }
            else {
                IPFSNetworkLogger.debug("unhandled command " + command);
                return "wrong command";
            }



        } catch (IOException e) {
            IPFSNetworkLogger.error("IOException Occurred", e);
            e.printStackTrace();
        } catch (InterruptedException e) {
            IPFSNetworkLogger.error("Interrupted Exception Occurred", e);
            e.printStackTrace();
        }
        IPFSNetworkLogger.debug("return string " );
        return "result";
    }



    /**
     * This method perform ipfs CLI command and returns the CLI output
     *
     * @param command CLI command to be executed
     */

    public static void executeIPFSCommands(String command) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String OS = getOsName();
        String[] commands = new String[3];
        if(OS.contains("Mac") || OS.contains("Linux")){

            commands[0] = "bash";
            commands[1] = "-c";
            commands[2] = "export PATH=/usr/local/bin:$PATH &&" + command;
        }else if(OS.contains("Windows")){
            commands[0] = "cmd.exe";
            commands[1] = "/c";
            commands[2] = command;
        }
        ProcessBuilder p;

        try {
            Process process;
            if (command.contains(daemon)) {
                p = new ProcessBuilder(commands);
                process = p.start();
                Thread.sleep(7000);
                IPFSNetworkLogger.debug("Daemon is running");

            }

            if (command.contains(listen) || command.contains(forward) || command.contains(p2p) || command.contains(shutdown)) {
                p = new ProcessBuilder(commands);
                process = p.start();

                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line = null;
                while ( (line = reader.readLine()) != null) {
                    builder.append(line);
                    builder.append(System.getProperty("line.separator"));
                }
                String result = builder.toString();

                System.out.println("result "+result+ "for process "+ command);

                if(OS.contains("Mac") || OS.contains("Linux"))
                    process.waitFor();
            }



        } catch (IOException e) {
            IPFSNetworkLogger.error("IOException Occurred", e);
            e.printStackTrace();
        } catch (InterruptedException e) {
            IPFSNetworkLogger.error("Interrupted Exception Occurred", e);
            e.printStackTrace();
        }
    }
}





