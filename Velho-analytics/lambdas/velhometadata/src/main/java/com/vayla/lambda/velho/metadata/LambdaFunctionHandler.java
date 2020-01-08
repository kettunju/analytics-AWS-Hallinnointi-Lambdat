package com.vayla.lambda.velho.metadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

public class LambdaFunctionHandler implements RequestHandler<Object, String> {
	private AmazonS3 s3 = AmazonS3Client.builder().withRegion(Regions.EU_WEST_1).build();
	static final String velhoHost = System.getenv("velhoHost"); // https://api.stg.velho.vayla.fi
	static final String velhoAPI = System.getenv("velhoAPI"); // esim. https://api.stg.velho.vayla.fi/v1/kohdeluokat
	static final String velhoRegion = System.getenv("velhoRegion"); // eu-central-1
	static final String velhoService = "execute-api"; // AWS API Gateway
	LambdaLogger logger;

	public LambdaFunctionHandler() {
	}

	// Test purpose only.
	LambdaFunctionHandler(AmazonS3 s3) {
		this.s3 = s3;
	}

	void log(String s) {
		logger.log(s);
	}

	@Override
	public String handleRequest(Object event, Context context) {
		this.logger = context.getLogger();
		log("Received event: " + event);

		try {
			//TODO: luo allekirjoitusavain https://api.stg.velho.vayla.fi/v1/kohdeluokat
			String authHeader = VelhoRequestSigner.getVelhoAuthKey("GET", "execute-api", "https://api.stg.velho.vayla.fi", "eu-central-1", "https://api.stg.velho.vayla.fi/v1/kohdeluokat", "");
			log(authHeader);
			
			// TODO: kutsu velhon rajapintaa
			String jsonString = getMetadata("https://api.stg.velho.vayla.fi/v1/kohdeluokat", authHeader);
			log(jsonString);

		} catch (Exception e) {
			e.printStackTrace();
			log("Virhe");
			System.exit(-1);
		}
		
		return "OK";
	}

	String getMetadata(String urlString, String authHeader) {
		StringBuilder sb = new StringBuilder();
		sb.append("");
		try {
			URL url = new URL(urlString);
			log("Url created");
			
			HttpURLConnection httpconn = (HttpURLConnection) url.openConnection();
			log("Connection open");
			
			// The request can include any headers, but MUST include "host", "x-amz-date", 
			// and (for this scenario) "Authorization". "host" and "x-amz-date" must
			// be included in the canonical_headers and signed_headers, as noted
			// earlier. Order here is not significant.
			httpconn.setRequestMethod("GET");
			httpconn.setRequestProperty("Authorization", authHeader);
			
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
}