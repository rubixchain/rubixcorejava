package com.rubix.KeyPairGen;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.Key;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;

public class PemFile {
  
  private PemObject pemObject;
  
  public PemFile (Key key, String description) {
    this.pemObject = new PemObject(description, key.getEncoded());
  }
  
  public void write(String filename) throws FileNotFoundException, IOException {
    JcaPEMWriter pemWriter = new JcaPEMWriter(new OutputStreamWriter(new FileOutputStream(filename)));
    try {
      pemWriter.writeObject(this.pemObject);
    } finally {
      pemWriter.close();
    }
  }
}