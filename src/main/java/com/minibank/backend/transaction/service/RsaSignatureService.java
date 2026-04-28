package com.minibank.backend.transaction.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RsaSignatureService {

	public void verifyOrThrow(String publicKeyPem, String canonicalPayload, String signatureBase64) {
		if (publicKeyPem == null || publicKeyPem.isBlank()) {
			throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Public key is not registered");
		}
		if (signatureBase64 == null || signatureBase64.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signature is required");
		}

		try {
			PublicKey publicKey = parsePublicKey(publicKeyPem);
			byte[] sigBytes = Base64.getDecoder().decode(signatureBase64);

			Signature verifier = Signature.getInstance("SHA256withRSA");
			verifier.initVerify(publicKey);
			verifier.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));

			boolean ok = verifier.verify(sigBytes);
			if (!ok) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid digital signature");
			}
		} catch (ResponseStatusException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid public key or signature");
		}
	}

	private static PublicKey parsePublicKey(String pem) throws Exception {
		String normalized = pem
			.replace("-----BEGIN PUBLIC KEY-----", "")
			.replace("-----END PUBLIC KEY-----", "")
			.replaceAll("\\s+", "");

		byte[] der = Base64.getDecoder().decode(normalized);
		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(der);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		return kf.generatePublic(keySpec);
	}
}
