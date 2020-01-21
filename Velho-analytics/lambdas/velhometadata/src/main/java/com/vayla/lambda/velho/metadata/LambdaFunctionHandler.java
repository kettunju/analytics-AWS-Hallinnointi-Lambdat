package com.vayla.lambda.velho.metadata;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.vayla.lambda.velho.metadata.auth.AWS4SignerBase;
import com.vayla.lambda.velho.metadata.auth.AWS4SignerForAuthorizationHeader;
import com.vayla.lambda.velho.metadata.utils.HttpUtils;

public class LambdaFunctionHandler implements RequestHandler<Object, String> {
	private AmazonS3 s3 = AmazonS3Client.builder().withRegion(Regions.EU_CENTRAL_1).build();
	static final String velhoHost = "api.stg.velho.vayla.fi";//System.getenv("velhoHost"); 
	static final String velhoAPI = "https://api.stg.velho.vayla.fi/v1/kohdeluokat"; //System.getenv("velhoAPI");
	static final String velhoRegion = "eu-central-1"; //System.getenv("velhoRegion"); 
	static final String velhoService = "execute-api"; // AWS API Gateway
	static final String velhoAccessKey = System.getenv("velhoAccessKey");
	static final String velhoSecretKey = System.getenv("velhoSecretKey");
	LambdaLogger logger;

	public LambdaFunctionHandler() {
	}

	// Test purpose only.
	LambdaFunctionHandler(AmazonS3 s3) {
		this.s3 = s3;
	}

	void log(String s) {
		if(logger != null) logger.log(s);
		else System.out.println(s);
	}

	@Override
	public String handleRequest(Object event, Context context) {
		//this.logger = context.getLogger();
		log("## Received event: " + event);

		try {
			// 1: luo headerit ja allekirjoitusavain 
			AWS4SignerForAuthorizationHeader signer = new AWS4SignerForAuthorizationHeader(new URL(velhoAPI), "GET", velhoService, "eu-central-1");
			Map<String, String> headers = new HashMap<String, String>();
			Map<String, String> queryParams = new HashMap<String, String>();
			String authHeader = signer.computeSignature(headers, queryParams, AWS4SignerBase.EMPTY_BODY_SHA256, velhoAccessKey, velhoSecretKey);
			log(authHeader);
			headers.put("Authorization", authHeader);
			
			// 2: kutsu velhon rajapintaa
			String jsonString = HttpUtils.invokeHttpRequest(new URL(velhoAPI), "GET", headers, null);
			log("---------- RESPONSE ----------");
			log(jsonString);
			log("------------------------------");

		} catch (Exception e) {
			e.printStackTrace();
			log("Virhe");
			System.exit(-1);
		}
		
		return "OK";
	}
}