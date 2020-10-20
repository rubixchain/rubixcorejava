package com.rubix.Resources;

import com.rubix.AuthenticateNode.PropImage;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import static com.rubix.Resources.IPFSNetwork.*;


public class Functions {

    public static boolean mutex = false;
    public static String DATA_PATH = "";
    public static String TOKENS_PATH = "";
    public static String TOKENCHAIN_PATH = "";
    public static String LOGGER_PATH = "";
    public static String WALLET_DATA_PATH = "";
    public static int RECEIVER_PORT, GOSSIP_SENDER, GOSSIP_RECEIVER, QUORUM_PORT, SENDER2Q1, SENDER2Q2, SENDER2Q3, SENDER2Q4, SENDER2Q5, SENDER2Q6, SENDER2Q7;
    public static int QUORUM_COUNT;
    public static int SEND_PORT;
    public static int IPFS_PORT;
    public static String SYNC_IP = "";
    public static int APPLICATION_PORT;
    public static String EXPLORER_IP = "";
    public static String USERDID_IP = "";
    public static String configPath = "";
    public static boolean CONSENSUS_STATUS;
    public static JSONObject QUORUM_MEMBERS;

    public static Logger FunctionsLogger = Logger.getLogger(Functions.class);

    /**
     * This method sets the required paths used in the functions
     */
    public static void pathSet() {
        String OSName = getOsName();
        if (OSName.contains("Windows"))
            configPath = "C:\\Rubix\\config.json";
        else if (OSName.contains("Mac"))
            configPath = "/Applications/Rubix/config.json";
        else if (OSName.contains("Linux"))
            configPath = "/home/" + getSystemUser() + "/Rubix/config.json/";
        else
            System.exit(0);

        String configFileContent = readFile(configPath);

        JSONArray pathsArray;
        try {
            pathsArray = new JSONArray(configFileContent);

            DATA_PATH = pathsArray.getJSONObject(0).getString("DATA_PATH");
            TOKENS_PATH = pathsArray.getJSONObject(0).getString("TOKENS_PATH");
            LOGGER_PATH = pathsArray.getJSONObject(0).getString("LOGGER_PATH");
            TOKENCHAIN_PATH = pathsArray.getJSONObject(0).getString("TOKENCHAIN_PATH");
            WALLET_DATA_PATH = pathsArray.getJSONObject(0).getString("WALLET_DATA_PATH");

            SEND_PORT = pathsArray.getJSONObject(1).getInt("SEND_PORT");
            RECEIVER_PORT = pathsArray.getJSONObject(1).getInt("RECEIVER_PORT");
            GOSSIP_RECEIVER = pathsArray.getJSONObject(1).getInt("GOSSIP_RECEIVER");
            GOSSIP_SENDER = pathsArray.getJSONObject(1).getInt("GOSSIP_SENDER");
            QUORUM_PORT = pathsArray.getJSONObject(1).getInt("QUORUM_PORT");
            SENDER2Q1 = pathsArray.getJSONObject(1).getInt("SENDER2Q1");
            SENDER2Q2 = pathsArray.getJSONObject(1).getInt("SENDER2Q2");
            SENDER2Q3 = pathsArray.getJSONObject(1).getInt("SENDER2Q3");
            SENDER2Q4 = pathsArray.getJSONObject(1).getInt("SENDER2Q4");
            SENDER2Q5 = pathsArray.getJSONObject(1).getInt("SENDER2Q5");
            SENDER2Q6 = pathsArray.getJSONObject(1).getInt("SENDER2Q6");
            SENDER2Q7 = pathsArray.getJSONObject(1).getInt("SENDER2Q7");
            IPFS_PORT = pathsArray.getJSONObject(1).getInt("IPFS_PORT");
            APPLICATION_PORT = pathsArray.getJSONObject(1).getInt("APPLICATION_PORT");

            SYNC_IP = pathsArray.getJSONObject(2).getString("SYNC_IP");
            EXPLORER_IP = pathsArray.getJSONObject(2).getString("EXPLORER_IP");
            USERDID_IP = pathsArray.getJSONObject(2).getString("USERDID_IP");

            CONSENSUS_STATUS = pathsArray.getJSONObject(3).getBoolean("CONSENSUS_STATUS");
            QUORUM_COUNT = pathsArray.getJSONObject(3).getInt("QUORUM_COUNT");

            QUORUM_MEMBERS = pathsArray.getJSONObject(4);


        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    /**
     * This method gets the currently logged in username
     *
     * @return lineID The current user
     */

    public static String getSystemUser() {
        Process processID;
        String lineID = "";
        try {
            processID = Runtime.getRuntime().exec("whoami");
            InputStreamReader inputStreamReader = new InputStreamReader(processID.getInputStream());
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            lineID = bufferedReader.readLine();
            processID.waitFor();
            inputStreamReader.close();
            bufferedReader.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return lineID;
    }


    /**
     * This method calculates different types of hashes as mentioned in the passed parameters for the mentioned message
     *
     * @param message   Input string to be hashed
     * @param algorithm Specification of the algorithm used for hashing
     * @return (String) hash
     */

    public static String calculateHash(String message, String algorithm) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            FunctionsLogger.error("Invalid Cryptographic Algorithm", e);
            e.printStackTrace();
        }
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] c = new byte[messageBytes.length];
        System.arraycopy(messageBytes, 0, c, 0, messageBytes.length);
        final byte[] hashBytes = digest.digest(messageBytes);
        return bytesToHex(hashBytes);
    }

    /**
     * This method Converts the string passed to it into an integer array
     *
     * @param inputString Input string to be converted to integer array
     * @return (Integer Array) outputArray
     */
    public static int[] strToIntArray(String inputString) {
        int[] outputArray = new int[inputString.length()];
        for (int k = 0; k < inputString.length(); k++) {
            if (inputString.charAt(k) == '0')
                outputArray[k] = 0;
            else
                outputArray[k] = 1;
        }
        return outputArray;
    }

    /**
     * This method Converts the integer array passed to it into String
     *
     * @param inputArray Input integer array to be converted to String
     * @return (String) outputString
     */
    public static String intArrayToStr(int[] inputArray) {
        StringBuilder outputString = new StringBuilder();
        for (int i : inputArray) {
            if (i == 1)
                outputString.append("1");
            else
                outputString.append("0");
        }
        return outputString.toString();
    }

    /**
     * This method converts the passed byte array into Hexadecimal String (hex)
     *
     * @param inputHash Byte Array to be concerted into hexadecimal string
     * @return outputHexString
     */
    public static String bytesToHex(byte[] inputHash) {
        StringBuilder outputHexString = new StringBuilder();
        for (byte b : inputHash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) outputHexString.append('0');
            outputHexString.append(hex);
        }
        return outputHexString.toString();
    }


    /**
     * This method returns the content of the file passed to it
     *
     * @param filePath Location of the file to be read
     * @return File Content as string
     */
    public static String readFile(String filePath) {
        FileReader fileReader;
        StringBuilder fileContent = new StringBuilder();
        try {
            fileReader = new FileReader(filePath);
            int i;
            while ((i = fileReader.read()) != -1)
                fileContent.append((char) i);
            fileReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileContent.toString();
    }

    /**
     * This function gets the shares images from IPFS with DID as parameter
     * @param DID Decentralized Identity
     * @param WID Wallet Share
     * @param ipfs IPFS instance
     * @param dataFile DataTable file to get data from
     * @throws IOException handles IO Exception
     */
    public static void getSharesImages(String DID, String WID, IPFS ipfs, File dataFile) throws IOException {
        dataFile.mkdirs();
        IPFSNetwork.getImage(DID, ipfs, DATA_PATH + DID + "/DID.png");
        IPFSNetwork.getImage(WID, ipfs, DATA_PATH + DID + "/PublicShare.png");
    }

    /**
     * This method writes the mentioned data into the file passed to it
     * This also allows to take a decision on whether or not to append the data to the already existing content in the file
     *
     * @param filePath     Location of the file to be read and written into
     * @param data         Data to be added
     * @param appendStatus Decides whether or not to append the new data into the already existing data
     */

    public static void writeToFile(String filePath, String data, Boolean appendStatus) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        try {
            File writeFile = new File(filePath);
            FileWriter fw;

            fw = new FileWriter(writeFile, appendStatus);

            fw.write(data);
            fw.close();
        } catch (IOException e) {
            FunctionsLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }
    }


    /**
     * This method allows transfer of a single message to a selected recipient and receive a response
     * It uses Libp2p stack to establish a connection between two nodes
     * Then a socket is bound to the port IPFS is listening on
     *
     * @param message        Message to be sent
     * @param receiverPeerID Identity of the Receiver
     * @param appNameExt     Extention to the application name that the receiver is already listening on
     * @param port           Port number for the nodes to get connected
     * @param username       Username
     * @return Reply message from the receiver
     */
    public static String singleDataTransfer(JSONObject message, String receiverPeerID, String appNameExt, int port, String username) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String receiverProof = "";
        try {
            String applicationName = receiverPeerID.concat(appNameExt);
            forward(applicationName, port, receiverPeerID);

            Socket socket = new Socket("127.0.0.1", port);

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintStream out = new PrintStream(socket.getOutputStream());
            out.println(message);

            while ((receiverProof = in.readLine()) == null) {
            }
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            FunctionsLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }
        return receiverProof;
    }

    /**
     * This method helps to sign a data with the selectively disclosed private share
     *
     * @param filePath Location of the Private share
     * @param hash     Data to be signed on
     * @return Signature for the data
     * @throws IOException   Handles IO Exceptions
     */

    public static String getSignFromShares(String filePath, String hash) throws IOException, JSONException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        BufferedImage pvt = ImageIO.read(new File(filePath));
        String firstPrivate = PropImage.img2bin(pvt);

        int[] privateIntegerArray1 = strToIntArray(firstPrivate);
        JSONObject P = randomPositions("signer", hash, 32, privateIntegerArray1);
        int[] finalpos = (int[]) P.get("posForSign");
        int[] originalpos = (int[]) P.get("originalPos");
        int[] p1Sign = getPrivatePosition(finalpos, privateIntegerArray1);
        String p1 = intArrayToStr(p1Sign);
        return p1;
    }


    /**
     * This function will sign on JSON data with private share
     * @param details Details(JSONObject) to sign on
     * @return Signature
     * @throws IOException Handles IO Exceptions
     * @throws JSONException Handles JSON Exceptions
     */
    public static String sign(JSONObject details) throws IOException, JSONException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        String DID = details.getString("did");
        String filePath = DATA_PATH + DID + "/PrivateShare.png";
        String hash = calculateHash(details.toString(), "SHA3-256");

        BufferedImage pvt = ImageIO.read(new File(filePath));
        String firstPrivate = PropImage.img2bin(pvt);

        int[] privateIntegerArray1 = strToIntArray(firstPrivate);
        JSONObject P = randomPositions("signer", hash, 32, privateIntegerArray1);
        int[] finalpos = (int[]) P.get("posForSign");
        int[] originalpos = (int[]) P.get("originalPos");
        int[] p1Sign = getPrivatePosition(finalpos, privateIntegerArray1);
        return intArrayToStr(p1Sign);
    }

    /**
     * This function will connect to the receiver
     * @param connectObject Details required for connection[DID, appName]
     * @throws JSONException Handles JSON Exception
     */
    public static void establishConnection(JSONObject connectObject) throws JSONException {
        IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
        String DID = connectObject.getString("did");
        String appName = DID.concat(connectObject.getString("appName"));
        String peerID = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", DID);
        swarmConnect(peerID, ipfs);
        forward(appName, SEND_PORT, peerID);
    }


    /**
     * This function will allow for a user to listen on a particular appName
     * @param connectObject Details required for connection[DID, appName]
     * @throws JSONException Handles JSON Exception
     */
    public static void listenThread(JSONObject connectObject) throws JSONException {
        String DID = connectObject.getString("did");
        String appName = DID.concat(connectObject.getString("appName"));
        listen(appName, RECEIVER_PORT);
    }


    /**
     * This function converts any integer to its binary form
     * @param a An integer
     * @return Binary form of the input integer
     */
    public static String intToBinary(int a) {
        String temp = Integer.toBinaryString(a);
        while (temp.length() != 8) {
            temp = "0" + temp;
        }
        return temp;
    }

    /**
     * This function converts any binary value to its Decimal form
     * @param bin Binary value
     * @return Decimal format of the input binary
     */
    public static String binarytoDec(String bin) {
        StringBuilder result = new StringBuilder();
        int val;
        for (int i = 0; i < bin.length(); i += 8) {
            val = Integer.parseInt(bin.substring(i, i + 8), 2);
            result.append(val);
            result.append(' ');
        }
        return result.toString();
    }

    /**
     * This method updates the mentioned JSON file
     * It can add a new data or remove an existing data
     *
     * @param operation Decides whether to add or remove data
     * @param filePath  Locatio nof the JSON file to be updated
     * @param data      Data to be added or removed
     */

    public static void updateJSON(String operation, String filePath, String data) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        try {
            while (mutex) {
            }

            mutex = true;
            File file = new File(filePath);
            if (!file.exists()) {
                file.createNewFile();
                JSONArray js = new JSONArray();
                writeToFile(file.toString(), js.toString(), false);
            }
            String fileContent = readFile(filePath);
            JSONArray contentArray = new JSONArray(fileContent);

            for (int i = 0; i < contentArray.length(); i++) {
                JSONObject contentArrayJSONObject = contentArray.getJSONObject(i);
                Iterator iterator = contentArrayJSONObject.keys();
                if (operation.equals("remove")) {
                    while (iterator.hasNext()) {
                        String tempString = iterator.next().toString();
                        if (contentArrayJSONObject.getString(tempString).equals(data))
                            contentArray.remove(i);
                    }
                }
            }
            writeToFile(filePath, contentArray.toString(), false);

            if (operation.equals("add")) {
                JSONArray newData = new JSONArray(data);
                for (int i = 0; i < newData.length(); i++)
                    contentArray.put(newData.getJSONObject(i));
                writeToFile(filePath, contentArray.toString(), false);
            }
            mutex = false;
        } catch (JSONException e) {
            FunctionsLogger.error("JSON Exception Occurred", e);
            e.printStackTrace();
        } catch (IOException e) {
            FunctionsLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }
    }

    /**
     * This method gets you a required data from a JSON file with a tag to be compared with
     *
     * @param filePath Location of the JSON file
     * @param get      Data to be fetched from the file
     * @param tagName  Name of the tag to be compared with
     * @param value    Value of the tag to be compared with
     * @return Data that is fetched from the JSON file
     */
    public static String getValues(String filePath, String get, String tagName, String value) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String resultString = "";
        JSONParser jsonParser = new JSONParser();

        try (FileReader reader = new FileReader(filePath)) {
            Object obj = jsonParser.parse(reader);
            org.json.simple.JSONArray List = (org.json.simple.JSONArray) obj;
            for (Object o : List) {
                org.json.simple.JSONObject js = (org.json.simple.JSONObject) o;

                String itemCompare = js.get(tagName).toString();
                if (value.equals(itemCompare)) {
                    resultString = js.get(get).toString();
                }
            }
        } catch (ParseException e) {
            FunctionsLogger.error("JSON Parser Exception Occurred", e);
            e.printStackTrace();
        } catch (IOException e) {
            FunctionsLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }


        return resultString;
    }

    /**
     * This method gets the Operating System that is being run the your computer
     *
     * @return Name of the running Operating System
     */
    public static String getOsName() {
        return System.getProperty("os.name");
    }

    /**
     * This function calculates the minimum number of quorum peers required for consensus to work
     * @return Minimum number of quorum count for consensus to work
     */
    public static int minQuorum(){
        return (((QUORUM_COUNT - 1)/3)*2) + 1;
    }

    /**
     * This method checks if Quorum is available for consensus
     * @param quorum List of peers
     * @param ipfs IPFS instance
     * @return final list of all available Quorum peers
     */
    public static ArrayList<String> QuorumCheck(JSONObject quorum, IPFS ipfs) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        ArrayList<String> peers = new ArrayList<>();
        if (!(quorum.length() < minQuorum() || quorum.length() > QUORUM_COUNT)) {
            for (int i = 1; i <= quorum.length(); i++) {
                String quorumPeer;
                try {
                    quorumPeer = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", quorum.getString("QUORUM_" + i));
                    if (ipfs.swarm.peers().toString().contains(quorumPeer)) {
                        peers.add(quorumPeer);
                        FunctionsLogger.debug(quorumPeer);
                    }

                } catch (JSONException e) {
                    FunctionsLogger.error("JSON Exception Occurred", e);
                    e.printStackTrace();
                } catch (IOException e) {
                    FunctionsLogger.error("IOException Occurred", e);
                    e.printStackTrace();
                }
            }
            if (peers.size() < minQuorum())
                return null;
            else {
                FunctionsLogger.debug("Quorum Peer IDs : " + peers);
                return peers;
            }
        } else
            return null;
    }

    /**
     * This method identifies the Peer ID of the system by IPFS during installation
     *
     * @param filePath Location of the file in which your IPFS Peer ID is stored
     * @return Your system's Peer ID assigned by IPFS
     */
    public static String getPeerID(String filePath) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        JSONArray fileContentArray;
        String peerid = "";
        JSONObject fileContentArrayJSONObject;
        try {
            String fileContent = Functions.readFile(filePath);
            fileContentArray = new JSONArray(fileContent);
            fileContentArrayJSONObject = fileContentArray.getJSONObject(0);
            peerid = fileContentArrayJSONObject.getString("peerid");
        } catch (JSONException e) {
            FunctionsLogger.error("JSON Exception Occurred", e);
            e.printStackTrace();
        }
        return peerid;
    }

    public static int[] getPrivatePosition(int[] positions, int[] privateArray) {
        int[] PrivatePosition = new int[positions.length];
        for (int k = 0; k < positions.length; k++) {
            int a = positions[k];
            int b = privateArray[a];
            PrivatePosition[k] = b;
        }
        return PrivatePosition;
    }

    public static JSONObject randomPositions(String role, String hash, int numberOfPositions, int[] pvt1) throws JSONException {

        int u = 0, l = 0, m = 0;
        long st = System.currentTimeMillis();
        int[] hashCharacters = new int[256];
        int[] randomPositions = new int[32];
        int[] randPos = new int[256];
        int[] finalPositions, pos;
        int[] originalPos = new int[32];
        int[] posForSign = new int[32 * 8];
        for (int k = 0; k < numberOfPositions; k++) {

            hashCharacters[k] = Character.getNumericValue(hash.charAt(k));
            randomPositions[k] = (((2402 + hashCharacters[k]) * 2709) + ((k + 2709) + hashCharacters[(k)])) % 2048;
            originalPos[k] = (randomPositions[k] / 8) * 8;

            pos = new int[32];
            pos[k] = originalPos[k];
            randPos[k] = pos[k];
            finalPositions = new int[8];
            for (int p = 0; p < 8; p++) {
                posForSign[u] = randPos[k];
                randPos[k]++;
                u++;

                finalPositions[l] = pos[k];
                pos[k]++;
                l++;
                if (l == 8)
                    l = 0;
            }
            if (role.equals("signer")) {
                int[] p1 = getPrivatePosition(finalPositions, pvt1);
                hash = calculateHash(hash + intArrayToStr(originalPos) + intArrayToStr(p1), "SHA3-256");
            } else {
                int[] p1 = new int[8];
                for (int i = 0; i < 8; i++) {
                    p1[i] = pvt1[m];
                    m++;
                }
                hash = calculateHash(hash + intArrayToStr(originalPos) + intArrayToStr(p1), "SHA3-256");
            }

        }

        JSONObject resultObject = new JSONObject();
        resultObject.put("originalPos", originalPos);
        resultObject.put("posForSign", posForSign);
        long et = System.currentTimeMillis();
        FunctionsLogger.debug("Time taken for randomPositions Calculation " + (et-st));
        return resultObject;
    }

    /**
     * This functions extends the random positions into 64 times longer
     *
     * @param randomPositions Array of random positions
     * @param positionsCount  Number of positions required
     * @return Extended array of positions
     */
    public static int[] finalPositions(int[] randomPositions, int positionsCount) {
        int[] finalPositions = new int[positionsCount * 64];
        int u = 0;
        for (int k = 0; k < positionsCount; k++) {
            for (int p = 0; p < 64; p++) {
                finalPositions[u] = randomPositions[k];
                randomPositions[k]++;
                u++;
            }
        }
        return finalPositions;
    }

    /**
     * This function deletes the mentioned file
     *
     * @param fileName Location of the file to be deleted
     */
    public static void deleteFile(String fileName) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        try {
            Files.deleteIfExists(Paths.get(fileName));
        } catch (IOException e) {
            FunctionsLogger.error("IOException Occurred", e);
            e.printStackTrace();
        }
        FunctionsLogger.debug("File Deletion successful");
    }


    /**
     * This functions picks the required number of quorum members from the mentioned file
     *
     * @param filePath Location of the file
     * @param hash     Data from which positions are chosen
     * @return List of chosen members from the file
     */
    public static ArrayList<String> quorumChooser(String filePath, String hash) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        ArrayList<String> quorumList = new ArrayList();
        try {
            String fileContent = readFile(filePath);
            JSONArray blockHeight = new JSONArray(fileContent);

            int[] hashCharacters = new int[256];
            var randomPositions = new ArrayList<Integer>();
            HashSet<Integer> positionSet = new HashSet<>();
            for (int k = 0; positionSet.size() != 7; k++) {
                hashCharacters[k] = Character.getNumericValue(hash.charAt(k));
                randomPositions.add((((2402 + hashCharacters[k]) * 2709) + ((k + 2709) + hashCharacters[(k)])) % blockHeight.length());
                positionSet.add(randomPositions.get(k));
            }

            for (Integer integer : positionSet)
                quorumList.add(blockHeight.getJSONObject(integer).getString("peer-id"));
        } catch (JSONException e) {
            FunctionsLogger.error("JSON Exception Occurred", e);
            e.printStackTrace();
        }
        return quorumList;
    }

    /**
     * This function is to be initially called to setup the environment of your project
     */
    public static void launch() {
        pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        int syncFlag = 0;
        try {
            executeIPFSCommands("ipfs daemon");
            StringBuilder result = new StringBuilder();
            if(!SYNC_IP.contains("127.0.0.1")){
                URL url = new URL(SYNC_IP + "/get");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                    syncFlag = 1;
                }
                rd.close();
                writeToFile(DATA_PATH + "DataTable.json", result.toString(), false);
            }

        } catch (MalformedURLException e) {
            FunctionsLogger.error("MalformedURL Exception Occurred", e);
            e.printStackTrace();
        } catch (ProtocolException e) {
            FunctionsLogger.error("Protocol Exception Occurred", e);
            e.printStackTrace();
        } catch (IOException e) {
            FunctionsLogger.error("IO Exception Occurred", e);
            e.printStackTrace();
        }
        if (syncFlag == 1)
            FunctionsLogger.info("Synced Successfully!");
        else
            FunctionsLogger.info("Not synced! Try again after sometime.");
    }
}

