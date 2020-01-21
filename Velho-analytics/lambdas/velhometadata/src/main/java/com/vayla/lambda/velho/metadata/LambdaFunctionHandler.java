package com.vayla.lambda.velho.metadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

public class LambdaFunctionHandler implements RequestHandler<Object, String> {
	private AmazonS3 s3 = AmazonS3Client.builder().withRegion(Regions.EU_CENTRAL_1).build();
	static final String velhoHost = "api.stg.velho.vayla.fi";//System.getenv("velhoHost"); 
	static final String velhoAPI = "https://api.stg.velho.vayla.fi/v1/kohdeluokat"; //System.getenv("velhoAPI");
	static final String velhoRegion = "eu-central-1"; //System.getenv("velhoRegion"); 
	static final String velhoService = "execute-api"; // AWS API Gateway
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
		// Create a date for headers and the credential string
		LocalDateTime t = LocalDateTime.now();
		log(t.toString());
		DateTimeFormatter format1 = DateTimeFormatter.ofPattern("YYYYMMDD'T'HHMMSS'Z'"); // Amzdate, The time stamp must be in UTC and in the following ISO 8601 format: YYYYMMDD'T'HHMMSS'Z'.
		DateTimeFormatter format2 = DateTimeFormatter.ofPattern("YYYYMMDD"); // Date w/o time, used in credential scope
		String amzdate = t.format(format1);
		String datestamp = t.format(format2); 

		try {
			//TODO: luo allekirjoitusavain 
			String authHeader = VelhoRequestSigner.getVelhoAuthKey("GET", velhoService, velhoHost, "eu-central-1", "", amzdate, datestamp);
			log(authHeader);
			
			// TODO: kutsu velhon rajapintaa
			String jsonString = getMetadata(velhoAPI, authHeader, velhoHost, amzdate);
			log(jsonString);

		} catch (Exception e) {
			e.printStackTrace();
			log("Virhe");
			System.exit(-1);
		}
		
		return "OK";
	}

	String getMetadata(String urlString, String authHeader, String host, String amzdate) {
		StringBuilder sb = new StringBuilder();
		sb.append("");
		try {
			URL url = new URL(urlString);
			log("## Url created");
			
			HttpURLConnection httpconn = (HttpURLConnection) url.openConnection();
			log("## Connection open");
			
			// The request can include any headers, but MUST include "host", "x-amz-date", 
			// and (for this scenario) "Authorization". "host" and "x-amz-date" must
			// be included in the canonical_headers and signed_headers, as noted
			// earlier. Order here is not significant.
			httpconn.setRequestMethod("GET");
			httpconn.setRequestProperty("Authorization", authHeader);
			httpconn.setRequestProperty("host", host);
			httpconn.setRequestProperty("x-amz-date", amzdate);
			log("## Connection properties set");
			
			BufferedReader br = new BufferedReader(new InputStreamReader(httpconn.getInputStream()));
			String str = "";
			while (null != (str = br.readLine())) {
				sb.append(str);
			}

		} catch (MalformedURLException e) {
			String errorMessage = "Error: Failed to create url from: " + urlString;
			log(errorMessage);
			e.printStackTrace();
		} catch (IOException e) {
			String errorMessage = "Error: Failed to open connection to: " + urlString;
			log(errorMessage);
			e.printStackTrace();
		}
		return sb.toString();
	}
	
	public static void main(String[] args) {
		LambdaFunctionHandler me = new LambdaFunctionHandler();
		me.handleRequest(null, null);
	}
}