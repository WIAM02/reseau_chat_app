import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Base64;

public class AESRSAExample {

    public static void main(String[] args) throws Exception {
        // Generate AES key
        KeyGenerator aesKeyGen = KeyGenerator.getInstance("AES");
        aesKeyGen.init(256); // AES key size
        SecretKey aesKey = aesKeyGen.generateKey();

        // Generate RSA key pair
        KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
        rsaKeyGen.initialize(2048); // RSA key size
        KeyPair rsaKeyPair = rsaKeyGen.generateKeyPair();
        PublicKey rsaPublicKey = rsaKeyPair.getPublic();
        PrivateKey rsaPrivateKey = rsaKeyPair.getPrivate();

        // Example plaintext
        String plaintext = "This is a secret message";
        System.out.println("Plaintext: " + plaintext);

        // Encrypt the plaintext with AES key
        Cipher aesCipher = Cipher.getInstance("AES");
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);
        byte[] encryptedText = aesCipher.doFinal(plaintext.getBytes());
        String encryptedTextBase64 = Base64.getEncoder().encodeToString(encryptedText);
        System.out.println("Encrypted text (AES): " + encryptedTextBase64);

        // Encrypt the AES key with RSA public key
        Cipher rsaCipher = Cipher.getInstance("RSA");
        rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());
        String encryptedAesKeyBase64 = Base64.getEncoder().encodeToString(encryptedAesKey);
        System.out.println("Encrypted AES key (RSA): " + encryptedAesKeyBase64);

        // Decrypt the AES key with RSA private key
        rsaCipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
        byte[] decryptedAesKeyBytes = rsaCipher.doFinal(Base64.getDecoder().decode(encryptedAesKeyBase64));
        SecretKeySpec decryptedAesKey = new SecretKeySpec(decryptedAesKeyBytes, "AES");

        // Decrypt the text with the decrypted AES key
        aesCipher.init(Cipher.DECRYPT_MODE, decryptedAesKey);
        byte[] decryptedTextBytes = aesCipher.doFinal(Base64.getDecoder().decode(encryptedTextBase64));
        String decryptedText = new String(decryptedTextBytes);
        System.out.println("Decrypted text: " + decryptedText);
    }
}
