package com.gtublog.auth;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
class PemKeyLoader {

    RSAPublicKey readPublicKey(String pem) {
        return (RSAPublicKey) createPublicKey(cleanPem(pem));
    }

    RSAPrivateKey readPrivateKey(String pem) {
        return (RSAPrivateKey) createPrivateKey(cleanPem(pem));
    }

    private PublicKey createPublicKey(String pemBody) {
        try {
            var bytes = Base64.getDecoder().decode(pemBody);
            var keySpec = new X509EncodedKeySpec(bytes);
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not load RSA public key.", exception);
        }
    }

    private PrivateKey createPrivateKey(String pemBody) {
        try {
            var bytes = Base64.getDecoder().decode(pemBody);
            var keySpec = new PKCS8EncodedKeySpec(bytes);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not load RSA private key.", exception);
        }
    }

    private String cleanPem(String pem) {
        return pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }
}
