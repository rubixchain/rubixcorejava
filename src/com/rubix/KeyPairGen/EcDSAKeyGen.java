
package com.rubix.KeyPairGen;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.*;
import java.io.*;
import java.security.*;
import java.util.UUID;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder;

import org.json.JSONException;
import org.json.JSONObject;
import org.apache.log4j.*;
import io.ipfs.api.IPFS;


public class EcDSAKeyGen {

    static {Security.addProvider(new BouncyCastleProvider());}
    
    public static Logger EcDSAKeyGenLogger = Logger.getLogger(EcDSAKeyGen.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);

    public static void generateKeyPair( String password) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        
        
        EcDSAKeyGenLogger.debug("Generating ECDSA private key public key pair");
        KeyPair keyPair = generateCryptoKeyPair();
        PrivateKey priv = keyPair.getPrivate();
        PublicKey pub = keyPair.getPublic();

        pvtKeyEncrypt(priv, password,"privatekey.pem");
        try {
            pathSet();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        writePemFile(pub, "EC PUBLIC KEY", DATA_PATH+"publickey.pub");

        String PublicKeyIpfsHash= add(DATA_PATH+"publickey.pub", ipfs);

        writeToFile(DATA_PATH+"PublicKeyIpfsHash", PublicKeyIpfsHash, false);

        pin(PublicKeyIpfsHash, ipfs);

    }

    public static String genAndRetKey(String password) {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");

        UUID uuid = UUID.randomUUID();
        EcDSAKeyGenLogger.debug("UUID : "+uuid);
        JSONObject resultObject = new JSONObject();
        Security.addProvider(new BouncyCastleProvider());
        EcDSAKeyGenLogger.debug("Generating ECDSA private key public key pair");
        KeyPair keyPair = generateCryptoKeyPair();
        PrivateKey priv = keyPair.getPrivate();
        PublicKey pub = keyPair.getPublic();
        pvtKeyEncrypt(priv, password, uuid+"-Privatekey.pem");
        try {
            pathSet();
        } catch (Exception e) {
            e.printStackTrace();
        }

        writePemFile(pub, "EC PUBLIC KEY", DATA_PATH +uuid +"-Publickey.pub");

        String PublicKeyIpfsHash = add(DATA_PATH +uuid +"-Publickey.pub", ipfs);
        pin(PublicKeyIpfsHash, ipfs);

        String privateKey = readFile(DATA_PATH +uuid +"-Privatekey.pem");
        String publicKey = readFile(DATA_PATH +uuid +"-Publickey.pub");

        deleteFile(DATA_PATH +uuid +"-Privatekey.pem");
        deleteFile(DATA_PATH +uuid +"-Publickey.pub");

        try {
            resultObject.put("privateKey", privateKey);
            resultObject.put("publicKey", publicKey);
            resultObject.put("publicKeyIpfsHash", PublicKeyIpfsHash);

        } catch (JSONException e) {
            System.out.println(
                    e.getMessage() + " " + e.getCause() + " " + e.getClass() + " " + e.getStackTrace().toString());
            EcDSAKeyGenLogger.debug(
                    e.getMessage() + " " + e.getCause() + " " + e.getClass() + " " + e.getStackTrace().toString());
        }

        return resultObject.toString();
    }

    private static KeyPair generateCryptoKeyPair() {

        KeyPairGenerator generator;
        KeyPair keyPair = null;
        try {
            ECParameterSpec ecSpec = ECNamedCurveTable
                .getParameterSpec("secp256k1");
            generator = KeyPairGenerator.getInstance("ECDSA","BC");
            generator.initialize(ecSpec);
            keyPair = generator.generateKeyPair();
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            System.out.println(e.toString());
        }
        /* catch (NoSuchAlgorithmException e) {
            System.out.println(e.toString());
        } */

        return keyPair;
    }

    public static void pvtKeyEncrypt(PrivateKey privateKey, String password, String fileName) {
        EcDSAKeyGenLogger.debug("Encrytping PrivateKey with user supplied password");
        try {
            pathSet();
            EcDSAKeyGenLogger.debug("Writing Encrypted Private Key to .pem file");
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
        EcDSAKeyGenLogger.debug("Writing Public Key to .pub file");
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
