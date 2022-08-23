package com.rubix.KeyPairGen;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;
import java.io.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder;

import org.json.JSONException;
import org.json.JSONObject;
import org.apache.log4j.*;
import io.ipfs.api.IPFS;


public class RsaKeyGen {

    static {Security.addProvider(new BouncyCastleProvider());}
    
    public static Logger RsaKeyGenLogger = Logger.getLogger(RsaKeyGen.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);

    public static void generateKeyPair( String password) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        
        
        RsaKeyGenLogger.debug("Generating RSA private key public key pair");
        KeyPair keyPair = generateRSAKeyPair();
        PrivateKey priv = keyPair.getPrivate();
        PublicKey pub = keyPair.getPublic();

        pvtKeyEncrypt(priv, password,"privatekey.pem");
        try {
            pathSet();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        writePemFile(pub, "RSA PUBLIC KEY", DATA_PATH+"publickey.pub");

        String PublicKeyIpfsHash= add(DATA_PATH+"publickey.pub", ipfs);

        writeToFile(DATA_PATH+"PublicKeyIpfsHash", PublicKeyIpfsHash, false);

        pin(PublicKeyIpfsHash, ipfs);

    }

    public static String genAndRetKey(String password) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        JSONObject resultObject = new JSONObject();
        Security.addProvider(new BouncyCastleProvider());
        RsaKeyGenLogger.debug("Generating RSA private key public key pair");
        KeyPair keyPair = generateRSAKeyPair();
        PrivateKey priv = keyPair.getPrivate();
        PublicKey pub = keyPair.getPublic();
        pvtKeyEncrypt(priv, password, "tempPrivatekey.pem");
        try {
            pathSet();
        } catch (Exception e) {
            e.printStackTrace();
        }

        writePemFile(pub, "RSA PUBLIC KEY", DATA_PATH + "tempPublickey.pub");

        String PublicKeyIpfsHash = add(DATA_PATH + "tempPublickey.pub", ipfs);
        pin(PublicKeyIpfsHash, ipfs);

        String privateKey = readFile(DATA_PATH + "tempPrivatekey.pem");
        String publicKey = readFile(DATA_PATH + "tempPublickey.pub");

        deleteFile(DATA_PATH + "tempPrivatekey.pem");
        deleteFile(DATA_PATH + "tempPublickey.pub");

        try {
            resultObject.put("privateKey", privateKey);
            resultObject.put("publicKey", publicKey);
            resultObject.put("publicKeyIpfsHash", PublicKeyIpfsHash);

        } catch (JSONException e) {
            System.out.println(
                    e.getMessage() + " " + e.getCause() + " " + e.getClass() + " " + e.getStackTrace().toString());
            RsaKeyGenLogger.debug(
                    e.getMessage() + " " + e.getCause() + " " + e.getClass() + " " + e.getStackTrace().toString());
        }

        return resultObject.toString();
    }

    private static KeyPair generateRSAKeyPair() {

        KeyPairGenerator generator;
        KeyPair keyPair = null;
        try {
            generator = KeyPairGenerator.getInstance("RSA","BC");
            generator.initialize(2048);
            keyPair = generator.generateKeyPair();
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            System.out.println(e.toString());
        }
        /* catch (NoSuchAlgorithmException e) {
            System.out.println(e.toString());
        } */

        return keyPair;
    }

    public static void pvtKeyEncrypt(PrivateKey privateKey, String password, String fileName) {
        RsaKeyGenLogger.debug("Encrytping PrivateKey with user supplied password");
        try {
            pathSet();
            RsaKeyGenLogger.debug("Writing Encrypted Private Key to .pem file");
            JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(
                    new FileWriter(DATA_PATH+ fileName));
            jcaPEMWriter.writeObject(privateKey,
                    new JcePEMEncryptorBuilder("AES-128-CBC").build(password.toCharArray()));
            jcaPEMWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void writePemFile(Key key, String description, String filename) {
        RsaKeyGenLogger.debug("Writing Public Key to .pub file");
        try{
        PemFile pemFile = new PemFile(key, description);
        pemFile.write(filename);
        }
        catch(Exception e)
        {
            System.out.println(e.toString());
        }
    }

}
