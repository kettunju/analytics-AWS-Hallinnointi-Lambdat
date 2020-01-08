package com.vayla.lambda.velho.metadata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class VelhoRequestSigner {
	
	static final String accessKey = System.getenv("velhoAccessKey");
	static final String secretKey = System.getenv("velhoSecretKey");
	
	public VelhoRequestSigner() {
		// TODO Auto-generated constructor stub
	}
	
	static byte[] HmacSHA256(String data, byte[] key) throws Exception {
	    String algorithm="HmacSHA256";
	    Mac mac = Mac.getInstance(algorithm);
	    mac.init(new SecretKeySpec(key, algorithm));
	    return mac.doFinal(data.getBytes("UTF-8"));
	}

	static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws Exception {
	    byte[] kSecret = ("AWS4" + key).getBytes("UTF-8");
	    byte[] kDate = HmacSHA256(dateStamp, kSecret);
	    byte[] kRegion = HmacSHA256(regionName, kDate);
	    byte[] kService = HmacSHA256(serviceName, kRegion);
	    byte[] kSigning = HmacSHA256("aws4_request", kService);
	    return kSigning;
	} 
	
	static String getVelhoAuthKey(String method, String service, String host, String region, String endpoint, String requestParameters) throws Exception {
		// Create a date for headers and the credential string
		LocalDate t = LocalDate.now();
		String amzdate = t.format(DateTimeFormatter.ISO_INSTANT);
		String datestamp = t.format(DateTimeFormatter.ISO_LOCAL_DATE); // Date w/o time, used in credential scope
		
		/*
		 *  TASK 1: CREATE A CANONICAL REQUEST
		 */
		// Step 1 is to define the verb (GET, POST, etc.)--already done.

		// Step 2: Create canonical URI--the part of the URI from domain to query 
		// string (use '/' if no path)
		String canonicalUri = "/";

		// Step 3: Create the canonical query string. In this example (a GET request),
		// request parameters are in the query string. Query string values must
		// be URL-encoded (space=%20). The parameters must be sorted by name.
		// For this example, the query string is pre-formatted in the request_parameters variable.
		String canonicalQuerystring = requestParameters;

		// Step 4: Create the canonical headers and signed headers. Header names
		// must be trimmed and lowercase, and sorted in code point order from
		// low to high. Note that there is a trailing \n.
		String canonicalHeaders = "host:" + host + "\n" + "x-amz-date:" + amzdate + "\n";

		// Step 5: Create the list of signed headers. This lists the headers
		// in the canonical_headers list, delimited with ";" and in alpha order.
		// Note: The request can include any headers; canonical_headers and
		// signed_headers lists those that you want to be included in the 
		// hash of the request. "Host" and "x-amz-date" are always required.
		String signedHeaders = "host;x-amz-date";

		// Step 6: Create payload hash (hash of the request body content). For GET
		// requests, the payload is an empty string ("").
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest("".getBytes(StandardCharsets.UTF_8));
		String payloadHash = new String(hash, StandardCharsets.UTF_8);

		// Step 7: Combine elements to create canonical request
		String canonicalRequest = method + "\n" + canonicalUri + "\n" + canonicalQuerystring + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
		
		/*
		 * TASK 2: CREATE THE STRING TO SIGN
		 */
		String algorithm = "AWS4-HMAC-SHA256";
		String credentialScope = datestamp + "/" + region + "/" + service + "/" + "aws4_request";
		byte[] hash2 = digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
		String requestHash = new String(hash2, StandardCharsets.UTF_8);
		
		// Combine
		String stringToSign = algorithm + "\n" + amzdate + "\n" + credentialScope + "\n" + requestHash;
		
		/**
		 * TASK 3: CALCULATE THE SIGNATURE
		 */
		
		// Step 1. Create the signing key using the function defined above.
		byte[] signingKey = getSignatureKey(secretKey, datestamp, region, service);
		
		// Step 2. Sign the stringToSign using the signingKey
		// TODO:
		String signature = "signed";
		
		/**
		 * TASK 4: 	RETURN SIGNING INFORMATION TO THE REQUEST
		 */
		String authorizationHeader = algorithm + " " + "Credential=" + accessKey + "/" + credentialScope + ", " +  "SignedHeaders=" + signedHeaders + ", " + "Signature=" + signature;
		
		return authorizationHeader;
	}

}
