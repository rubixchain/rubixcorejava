package com.rubix.Resources;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multiaddr.MultiAddress;
import io.ipfs.multihash.Multihash;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.rubix.Constants.IPFSConstants.*;
import static com.rubix.Resources.Functions.getOsName;



public class IPFSNetwork {

    /**
     * This method create libp2p service and forward connections made to target-address.
     * target-address here is /ip4/127.0.0.1/tcp/port
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-p2p-listen"> ipfs p2p listen </a> for more
     * @param application specifies the libp2p handler name , It must be prefixed with '/x/'.
     * @param port port where the connections are forwarded
     * @throws IOException handles IO Exception
     * @throws InterruptedException handles Interrupted Exception
     */

    // change listen command

    public static void listen(String application, int port,String username) throws IOException, InterruptedException {
        String IPFSListen = "IPFS_PATH=~/.ipfs"+username+ " ipfs p2p listen /x/" + application + "/1.0 /ip4/127.0.0.1/tcp/" + port;
        executeIPFSCommands(IPFSListen);
    }


    /**
     *This method forward connections made to listen-address to target-address
     * @param application specifies the libp2p protocol name to use for libp2p
     * connections and/or handlers. It must be prefixed with '/x/'.
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-p2p-forward"> ipfs p2p forward </a> for more
     * @param port port where the connections are forwarded
     * @param peerid ipfs peerid for the target address
     * @throws IOException handling IO Exception
     * @throws InterruptedException handling Interrupted Exception
     */

    public static void forward(String application, int port, String peerid,String username) throws IOException, InterruptedException {
        String IPFSForward = "IPFS_PATH=~/.ipfs"+username+" ipfs p2p forward /x/" + application + "/1.0 /ip4/127.0.0.1/tcp/" + port + " /ipfs/" + peerid;
        executeIPFSCommands(IPFSForward);
    }


    /**
     * This method opens a new direct connection to a peer address.
     * The address format is an IPFS multiaddr.
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-swarm-connect"> ipfs swarm connect </a> for more
     * @param peerid is the multiaddr of the node
     * @throws IOException handling IO Exception
     */


    public static void swarmConnect(String peerid,IPFS ipfs) throws IOException {
        Random ran = new Random();
        List bootslist = ipfs.bootstrap.list();
        Collections.shuffle(bootslist);
        ran.setSeed(123456);
        int j = ran.nextInt(bootslist.size());
        String bootsnode = String.valueOf(bootslist.get(j));
        bootsnode = bootsnode.substring(bootsnode.length() - 46);
        MultiAddress multiAddress = new MultiAddress("/ipfs/" + bootsnode + "/p2p-circuit/ipfs/" + peerid);
        ipfs.swarm.connect(multiAddress);
    }


    /**
     * This method adds contents of path to ipfs. Use -r to add directories.
     * Note that directories are added recursively, to form the ipfs MerkleDAG
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-add"> ipfs add </a> for more
     * @param fileName is the the path to a file to be added to ipfs
     * @return String ipfs hash of the file added
     * @throws IOException handling IO Exception
     */


    public static String add(String fileName,IPFS ipfs) throws IOException {
        long st1 = System.currentTimeMillis();
        NamedStreamable file = new NamedStreamable.FileWrapper(new File(fileName));
        MerkleNode response = ipfs.add(file).get(0);
        long et1 = System.currentTimeMillis();
        System.out.println("add buffer:" + (et1 - st1));
        return response.hash.toBase58();
    }

    /**
     * This method pin objects to local storage
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-pin-add"> ipfs pin add</a> for more
     * @param MultiHash ipfspath of object to be pinned
     * @throws IOException handling IO Exception
     */


    public static void pin(String MultiHash,IPFS ipfs) throws IOException {
        long st1 = System.currentTimeMillis();
        Multihash filePointer = Multihash.fromBase58(MultiHash);
        List<Multihash> fileContents = ipfs.pin.add(filePointer);
        long et1 = System.currentTimeMillis();
        if (fileContents == null)
            return;
        System.out.println("Pin buffer:" + (et1 - st1));
    }

    /**
     * This methodd removes pinned objects from local storage
     * ee <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-pin-rm"> ipfs pin rm </a> for more
     * @param MultiHash ipfspath of object to be pinned
     * @throws IOException handling IO Exception
     */

    public static void unpin(String MultiHash,IPFS ipfs) throws IOException {
        long st1 = System.currentTimeMillis();
        Multihash filePointer = Multihash.fromBase58(MultiHash);
        List<Multihash> fileContents = ipfs.pin.rm(filePointer, true);
        long et1 = System.currentTimeMillis();
        if (fileContents == null)
            return;
        System.out.println("UnPin buffer:" + (et1 - st1));
    }


    /**
     * This method stores to disk the data contained an IPFS or IPNS object(s) at the given path.
     * By default, the output will be stored at './ipfs-path'. The content is returned
     * as string.
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-get"> ipfs get </a> for more
     * @param MultiHash The path to the IPFS object(s) to be outputted
     * @return the contents inside the file in string format
     * @throws IOException handles IO Exception
     */

    public static String get(String MultiHash,IPFS ipfs) throws IOException {
        long st1 = System.currentTimeMillis();
        Multihash filePointer = Multihash.fromBase58(MultiHash);
        byte[] fileContents = ipfs.cat(filePointer);
        String S = new String(fileContents);
        long et1 = System.currentTimeMillis();
        System.out.println("Get buffer:" + (et1 - st1));
        return S;
    }

    /**
     * This method is a plumbing command that will sweep the local
     * set of stored objects and remove ones that are not pinned in
     * order to reclaim hard disk space
     * See <a href="https://docs.ipfs.io/reference/api/cli/#ipfs-repo-gc"> ipfs repo gc </a> for more
     * @throws IOException handles IO Exception
     */

    public static void repo(IPFS ipfs) throws IOException {
        long st1 = System.currentTimeMillis();
        Object fileContents = ipfs.repo.gc();
        long et1 = System.currentTimeMillis();
        System.out.println("repo buffer:" + (et1 - st1));
        System.out.println(fileContents);

    }


    /**
     * This method perform ipfs CLI command and returns the CLI output
     * @param command CLI command to be executed
     * @return CLI output of the command
     * @throws IOException handles IO Exception
     * @throws InterruptedException handles Interrupted Exception
     */

    public static String executeIPFSCommands(String command) throws IOException, InterruptedException {

        String OS = getOsName();
        {
            if (command.contains(daemonipfs)) {
                String[] args = new String[] {"/bin/bash", "-c", command};
                Process P = Runtime.getRuntime().exec(args);
                Thread.sleep(7000);
                if (!OS.contains("Windows"))
                    P.waitFor();
                P.destroy();
            }
        }
        if (command.contains(listenipfs) || command.contains(forwardipfs) || command.contains(p2pipfs) || command.contains(shutipfs)) {
            StringBuilder sb = new StringBuilder();
            String line;
            String[] args = new String[] {"/bin/bash", "-c", command};
            Process P = Runtime.getRuntime().exec(args);
            BufferedReader br = new BufferedReader(new InputStreamReader(P.getInputStream()));
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
            if (!OS.contains("Windows"))
                P.waitFor();

            br.close();
            P.destroy();
            return sb.toString();
        }
        return null;
    }
}





