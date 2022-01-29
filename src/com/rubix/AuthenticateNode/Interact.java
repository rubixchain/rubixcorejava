package com.rubix.AuthenticateNode;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static com.rubix.Resources.Functions.LOGGER_PATH;


public class Interact
{
    public String privateShare = "", candidateShare = "",bits;
    public static BufferedImage privateImage, walletImage;
    public StringBuilder pvt,cnd;
    public int[][] candidateArray;
    public int[][] secret;
    public static Logger InteractLogger = Logger.getLogger(Interact.class);


    /**
     * Constructor for setting the secret string
     * @param inputSecret Secret string
     */

    Interact(String inputSecret){
        bits = inputSecret ;
    }

    /**
     * This method creates two shares using NLSS (1,2,2)
     * @return Returns a boolean yes if shares are created successfully
     * @throws IOException handles IO Exception
     */
    public boolean createShare() throws IOException {
        PropertyConfigurator.configure(LOGGER_PATH + "log4jDID.properties");
        bits=bits.replaceAll("\\s+","");
        secret= new int[bits.length()][8];
        candidateArray = new int[bits.length()][8];
        SecretShare share;
        pvt = new StringBuilder();
        cnd = new StringBuilder();
        for(int i = 0; i < bits.length(); i++)
        {
            if(bits.charAt(i)=='0')
            {
                share = new SecretShare(0);
                share.starts();
                for(int j=0;j<8;j++) {
                    secret[i][j] = SecretShare.S0[j];
                    candidateArray[i][j] = SecretShare.Y1[j];
                    pvt.append(SecretShare.S0[j]);
                    cnd.append(SecretShare.Y1[j]);
                }
            }
            if(bits.charAt(i)=='1')
            {
                share = new SecretShare(1);
                share.starts();
                for(int j=0;j<8;j++)
                {
                    secret[i][j]= SecretShare.S0[j];
                    candidateArray[i][j]= SecretShare.Y1[j];
                    pvt.append(SecretShare.S0[j]);
                    cnd.append(SecretShare.Y1[j]);
                }
            }

        }
        privateShare = pvt.toString();
        candidateShare = cnd.toString();
        InteractLogger.debug("Share Generation Successful");
        return checkShare();
    }


    /**
     * This method combines the two shares to verify is the split is right or not
     *
     * @throws IOException handles IO Exceptions
     */
    public boolean checkShare() throws IOException {
        int i,j,sum;
        boolean verified = true;

        for(i=0;i<secret.length;i++) {
            sum = 0;
            for (j = 0; j < secret[i].length; j++)
                sum += secret[i][j] * candidateArray[i][j];

            sum %= 2;

            if (sum != (bits.charAt(i) - 48))
                verified = false;
        }

            if(verified) {
                privateImage = PropImage.generateRGB(privateShare, 1024, 512);
                walletImage = PropImage.generateRGB(candidateShare, 1024, 512);
        }
        return verified;
    }

}


