package com.vayla.lambda.velho.collector;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.vayla.lambda.velho.collector.util.HttpUtils;
import com.vayla.lambda.velho.dataloader.auth.AWS4SignerBase;
import com.vayla.lambda.velho.dataloader.auth.AWS4SignerForAuthorizationHeader;

public class LambdaFunctionHandler implements RequestHandler<S3Event, String> {
	private static final String velhoAccessKey = System.getenv("velhoAccessKey");
	private static final String velhoSecretKey = System.getenv("velhoSecretKey"); 
	private static String endpointUrl = System.getenv("velhodataurl"); //"http://latauspalvelu.stg.velho.vayla.fi/viimeisin/varustetiedot/kaiteet.json";
	private static String velhoBucket = System.getenv("landingbucket");
	private static final boolean DEBUG = true;
	private LambdaLogger logger;
	
	private AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.EU_CENTRAL_1).build();

    public LambdaFunctionHandler() {}

    // Test purpose only.
    LambdaFunctionHandler(AmazonS3 s3) {
        this.s3 = s3;
    }
    
    void log(String s) {
    	logger.log(s);
    }
    
    void debug(String s) {
    	if(DEBUG) log(s);
    }

    @Override
    public String handleRequest(S3Event event, Context context) {
    	this.logger = context.getLogger();
        log("## Received event: " + event);
        
    	URL myUrl;
		try {
			// 1. muodosta aws v4 signature
			myUrl = new URL(endpointUrl);
			AWS4SignerForAuthorizationHeader signer = 
					new AWS4SignerForAuthorizationHeader(myUrl, "GET", "s3", "eu-central-1");
			
			Map<String, String> headers = new HashMap<String, String>();
			Map<String, String> queryParams = new HashMap<String, String>();
			String authHeader = signer.computeSignature(headers, queryParams, AWS4SignerBase.EMPTY_BODY_SHA256, velhoAccessKey, velhoSecretKey);
			log(authHeader);
			
			// s3 pyyntoja varten pitaa olla mys x-amz-content-sha256 header, GET -> tyhja payload
			headers.put("Authorization", authHeader);
			headers.put("x-amz-content-sha256", AWS4SignerBase.EMPTY_BODY_SHA256);
			
			// 2: kutsu velhon rest-rajapintaa s3:lle
			HttpURLConnection conn = HttpUtils.createHttpConnection(myUrl, "GET", headers);
			String enc = conn.getContentEncoding();
	        String type = conn.getContentType();
	        int len = conn.getContentLength();
	        
	        
	        // 3: tallennetaan velho inputstream aws s3:een
	        ObjectMetadata metadata = new ObjectMetadata();
	    	metadata.setContentEncoding(enc);
	    	metadata.setContentType(type);
	    	metadata.setContentLength(len);
	    	
	    	// landing buckettiin data-prefixilla mutta muuten alkuperaisella polulla l. prefixilla
	    	String key = "data/" + getS3KeyFromURL(new URL(endpointUrl));
	    	
	    	String saveResult = saveObject(conn.getInputStream(), metadata, key);
	    	log("## object saved " + saveResult);
			
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return "OK";
    }
    
    private String saveObject(InputStream input, ObjectMetadata metadata, String key) {
    	log("## saving object ");
    	log("## " + velhoBucket);
    	log("## " + key);
    	log("## " + metadata);
    	PutObjectRequest putObjectRequest = new PutObjectRequest(velhoBucket, key, input, metadata);
    	PutObjectResult result = s3.putObject(putObjectRequest);
    	return result.getETag();
    }
    
    static String getS3KeyFromURL(URL endpoint) {
		if ( endpoint == null ) {
            throw new IllegalArgumentException("URL can't be null");
        }
        String path = endpoint.getPath();
        if ( path == null || path.isEmpty() ) {
            throw new IllegalArgumentException("URL must have a path");
        }
        // drop the leading separator for s3 key
        if (path.startsWith("/")) {
        	path = path.substring(1);
        }
        
		return path;
	}
    
}