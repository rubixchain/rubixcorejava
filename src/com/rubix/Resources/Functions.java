package com.rubix.Resources;

import com.rubix.AuthenticateNode.PropImage;
import com.rubix.Ping.PingCheck;
import io.ipfs.api.IPFS;
import io.ipfs.multiaddr.MultiAddress;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import static com.rubix.Resources.APIHandler.addPublicData;
import static com.rubix.Resources.IPFSNetwork.*;


public class Functions {

    public static boolean mutex = false;
    public static String DATA_PATH = "";
    public static String TOKENS_PATH = "";
    public static String TOKENCHAIN_PATH = "";
    public static String LOGGER_PATH = "";
    public static String WALLET_DATA_PATH = "";
    public static String PAYMENTS_PATH = "";
    public static int RECEIVER_PORT, GOSSIP_SENDER, GOSSIP_RECEIVER, QUORUM_PORT, SENDER2Q1, SENDER2Q2, SENDER2Q3, SENDER2Q4, SENDER2Q5, SENDER2Q6, SENDER2Q7;
    public static int QUORUM_COUNT;
    public static int SEND_PORT;
    public static int IPFS_PORT;
    public static String SYNC_IP = "";
    public static String ADVISORY_IP = "";
    public static int APPLICATION_PORT;
    public static String EXPLORER_IP = "";
    public static String USERDID_IP = "";
    public static String configPath = "";
    public static String dirPath = "";
    public static boolean CONSENSUS_STATUS;
    public static JSONObject QUORUM_MEMBERS;
    public static JSONArray BOOTSTRAPS;

    public static Logger FunctionsLogger = Logger.getLogger(Functions.class);

    public static void setDir() {
        String OSName = getOsName();
        if (OSName.contains("Windows"))
            dirPath = "C:\\Rubix\\";
        else if (OSName.contains("Mac"))
            dirPath = "/Applications/Rubix/";
        else if (OSName.contains("Linux"))
            dirPath = "/home/" + getSystemUser() + "/Rubix/";
        else
            System.exit(0);
    }

    public static void setConfig() {
        setDir();
        configPath = dirPath.concat("config.json");
    }

    /**
     * This method sets the required paths used in the functions
     */
    public static void pathSet() {
        setConfig();
        String configFileContent = readFile(configPath);

        JSONArray pathsArray;
        try {
            pathsArray = new JSONArray(configFileContent);

            DATA_PATH = pathsArray.getJSONObject(0).getString("DATA_PATH");
            TOKENS_PATH = pathsArray.getJSONObject(0).getString("TOKENS_PATH");
            LOGGER_PATH = pathsArray.getJSONObject(0).getString("LOGGER_PATH");
            TOKENCHAIN_PATH = pathsArray.getJSONObject(0).getString("TOKENCHAIN_PATH");
            WALLET_DATA_PATH = pathsArray.getJSONObject(0).getString("WALLET_DATA_PATH");
            PAYMENTS_PATH = pathsArray.getJSONObject(0).getString("PAYMENTS_PATH");

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
            ADVISORY_IP = pathsArray.getJSONObject(2).getString("ADVISORY_IP");

            CONSENSUS_STATUS = pathsArray.getJSONObject(3).getBoolean("CONSENSUS_STATUS");
            QUORUM_COUNT = pathsArray.getJSONObject(3).getInt("QUORUM_COUNT");

            QUORUM_MEMBERS = pathsArray.getJSONObject(4);

            BOOTSTRAPS = pathsArray.getJSONArray(5);


        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public static void nodeData(String did, String wid, IPFS ipfs) throws IOException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        File dataFolder = new File(DATA_PATH + did + "/");

        if (!(dataFolder.exists())) {
            dataFolder.mkdirs();
            IPFSNetwork.getImage(did, ipfs, DATA_PATH + did + "/DID.png");
            IPFSNetwork.getImage(wid, ipfs, DATA_PATH + did + "/PublicShare.png");

            IPFSNetwork.add(DATA_PATH + did + "/DID.png", ipfs);
            IPFSNetwork.add(DATA_PATH + did + "/PublicShare.png", ipfs);

        } else {
            File DIDFile = new File(dataFolder + "/DID.png");
            File WIDFile = new File(dataFolder + "/PublicShare.png");
            if (!DIDFile.exists())
                IPFSNetwork.getImage(did, ipfs, DATA_PATH + did + "/DID.png");

            if (!WIDFile.exists())
                IPFSNetwork.getImage(wid, ipfs, DATA_PATH + did + "/PublicShare.png");

            String didHash = IPFSNetwork.add(DATA_PATH + did + "/DID.png", ipfs);
            String widHash = IPFSNetwork.add(DATA_PATH + did + "/PublicShare.png", ipfs);

            if (!didHash.equals(did) || !widHash.equals(wid)) {
                FunctionsLogger.debug("New DID Created for user " + did);
                File didFile = new File(DATA_PATH + did + "/DID.png");
                File widFile = new File(DATA_PATH + did + "/PublicShare.png");
                didFile.delete();
                widFile.delete();
                IPFSNetwork.getImage(did, ipfs, DATA_PATH + did + "/DID.png");
                IPFSNetwork.getImage(wid, ipfs, DATA_PATH + did + "/PublicShare.png");

                IPFSNetwork.add(DATA_PATH + did + "/DID.png", ipfs);
                IPFSNetwork.add(DATA_PATH + did + "/PublicShare.png", ipfs);
            }
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
            String OS = getOsName();
            String[] command = new String[3];
            if (OS.contains("Mac") || OS.contains("Linux")) {
                command[0] = "bash";
                command[1] = "-c";
            } else if (OS.contains("Windows")) {
                command[0] = "cmd.exe";
                command[1] = "/c";
            }
            command[2] = "whoami";

            processID = Runtime.getRuntime().exec(command);
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
     * This method writes the mentioned data into the file passed to it
     * This also allows to take a decision on whether or not to append the data to the already existing content in the file
     *
     * @param filePath     Location of the file to be read and written into
     * @param data         Data to be added
     * @param appendStatus Decides whether or not to append the new data into the already existing data
     */

    public synchronized static void writeToFile(String filePath, String data, Boolean appendStatus) {
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
     * This method helps to sign a data with the selectively disclosed private share
     *
     * @param filePath Location of the Private share
     * @param hash     Data to be signed on
     * @return Signature for the data
     * @throws IOException Handles IO Exceptions
     */

    public static String getSignFromShares(String filePath, String hash) throws IOException, JSONException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        BufferedImage pvt = ImageIO.read(new File(filePath));
        String firstPrivate = PropImage.img2bin(pvt);

        int[] privateIntegerArray1 = strToIntArray(firstPrivate);
        JSONObject P = randomPositions("signer", hash, 32, privateIntegerArray1);
        int[] finalpos = (int[]) P.get("posForSign");
        int[] p1Sign = getPrivatePosition(finalpos, privateIntegerArray1);
        String p1 = intArrayToStr(p1Sign);
        return p1;
    }


    /**
     * This function will sign on JSON data with private share
     *
     * @param detailsString Details(JSONObject) to sign on
     * @return Signature
     * @throws IOException   Handles IO Exceptions
     * @throws JSONException Handles JSON Exceptions
     */
    public static String sign(String detailsString) throws IOException, JSONException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONObject details = new JSONObject(detailsString);
        String DID = details.getString("did");
        String filePath = DATA_PATH + DID + "/PrivateShare.png";
        String hash = calculateHash(detailsString, "SHA3-256");

        BufferedImage pvt = ImageIO.read(new File(filePath));
        String firstPrivate = PropImage.img2bin(pvt);

        int[] privateIntegerArray1 = strToIntArray(firstPrivate);
        JSONObject P = randomPositions("signer", hash, 32, privateIntegerArray1);
        int[] finalpos = (int[]) P.get("posForSign");
        int[] p1Sign = getPrivatePosition(finalpos, privateIntegerArray1);
        return intArrayToStr(p1Sign);
    }

    /**
     * This function will allow for a user to listen on a particular appName
     *
     * @param connectObject Details required for connection[DID, appName]
     * @throws JSONException Handles JSON Exception
     */
    public static void listenThread(JSONObject connectObject) {
        String DID = null;
        try {
            DID = connectObject.getString("did");
            String appName = DID.concat(connectObject.getString("appName"));
            listen(appName, RECEIVER_PORT);
        } catch (JSONException e) {
            FunctionsLogger.error("JSONException Occurred", e);
        }

    }


    /**
     * This function converts any integer to its binary form
     *
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
     *
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
        try {
            JSONArray searchSpace = new JSONArray(readFile(filePath));
            for (int i = 0; i < searchSpace.length(); i++) {
                JSONObject temp = searchSpace.getJSONObject(i);
                if (temp.get(tagName).equals(value)) {
                    resultString = temp.getString(get);
                    break;
                }
            }
        } catch (JSONException e) {
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
     *
     * @return Minimum number of quorum count for consensus to work
     */
    public static int minQuorum() {
        return (((QUORUM_COUNT - 1) / 3) * 2) + 1;
    }

    /**
     * This function calculates the minimum number of quorum peers required for consensus to work
     *
     * @return Minimum number of quorum count for consensus to work
     */
    public static int minQuorum(int count) {
        return (((count - 1) / 3) * 2) + 1;
    }


    /**
     * This method checks if Quorum is available for consensus
     *
     * @param quorum List of peers
     * @return final list of all available Quorum peers
     */
    public static ArrayList<String> QuorumCheck(JSONArray quorum, int size) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        ArrayList<String> peers = new ArrayList<>();

        if (quorum.length() >= minQuorum(size)) {
            for (int i = 0; i < quorum.length(); i++) {
                String quorumPeer;
                try {
                    quorumPeer = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", quorum.getString(i));
                    if (checkSwarmConnect().contains(quorumPeer)) {
                        peers.add(quorumPeer);
                        FunctionsLogger.debug(quorumPeer + " added to list");
                    }
                } catch (JSONException e) {
                    FunctionsLogger.error("JSON Exception Occurred", e);
                    e.printStackTrace();
                }
            }

            FunctionsLogger.debug("Quorum Peer IDs : " + peers);
            return peers;
        } else
            return null;
    }

    /**
     * This method is to connect to quorum nodes for consensus
     *
     * @param quorum JSONArray is list of quorum nodes didHash
     * @param ipfs   ipfs instance
     */


    public static void QuorumSwarmConnect(JSONArray quorum, IPFS ipfs) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        for (int i = 0; i < quorum.length(); i++) {
            String quorumPeer;
            try {
                quorumPeer = getValues(DATA_PATH + "DataTable.json", "peerid", "didHash", quorum.getString(i));

                IPFSNetwork.swarmConnectP2P(quorumPeer, ipfs);

            } catch (JSONException e) {
                FunctionsLogger.error("JSON Exception Occurred", e);
                e.printStackTrace();
            }
        }

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
        return resultObject;
    }

    /**
     * This functions extends the random positions into 64 times longer
     *
     * @param randomPositions Array of random positions
     * @param positionsCount  Number of positions required
     * @return Extended array of positions
     */
//    public static int[] finalPositions(int[] randomPositions, int positionsCount) {
//        int[] finalPositions = new int[positionsCount * 64];
//        int u = 0;
//        for (int k = 0; k < positionsCount; k++) {
//            for (int p = 0; p < 64; p++) {
//                finalPositions[u] = randomPositions[k];
//                randomPositions[k]++;
//                u++;
//            }
//        }
//        return finalPositions;
//    }

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

    }


//    /**
//     * This functions picks the required number of quorum members from the mentioned file
//     *
//     * @param filePath Location of the file
//     * @param hash     Data from which positions are chosen
//     * @return List of chosen members from the file
//     */
//    public static ArrayList<String> quorumChooser(String filePath, String hash) {
//        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
//        ArrayList<String> quorumList = new ArrayList();
//        try {
//            String fileContent = readFile(filePath);
//            JSONArray blockHeight = new JSONArray(fileContent);
//
//            int[] hashCharacters = new int[256];
//            var randomPositions = new ArrayList<Integer>();
//            HashSet<Integer> positionSet = new HashSet<>();
//            for (int k = 0; positionSet.size() != 7; k++) {
//                hashCharacters[k] = Character.getNumericValue(hash.charAt(k));
//                randomPositions.add((((2402 + hashCharacters[k]) * 2709) + ((k + 2709) + hashCharacters[(k)])) % blockHeight.length());
//                positionSet.add(randomPositions.get(k));
//            }
//
//            for (Integer integer : positionSet)
//                quorumList.add(blockHeight.getJSONObject(integer).getString("peer-id"));
//        } catch (JSONException e) {
//            FunctionsLogger.error("JSON Exception Occurred", e);
//            e.printStackTrace();
//        }
//        return quorumList;
//    }

    /**
     * This function is to be initially called to setup the environment of your project
     */
    public static void launch() {
        pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        executeIPFSCommands("ipfs daemon --enable-gc");

        FunctionsLogger.debug("Enabled ipfs GC");
    }

    /**
     * This function checks if the Rubix Working Directory is present or not
     *
     * @return A message
     * @throws JSONException handle all JSON Exceptions
     */
    public static String checkDirectory() throws JSONException {
        setDir();
        File mainDir = new File(dirPath);
        if (!mainDir.exists()) {
            mainDir.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("info", "Main Directory Missing");
            result.put("status", "Failed");
            return result.toString();
        }
        setConfig();
        File configFile = new File(configPath);

        if (!configFile.exists()) {
            configFile.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("info", "Config File Missing");
            result.put("status", "Failed");
            return result.toString();
        }

        pathSet();
        File dataFolder = new File(DATA_PATH);
        File loggerFolder = new File(LOGGER_PATH);
        File tokensFolder = new File(TOKENS_PATH);
        File tokenChainsFolder = new File(TOKENCHAIN_PATH);
        File walletDataFolder = new File(WALLET_DATA_PATH);

        if (!dataFolder.exists() || !loggerFolder.exists() || !tokenChainsFolder.exists() || !tokensFolder.exists() || !walletDataFolder.exists()) {
            dataFolder.delete();
            loggerFolder.delete();
            tokenChainsFolder.delete();
            tokensFolder.delete();
            walletDataFolder.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("info", "Inner Folders Missing");
            result.put("status", "Failed");
            return result.toString();
        }

        File didFile = new File(DATA_PATH + "DID.json");
        File dataTable = new File(DATA_PATH + "DataTable.json");
        if (!didFile.exists() || !dataTable.exists()) {
            didFile.delete();
            dataTable.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("nfo", "DID / Datatable File Missing ");
            result.put("status", "Failed");
            return result.toString();
        }

        String didContent = readFile(DATA_PATH + "DID.json");
        JSONArray didArray = new JSONArray(didContent);
        String myDID = didArray.getJSONObject(0).getString("didHash");

        File didFolder = new File(DATA_PATH + myDID + "/");
        if (!didFolder.exists()) {
            didFolder.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("info", "DID Folder Missing");
            result.put("status", "Failed");
            return result.toString();
        }

        File didImage = new File(DATA_PATH + myDID + "/DID.png");
        File widImage = new File(DATA_PATH + myDID + "/PublicShare.png");
        File pvtImage = new File(DATA_PATH + myDID + "/PrivateShare.png");
        if (!didImage.exists() || !widImage.exists() || !pvtImage.exists()) {
            didImage.delete();
            didImage.delete();
            didImage.delete();
            JSONObject result = new JSONObject();
            result.put("message", "User not registered, create your Decentralised Identity!");
            result.put("info", "Shares Images Missing");
            result.put("status", "Failed");
            return result.toString();
        }
        JSONObject returnObject = new JSONObject();
        returnObject.put("message", "User successfully registered!");
        returnObject.put("status", "Success");
        return returnObject.toString();
    }

    /**
     * This method is used generate new token given level and tokenNumber
     * New token is the multi hash of hash of token number and hex of level
     *
     * @param level       level in token tree
     * @param tokenNumber unique number for particular level in token tree
     * @return mined token
     */

    public static String mineToken(int level, int tokenNumber) {

        String tokenHash = calculateHash(String.valueOf(tokenNumber), "SHA-256");
        String levelHex = Integer.toHexString(level);
        if (level < 16)
            levelHex = String.valueOf(0).concat(levelHex);
        String token = String.valueOf(0) + levelHex + tokenHash;
        return token;
    }


    public static String toBinary(int x, int len) {
        if (len > 0) {
            return String.format("%" + len + "s",
                    Integer.toBinaryString(x)).replaceAll(" ", "0");
        }
        return null;
    }

    /**
     * This method is used to check uniqueness of value in a particular file
     *
     * @param consensusID unique ID that is to be compared
     * @return true if data is unique , false otherwise
     */

    public static Boolean integrityCheck(String consensusID) {
        File file = new File(WALLET_DATA_PATH + "QuorumSignedTransactions.json");
        if (file.exists()) {
            if (getValues(file.getAbsolutePath(), "senderdid", "consensusID", consensusID).equals(""))
                return true;
            else
                return false;
        } else
            return true;
    }

    /**
     * This method is used generate current utc time
     */

    public static Date getCurrentUtcTime() throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
        SimpleDateFormat localDateFormat = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
        return localDateFormat.parse(simpleDateFormat.format(new Date()));
    }


    /**
     * This method is used to update quorum credits in server
     *
     * @param quorumArray      jsonarray of all quorum
     * @param signedQuorumList jsonarray of all signedquorum
     * @param status           boolean for consensus status
     * @param type             transaction type : default to 1
     * @return mined token
     */


    public static void updateQuorum(JSONArray quorumArray, JSONArray signedQuorumList, boolean status, int type) throws IOException, JSONException {

        if (type == 1) {
            String urlQuorumUpdate = ADVISORY_IP + "/updateQuorum";
            URL objQuorumUpdate = new URL(urlQuorumUpdate);
            HttpURLConnection conQuorumUpdate = (HttpURLConnection) objQuorumUpdate.openConnection();

            conQuorumUpdate.setRequestMethod("POST");
            conQuorumUpdate.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            conQuorumUpdate.setRequestProperty("Accept", "application/json");
            conQuorumUpdate.setRequestProperty("Content-Type", "application/json");
            conQuorumUpdate.setRequestProperty("Authorization", "null");

            JSONObject dataToSendQuorumUpdate = new JSONObject();
            dataToSendQuorumUpdate.put("completequorum", quorumArray);
            dataToSendQuorumUpdate.put("signedquorum", signedQuorumList);
            dataToSendQuorumUpdate.put("status", status);
            String populateQuorumUpdate = dataToSendQuorumUpdate.toString();

            conQuorumUpdate.setDoOutput(true);
            DataOutputStream wrQuorumUpdate = new DataOutputStream(conQuorumUpdate.getOutputStream());
            wrQuorumUpdate.writeBytes(populateQuorumUpdate);
            wrQuorumUpdate.flush();
            wrQuorumUpdate.close();

            int responseCodeQuorumUpdate = conQuorumUpdate.getResponseCode();
            FunctionsLogger.debug("Sending 'POST' request to URL : " + urlQuorumUpdate);
            FunctionsLogger.debug("Post Data : " + populateQuorumUpdate);
            FunctionsLogger.debug("Response Code : " + responseCodeQuorumUpdate);

            BufferedReader inQuorumUpdate = new BufferedReader(
                    new InputStreamReader(conQuorumUpdate.getInputStream()));
            String outputQuorumUpdate;
            StringBuffer responseQuorumUpdate = new StringBuffer();
            while ((outputQuorumUpdate = inQuorumUpdate.readLine()) != null) {
                responseQuorumUpdate.append(outputQuorumUpdate);
            }
            inQuorumUpdate.close();

        }
    }


    /**
     * This method is used get getquorum from advisory node
     *
     * @param betaHash            betahash in string form
     * @param gammaHash           gammahash in string form
     * @param senderDidIpfsHash   didhash of sender
     * @param receiverDidIpfsHash didhash of receiver
     * @param tokenslength        tokens amount for picking quorum
     * @return JSONArray of quorum nodes
     */


    public static JSONArray getQuorum(String betaHash, String gammaHash, String senderDidIpfsHash, String receiverDidIpfsHash, int tokenslength) throws IOException, JSONException {
        JSONArray quorumArray;
        String urlQuorumPick = ADVISORY_IP + "/getQuorum";
        URL objQuorumPick = new URL(urlQuorumPick);
        HttpURLConnection conQuorumPick = (HttpURLConnection) objQuorumPick.openConnection();

        conQuorumPick.setRequestMethod("POST");
        conQuorumPick.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conQuorumPick.setRequestProperty("Accept", "application/json");
        conQuorumPick.setRequestProperty("Content-Type", "application/json");
        conQuorumPick.setRequestProperty("Authorization", "null");

        JSONObject dataToSendQuorumPick = new JSONObject();
        dataToSendQuorumPick.put("betahash", betaHash);
        dataToSendQuorumPick.put("gammahash", gammaHash);
        dataToSendQuorumPick.put("sender", senderDidIpfsHash);
        dataToSendQuorumPick.put("receiver", receiverDidIpfsHash);
        dataToSendQuorumPick.put("tokencount", tokenslength);
        String populateQuorumPick = dataToSendQuorumPick.toString();

        conQuorumPick.setDoOutput(true);
        DataOutputStream wrQuorumPick = new DataOutputStream(conQuorumPick.getOutputStream());
        wrQuorumPick.writeBytes(populateQuorumPick);
        wrQuorumPick.flush();
        wrQuorumPick.close();

        int responseCodeQuorumPick = conQuorumPick.getResponseCode();
        FunctionsLogger.debug("Sending 'POST' request to URL : " + urlQuorumPick);
        FunctionsLogger.debug("Post Data : " + populateQuorumPick);
        FunctionsLogger.debug("Response Code : " + responseCodeQuorumPick);

        BufferedReader inQuorumPick = new BufferedReader(
                new InputStreamReader(conQuorumPick.getInputStream()));
        String outputQuorumPick;
        StringBuffer responseQuorumPick = new StringBuffer();
        while ((outputQuorumPick = inQuorumPick.readLine()) != null) {
            responseQuorumPick.append(outputQuorumPick);
        }
        inQuorumPick.close();
        FunctionsLogger.debug(" responsequorumpick " + responseQuorumPick.toString());
        quorumArray = new JSONArray(responseQuorumPick.toString());
        return quorumArray;
    }

    /**
     * This method is used update credits of didHash
     *
     * @param didHash senderdidhash
     * @param credits credits to be removed
     * @return JSONArray of quorum nodes
     */

    public static void mineUpdate(String didHash, int credits) throws IOException, JSONException {
        String urlMineUpdate = ADVISORY_IP + "/updatemine";
        URL objMineUpdate = new URL(urlMineUpdate);
        HttpURLConnection conMineUpdate = (HttpURLConnection) objMineUpdate.openConnection();

        conMineUpdate.setRequestMethod("POST");
        conMineUpdate.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conMineUpdate.setRequestProperty("Accept", "application/json");
        conMineUpdate.setRequestProperty("Content-Type", "application/json");
        conMineUpdate.setRequestProperty("Authorization", "null");

        JSONObject dataToSendMineUpdate = new JSONObject();
        dataToSendMineUpdate.put("didhash", didHash);
        dataToSendMineUpdate.put("credits", credits);
        String populateMineUpdate = dataToSendMineUpdate.toString();

        conMineUpdate.setDoOutput(true);
        DataOutputStream wrMineUpdate = new DataOutputStream(conMineUpdate.getOutputStream());
        wrMineUpdate.writeBytes(populateMineUpdate);
        wrMineUpdate.flush();
        wrMineUpdate.close();

        int responseCodeMineUpdate = conMineUpdate.getResponseCode();
        FunctionsLogger.debug("Sending 'POST' request to URL : " + urlMineUpdate);
        FunctionsLogger.debug("Post Data : " + populateMineUpdate);
        FunctionsLogger.debug("Response Code : " + responseCodeMineUpdate);

        BufferedReader inMineUpdate = new BufferedReader(
                new InputStreamReader(conMineUpdate.getInputStream()));
        String outputMineUpdate;
        StringBuffer responseMineUpdate = new StringBuffer();
        while ((outputMineUpdate = inMineUpdate.readLine()) != null) {
            responseMineUpdate.append(outputMineUpdate);
        }
        inMineUpdate.close();

    }

    public static int checkHeartBeat(String peerId, String appName) {

        if (forwardCheck(appName, QUORUM_PORT, peerId)) {
            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + peerId);
            return 1;
        } else {
            IPFSNetwork.executeIPFSCommands("ipfs p2p close -t /p2p/" + peerId);
            return 0;
        }
    }

    /**
     * To Sync DataTable.json, if required
     */
    public static void syncDataTable(String did, String peerId) {
        try {
            String dataTableData = readFile(DATA_PATH + "DataTable.json");
            boolean isObjectValid = false;
            JSONArray dataTable = new JSONArray(dataTableData);
            for (int i = 0; i < dataTable.length(); i++) {
                JSONObject dataTableObject = dataTable.getJSONObject(i);
                if ((did != null && dataTableObject.getString("didHash").equals(did))
                        ||
                        (peerId != null && dataTableObject.getString("peerid").equals(peerId))) {
                    isObjectValid = true;
                    break;
                }
            }
            if (!isObjectValid) {
                FunctionsLogger.debug("Syncing Datatable.json!");
                APIHandler.networkInfo();
            }
        } catch (Exception e) {
            FunctionsLogger.error("Exception Occured", e);
            e.printStackTrace();
        }
    }

    public static void removeToken() {
        String bnkFile = readFile(PAYMENTS_PATH.concat("BNK00.json"));
        JSONArray bnkArray = new JSONArray(bnkFile);
        JSONObject removeToken = bnkArray.getJSONObject(0);
        bnkArray.remove(0);
        writeToFile(PAYMENTS_PATH.concat("BNK00.json"), bnkArray.toString(), false);

        File doubleSpentFile = new File(PAYMENTS_PATH.concat("DoubleSpent.json"));
        if (!doubleSpentFile.exists()) {
            try {
                doubleSpentFile.createNewFile();
            } catch (IOException e) {
                FunctionsLogger.debug("File couldn't be created");
            }
            JSONArray removeArray = new JSONArray();
            removeArray.put(removeToken);
            writeToFile(PAYMENTS_PATH.concat("DoubleSpent.json"), removeArray.toString(), false);
        } else {
            String removeFile = readFile(PAYMENTS_PATH.concat("DoubleSpent.json"));
            JSONArray removeArray = new JSONArray(removeFile);
            removeArray.put(removeToken);
            writeToFile(PAYMENTS_PATH.concat("DoubleSpent.json"), removeArray.toString(), false);
        }

    }

    public static void tokenBank() {
        pathSet();
        String bank = readFile(PAYMENTS_PATH.concat("BNK00.json"));
        JSONArray bankArray = new JSONArray(bank);

        ArrayList<String> bankDuplicates = new ArrayList<>();
        for (int i = 0; i < bankArray.length(); i++) {
            if (!bankDuplicates.contains(bankArray.getJSONObject(i).getString("tokenHash")))
                bankDuplicates.add(bankArray.getJSONObject(i).getString("tokenHash"));
        }

        if (bankDuplicates.size() < bankArray.length()) {
            FunctionsLogger.debug("Duplicates Found. Cleaning up ...");

            JSONArray newBank = new JSONArray();
            for (int i = 0; i < bankDuplicates.size(); i++) {
                JSONObject tokenObject = new JSONObject();
                tokenObject.put("tokenHash", bankDuplicates.get(i));
                newBank.put(tokenObject);
            }
            writeToFile(PAYMENTS_PATH.concat("BNK00.json"), newBank.toString(), false);
        }

        File tokensPath = new File(TOKENS_PATH);
        String contents[] = tokensPath.list();
        ArrayList tokenFiles = new ArrayList();
        for (int i = 0; i < contents.length; i++) {
            if (!contents[i].contains("PARTS"))
                tokenFiles.add(contents[i]);
        }

        for (int i = 0; i < tokenFiles.size(); i++) {
            if (!bankDuplicates.contains(tokenFiles.get(i).toString()))
                deleteFile(TOKENS_PATH.concat(tokenFiles.get(i).toString()));
        }

    }

    public static Double getPartsBalance() throws JSONException {
        pathSet();

        Double balance = 0.000D;
        String didFile = readFile(DATA_PATH.concat("DID.json"));
        JSONArray didArray = new JSONArray(didFile);
        String myDID = didArray.getJSONObject(0).getString("didHash");

        File partsFile = new File(PAYMENTS_PATH + "PartsToken.json");
        if (partsFile.exists()) {
            String PART_TOKEN_CHAIN_PATH = TOKENCHAIN_PATH.concat("/PARTS/");
            File partFolder = new File(PART_TOKEN_CHAIN_PATH);
            if (!partFolder.exists())
                partFolder.mkdir();
            String partsTokenFile = readFile(PAYMENTS_PATH + "PartsToken.json");
            JSONArray partTokensArray = new JSONArray(partsTokenFile);
            Double parts = 0.000D;
            if (partTokensArray.length() != 0) {
                for (int i = 0; i < partTokensArray.length(); i++) {
                    String token = partTokensArray.getJSONObject(i).getString("tokenHash");
                    String tokenChainFile = readFile(PART_TOKEN_CHAIN_PATH.concat(token).concat(".json"));
                    JSONArray tokenChainArray = new JSONArray(tokenChainFile);

                    Double availableParts = 0.000D, senderCount = 0.000D, receiverCount = 0.000D;
                    for (int k = 0; k < tokenChainArray.length(); k++) {
                        if (tokenChainArray.getJSONObject(k).has("role")) {
                            if (tokenChainArray.getJSONObject(k).getString("role").equals("Sender") && tokenChainArray.getJSONObject(k).getString("sender").equals(myDID)) {
                                senderCount += tokenChainArray.getJSONObject(k).getDouble("amount");
                            } else if (tokenChainArray.getJSONObject(k).getString("role").equals("Receiver") && tokenChainArray.getJSONObject(k).getString("receiver").equals(myDID)) {
                                receiverCount += tokenChainArray.getJSONObject(k).getDouble("amount");
                            }
                        }
                    }
                    availableParts = 1 - (senderCount - receiverCount);
                    parts += availableParts;

                }
            }
            parts = formatAmount(parts);
            balance = balance + parts;


            int count = 0;
            File shiftedFile = new File(PAYMENTS_PATH.concat("ShiftedTokens.json"));
            if (shiftedFile.exists()) {
                String shiftedContent = readFile(PAYMENTS_PATH.concat("ShiftedTokens.json"));
                JSONArray shiftedArray = new JSONArray(shiftedContent);
                ArrayList<String> arrayTokens = new ArrayList<>();
                for (int i = 0; i < shiftedArray.length(); i++)
                    arrayTokens.add(shiftedArray.getString(i));


                for (int i = 0; i < partTokensArray.length(); i++) {
                    if (!arrayTokens.contains(partTokensArray.getJSONObject(i).getString("tokenHash")))
                        count++;
                }
            } else
                count = partTokensArray.length();

            balance = balance - count;
        }

        balance = formatAmount(balance);
        return balance;
    }

    public static Double checkTokenPartBalance(String tokenHash) throws JSONException {
        pathSet();

        String didFile = readFile(DATA_PATH.concat("DID.json"));
        JSONArray didArray = new JSONArray(didFile);
        String myDID = didArray.getJSONObject(0).getString("didHash");

        String tokenChainFile = readFile(TOKENCHAIN_PATH.concat("PARTS/").concat(tokenHash).concat(".json"));
        JSONArray tokenChainArray = new JSONArray(tokenChainFile);

        Double senderCount = 0.000D, receiverCount = 0.000D;
        for (int k = 0; k < tokenChainArray.length(); k++) {
            if (tokenChainArray.getJSONObject(k).has("role")) {
                if (tokenChainArray.getJSONObject(k).getString("role").equals("Sender") && tokenChainArray.getJSONObject(k).getString("sender").equals(myDID)) {
                    senderCount += tokenChainArray.getJSONObject(k).getDouble("amount");
                } else if (tokenChainArray.getJSONObject(k).getString("role").equals("Receiver") && tokenChainArray.getJSONObject(k).getString("receiver").equals(myDID)) {
                    receiverCount += tokenChainArray.getJSONObject(k).getDouble("amount");
                }
            }
        }
        senderCount = formatAmount(senderCount);
        receiverCount = formatAmount(receiverCount);

        Double availableParts = receiverCount - senderCount;
        availableParts = formatAmount(availableParts);
        if (availableParts <= 0.000)
            availableParts = 1 + availableParts;

        availableParts = formatAmount(availableParts);
        return availableParts;
    }


    public static Double getBalance() throws JSONException {
        pathSet();
        Double balance = 0.000D;
        String tokenMapFile = readFile(PAYMENTS_PATH + "TokenMap.json");
        JSONArray tokenMapArray = new JSONArray(tokenMapFile);

        String didFile = readFile(DATA_PATH.concat("DID.json"));
        JSONArray didArray = new JSONArray(didFile);
        String myDID = didArray.getJSONObject(0).getString("didHash");

        for (int i = 0; i < tokenMapArray.length(); i++) {
            String bankFile = readFile(PAYMENTS_PATH + tokenMapArray.getJSONObject(i).getString("type") + ".json");
            JSONArray bankArray = new JSONArray(bankFile);
            int tokenCount = bankArray.length();
            int value = tokenCount * tokenMapArray.getJSONObject(i).getInt("value");
            balance = balance + value;
        }


        File partsFile = new File(PAYMENTS_PATH + "PartsToken.json");
        if (partsFile.exists()) {
            String PART_TOKEN_CHAIN_PATH = TOKENCHAIN_PATH.concat("/PARTS/");
            File partFolder = new File(PART_TOKEN_CHAIN_PATH);
            if (!partFolder.exists())
                partFolder.mkdir();
            String partsTokenFile = readFile(PAYMENTS_PATH + "PartsToken.json");
            JSONArray partTokensArray = new JSONArray(partsTokenFile);
            Double parts = 0.000D;
            if (partTokensArray.length() != 0) {
                for (int i = 0; i < partTokensArray.length(); i++) {
                    String token = partTokensArray.getJSONObject(i).getString("tokenHash");
                    String tokenChainFile = readFile(PART_TOKEN_CHAIN_PATH.concat(token).concat(".json"));
                    JSONArray tokenChainArray = new JSONArray(tokenChainFile);

                    Double availableParts = 0.000D, senderCount = 0.000D, receiverCount = 0.000D;
                    for (int k = 0; k < tokenChainArray.length(); k++) {
                        if (tokenChainArray.getJSONObject(k).has("role")) {
                            if (tokenChainArray.getJSONObject(k).getString("role").equals("Sender") && tokenChainArray.getJSONObject(k).getString("sender").equals(myDID)) {
                                senderCount += tokenChainArray.getJSONObject(k).getDouble("amount");
                            } else if (tokenChainArray.getJSONObject(k).getString("role").equals("Receiver") && tokenChainArray.getJSONObject(k).getString("receiver").equals(myDID)) {
                                receiverCount += tokenChainArray.getJSONObject(k).getDouble("amount");
                            }
                        }
                    }
                    availableParts = 1 - (senderCount - receiverCount);
                    parts += availableParts;

                }
            }
            parts = formatAmount(parts);
            balance = balance + parts;

            balance = formatAmount(balance);
            int count = 0;
            File shiftedFile = new File(PAYMENTS_PATH.concat("ShiftedTokens.json"));
            if (shiftedFile.exists()) {
                String shiftedContent = readFile(PAYMENTS_PATH.concat("ShiftedTokens.json"));
                JSONArray shiftedArray = new JSONArray(shiftedContent);
                ArrayList<String> arrayTokens = new ArrayList<>();
                for (int i = 0; i < shiftedArray.length(); i++)
                    arrayTokens.add(shiftedArray.getString(i));


                for (int i = 0; i < partTokensArray.length(); i++) {
                    if (!arrayTokens.contains(partTokensArray.getJSONObject(i).getString("tokenHash")))
                        count++;
                }
            } else
                count = partTokensArray.length();

            balance = balance - count;
        }


        balance = formatAmount(balance);
        return balance;
    }


    public static Double partTokenBalance(String tokenHash) throws JSONException {
        pathSet();

        String didFile = readFile(DATA_PATH.concat("DID.json"));
        JSONArray didArray = new JSONArray(didFile);
        String myDID = didArray.getJSONObject(0).getString("didHash");

        String tokenChainFile = readFile(TOKENCHAIN_PATH.concat("PARTS/").concat(tokenHash).concat(".json"));
        JSONArray tokenChainArray = new JSONArray(tokenChainFile);

        Double senderCount = 0.000D, receiverCount = 0.000D;
        for (int k = 0; k < tokenChainArray.length(); k++) {
            if (tokenChainArray.getJSONObject(k).has("role")) {
                if (tokenChainArray.getJSONObject(k).getString("role").equals("Sender") && tokenChainArray.getJSONObject(k).getString("sender").equals(myDID)) {
                    senderCount += tokenChainArray.getJSONObject(k).getDouble("amount");
                } else if (tokenChainArray.getJSONObject(k).getString("role").equals("Receiver") && tokenChainArray.getJSONObject(k).getString("receiver").equals(myDID)) {
                    receiverCount += tokenChainArray.getJSONObject(k).getDouble("amount");
                }
            }
        }
        senderCount = formatAmount(senderCount);
        receiverCount = formatAmount(receiverCount);

        Double availableParts = receiverCount - senderCount;
        availableParts = formatAmount(availableParts);
        if (availableParts <= 0.000)
            availableParts = 1 + availableParts;

        availableParts = formatAmount(availableParts);
        return availableParts;
    }

    public static Double formatAmount(Double amount) {
        DecimalFormat df = new DecimalFormat("#.###");
        df.setRoundingMode(RoundingMode.CEILING);

        amount = ((amount * 1e4) / 1e4);
        String bal = String.format("%.3f", amount);
        Number numberFormat = Double.parseDouble(bal);
        amount = Double.parseDouble(df.format(numberFormat.doubleValue()));
        return amount;

    }

    public static void clearParts() {
        String partsFile = readFile(PAYMENTS_PATH.concat("PartsToken.json"));
        JSONArray partsArray = new JSONArray(partsFile);
        for (int i = 0; i < partsArray.length(); i++) {
            if (partTokenBalance(partsArray.getJSONObject(i).getString("tokenHash")) <= 0.000 || partTokenBalance(partsArray.getJSONObject(i).getString("tokenHash")) > 1.000) {
                deleteFile(TOKENS_PATH.concat("PARTS/").concat(partsArray.getJSONObject(i).getString("tokenHash")));
                partsArray.remove(i);
            }
        }
        writeToFile(PAYMENTS_PATH.concat("PartsToken.json"), partsArray.toString(), false);
    }

    public static void backgroundChecks() {
        try {
            Functions.tokenBank();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            Functions.clearParts();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);
        IPFSNetwork.repo(ipfs);

        addPublicData();

    }
    public static String sanityMessage;
    public static boolean sanityCheck(String peerid, IPFS ipfs, int port) throws IOException {
        FunctionsLogger.info("Entering SanityCheck");
        boolean sanityCheckErrorFlag = true;
        if (sanityCheckErrorFlag && checkIPFSStatus(peerid, ipfs)) {
            FunctionsLogger.debug("IPFS is working in " + peerid);
            FunctionsLogger.debug("IPFS check true");
        } else {
            sanityCheckErrorFlag = false;
            FunctionsLogger.debug("IPFS is not working in " + peerid);
            FunctionsLogger.debug("IPFS check false");
            sanityMessage = "IPFS is not working in " + peerid;
        }

        if (sanityCheckErrorFlag) {
            if (bootstrapConnect(peerid, ipfs)) {
                FunctionsLogger.debug("Bootstrap connected for " + peerid);
                FunctionsLogger.debug("Bootstrap check true");
            } else {
                sanityCheckErrorFlag = false;
                FunctionsLogger.debug("Bootstrap connection unsuccessful for " + peerid);
                FunctionsLogger.debug("Bootstrap check false");
                sanityMessage = "Bootstrap connection unsuccessful for " + peerid;
            }
        }

        if (sanityCheckErrorFlag) {
            if (ping(peerid, port)) {
                FunctionsLogger.debug("Jar is running as expected in " + peerid);
                FunctionsLogger.debug("Jar check true");
            } else {
                sanityCheckErrorFlag = false;
                FunctionsLogger.debug("Jar is not running in " + peerid);
                FunctionsLogger.debug("Jar check false");
                sanityMessage = "Jar is not running in " + peerid;
            }
        }
        if (sanityCheckErrorFlag) {
            if (portCheckAndKill(port)) {
                FunctionsLogger.debug("Ports are available for transcations in " + peerid);
                FunctionsLogger.debug("Ports check true");
            } else {
                sanityCheckErrorFlag = false;
                FunctionsLogger.debug("Ports are not available for " + peerid);
                FunctionsLogger.debug("Ports check false");
                sanityMessage = "Ports are not available for " + peerid;
            }
        }


        return sanityCheckErrorFlag;
    }
    public static boolean checkIPFSStatus(String peerid, IPFS ipfs) {
        FunctionsLogger.info("Entering checkIPFSStatus");
        boolean swarmConnectedStatus = false;
        try {
            MultiAddress multiAddress = new MultiAddress("/ipfs/" + peerid);
            FunctionsLogger.info("MultiAdrress concated " + multiAddress + "|||");
            boolean output = swarmConnectP2P(peerid, ipfs);
           
            if (output) {
                swarmConnectedStatus = true;
                FunctionsLogger.debug("Swarm is already connected");
            } else {
                swarmConnectedStatus = false;
                FunctionsLogger.debug("Swarm is not connected");
            }
        } catch (Exception e) {
            FunctionsLogger.error("Check Swarm Connect is failed", e);

        }
        FunctionsLogger.info("checkIPFSStatus return value is " + swarmConnectedStatus);
        return swarmConnectedStatus;
    }


    public static boolean ping(String peerid, int port) throws IOException {
        JSONObject pingCheck = PingCheck.Ping(peerid, port);
        if (pingCheck.getString("status").contains("Failed")) {
            return false;
        } else
            return true;

    }

//    public static String getPing(int port) {
//        try {
//
//            String didContent = readFile(DATA_PATH + "DID.json");
//            JSONArray didArray = new JSONArray(didContent);
//            String myPeerID = didArray.getJSONObject(0).getString("peerid");
//
//            listen(myPeerID.concat("Ping"), port);
//            ServerSocket ss = new ServerSocket(port);
//            FunctionsLogger.info("Get Ping Listening on port " + port + " appname " + myPeerID.concat("Ping"));
//            Socket socket = ss.accept();
//            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//            PrintStream output = new PrintStream(socket.getOutputStream());
//            FunctionsLogger.info("getPing- waiting response from server");
//            String peerID = input.readLine();
//            if (peerID != null && peerID.contains("Qm")) {
//                FunctionsLogger.info("getPing -  Received message from server");
//                output.println("Ping received");
//                FunctionsLogger.debug("Ping received from sender");
//
//                output.close();
//                input.close();
//                socket.close();
//                ss.close();
//                executeIPFSCommands(" ipfs p2p close -t /p2p/" + peerID);
//                FunctionsLogger.info("If - Closing Sockets");
//                return "Ping received from sender and Pong sent";
//            }
//            else{
//                output.close();
//                input.close();
//                socket.close();
//                ss.close();
//                FunctionsLogger.info("Else - Closing Sockets");
//                return "Ping received from sender but not PeerID";
//            }
//
//        } catch (Exception e) {
//            FunctionsLogger.error("Error in client side communication", e);
//            return "Error in client side communication";
//        }
//    }

    public static boolean bootstrapConnect(String peerid, IPFS ipfs) {
        FunctionsLogger.info("bootstrapConnect- entering function");
        String bootNode;
        boolean bootstrapConnected = false;

        MultiAddress multiAddress = new MultiAddress("/ipfs/" + peerid);
        FunctionsLogger.info("bootstrapConnect- multiaddress is " + multiAddress.toString());

        String output = swarmConnectProcess(multiAddress);
        try {
            for (int i = 0; i < BOOTSTRAPS.length(); i++) {
                FunctionsLogger.info("bootstrapConnect- Bootstrap length is " + BOOTSTRAPS.length());

                if (!bootstrapConnected) {
                    FunctionsLogger.info("bootstrapConnect- Connecting to bootstrp " + i);
                    bootNode = String.valueOf(BOOTSTRAPS.get(i));
                    bootNode = bootNode.substring(bootNode.length() - 46);
                    FunctionsLogger.info("bootstrapConnect- trying to connect with " + bootNode);

                    multiAddress = new MultiAddress("/ipfs/" + bootNode);
                    output = swarmConnectProcess(multiAddress);
                    FunctionsLogger.info("bootstrapConnect- connection status to " + bootNode + " is " + output);
                    if (output.contains("success")) {
                        FunctionsLogger.info("bootstrapConnect- trying to swarm connect");
                        multiAddress = new MultiAddress("/ipfs/" + bootNode + "/p2p-circuit/ipfs/" + peerid);
                        output = swarmConnectProcess(multiAddress);
                        FunctionsLogger.info("bootstrapConnect- Swarmconnect status is " + output);
                        if (!output.contains("success")) {
                            IPFSNetworkLogger.debug("swarm attempt failed with " + peerid);
                        } else {
                            IPFSNetworkLogger.debug("swarm Connected : " + peerid);
                            bootstrapConnected = true;
                        }
                    } else {
                        IPFSNetworkLogger.debug("bootstrap connection failed! " + bootNode);
                    }

                }
            }

        } catch (Exception e) {
            FunctionsLogger.error("Error occured during IPFS Swarm connect with bootstrap", e);

        }

        if (bootstrapConnected) {
            return true;
        } else {
            return false;
        }

    }

    public static boolean portCheckAndKill(int port) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        boolean portStatus = false;

        try {
            if (getOsName() != "Windows") {
                portStatus = releasePorts(port);
            } else {
                portStatusWindows(port);
                portStatus = portStatusWindows(port);
            }
        } catch (Exception e) {
            FunctionsLogger.error("Error occured during port checking ", e);
        }
        return portStatus;

    }

    /**
     * This function will release the port in linux based machines if the port is already in use
     */
    public static boolean releasePorts(int port) {
        FunctionsLogger.info("releasePorts- ");
        boolean releasedPort = false;
        String processStr;
        Process processId;
        try {
            processId = Runtime.getRuntime().exec("lsof -ti :" + port);
            long currentPid = ProcessHandle.current().pid();
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(processId.getInputStream()));
            FunctionsLogger.info("releasePorts- process " + br.readLine() + " is occupied in " + port);
            processId = Runtime.getRuntime().exec("pgrep ipfs");
            BufferedReader ipfsPidBr = new BufferedReader(new InputStreamReader(processId.getInputStream()));

            processStr = br.readLine();
            FunctionsLogger.info("releasePorts- Process string is " + processStr);
            if (processStr != null) {
                FunctionsLogger.info("releasePorts- Processstr is not null");
                if (String.valueOf(currentPid) != processStr && ipfsPidBr.readLine() != processStr) {
                    FunctionsLogger.info("releasePorts- jar is running on " + currentPid + " and IPFS is occupied in " + ipfsPidBr.readLine());
                    FunctionsLogger.debug("Port " + port + " is in using, killing PID " + processStr);
                    processId = Runtime.getRuntime().exec("kill -9 " + processStr);
                    FunctionsLogger.info("releasePorts- killing " + processStr);

                }
            }
            releasedPort = true;
            FunctionsLogger.info("releasePorts- status is " + releasedPort);
            processId.waitFor();
            FunctionsLogger.info("releasePorts- Waitng for process");
            processId.destroy();
            FunctionsLogger.info("releasePorts- destorying process after waiting");
        } catch (Exception e) {
            FunctionsLogger.error("Exception Occured at releasePort", e);
            e.printStackTrace();
        }
        return releasedPort;
    }

    public static boolean portStatusWindows(int port) {
        FunctionsLogger.info("Starting portStatusWindows");
        boolean releasedPort = false;
        String processStr;
        Process p;
        try {
            Runtime rt = Runtime.getRuntime();
            Process proc = rt.exec("cmd /c netstat -ano | findstr " + port);
            FunctionsLogger.info("Checking port status");
            long currentPid = ProcessHandle.current().pid();
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            processStr = stdInput.readLine();
            FunctionsLogger.info("Process id found for port is " + processStr + " current jar pid is " + currentPid);
            if (processStr != null && String.valueOf(currentPid) != processStr) {
                int index = processStr.lastIndexOf(" ");
                String sc = processStr.substring(index, processStr.length());
                //System.out.println("Port "+port+" is locked by PID "+sc+". Kindly close this port and retry transcation");
                if (sc != String.valueOf(currentPid)) {
                    FunctionsLogger.debug("Port " + port + " is locked by PID " + sc);
                } else {
                    FunctionsLogger.debug("Port " + port + " is locked by current jar with PID " + sc);
                }
            } else {
                releasedPort = true;
                FunctionsLogger.info("Port is unlocked");
            }
        } catch (Exception e) {
            FunctionsLogger.error("Exception occured at portStatusWindows", e);
            e.printStackTrace();
        }
        return releasedPort;
    }


}

