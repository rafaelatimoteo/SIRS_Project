package sirs.api.lab;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sirs.api.lab.entities.CustomProtocolResponse;
import sirs.api.lab.entities.TestRequest;
import sirs.api.lab.entities.TestResponse;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.util.Random;


@RestController
public class Handlers {
    Crypto cr = new Crypto();
    CustomProtocol cp = new CustomProtocol();

    @PostMapping("/teststoanalyze/{id}")
    public ResponseEntity<CustomProtocolResponse> testsToAnalyze(@PathVariable int id, @RequestBody TestRequest testreq) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, InvalidKeyException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException, CertificateException {
        // Because for simplicity reasons we only answer to requests with id 1
        // id is only for representation purposes in case this was a real system
        // we would have multiple id's...
        if(id != 1)
            return ResponseEntity.status(404).build();

        String certificate = testreq.getCertificate();
        System.out.println("Certificate received "+ certificate);

        //creates certificate from the TesteRequest
        byte [] decoded = Base64.decodeBase64(certificate.replaceAll("-----BEGIN CERTIFICATE-----\n", "").replaceAll("-----END CERTIFICATE-----", ""));
        Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(decoded));

        try {
           boolean valid= cp.verifyCertificate(cert,"src/main/resources/myCA.crt");
           System.out.println("Certificate is "+ valid);
        } catch (InvalidAlgorithmParameterException e) {
            System.out.println("Ups NOT WORKING");
        }

        // Generating random string
        byte[] randomString = new byte[32];
        new Random().nextBytes(randomString);

        // Extract pub key from certificate
        PublicKey pubKey = cp.extractPubKey(certificate);

        // Encrypt random string with pub key
        byte[] encrypted_data = cp.encryptData(randomString, pubKey);
        String encrypted_string64 = java.util.Base64.getEncoder().encodeToString(encrypted_data);

        // Generating secretKey from randomString
        SecretKey secretKey = new SecretKeySpec(randomString, 0, randomString.length, "AES");

        // Encrypting test results with secret key
        String results = "25/05/2020 Covid19:True,Pneumonia:True...";
        String encryptedResults = cp.encryptWithSecretKey(results, secretKey);

        // Object containing encrypted random string + encrypted test results + signature
        String signature = cr.signData(results);
        TestResponse resp = new TestResponse(encryptedResults, signature);

        // Using mapper to transform testResponse into string
        // Doing mac of the resulting string, generating the data string meant to put in customProtocolResponse
        ObjectMapper mapper = new ObjectMapper();
        String respData = mapper.writeValueAsString(resp);
        String data = cp.macMessage(respData.getBytes(), secretKey);

        // This object contains the encrypted data after MAC + random string encrypted with the hospitals pubKey
        CustomProtocolResponse response = new CustomProtocolResponse(data, encrypted_string64);

//        if(signature.equals(""))
//            return ResponseEntity.status(500).build();

        return ResponseEntity.ok(response);
    }
}
