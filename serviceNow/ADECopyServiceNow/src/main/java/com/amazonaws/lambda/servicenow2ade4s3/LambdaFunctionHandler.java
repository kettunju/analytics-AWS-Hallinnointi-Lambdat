package com.amazonaws.lambda.servicenow2ade4s3;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.CanonicalGrantee;
import com.amazonaws.services.s3.model.Grant;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.Permission;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class LambdaFunctionHandler implements RequestHandler<S3Event, String> {
	// Code is mostly copied from Lambda related to Reimari ADE Loading
    private AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
	private String destinationBucket = System.getenv("databucket");
	private String manifestBucket = System.getenv("manifestbucket");
	private String manifestBucketqa = System.getenv("manifestbucketQA");
	private String manifestBucketprod = System.getenv("manifestbucketPROD");
	private String prefix = System.getenv("manifestprefix");
	private String fullscans = System.getenv("fullscan");
	private String DevARN = System.getenv("DevARN");
	private String QAARN = System.getenv("QAARN");
	private String PRODARN = System.getenv("PRODARN");
	
	protected boolean fullscanned = false;

    public LambdaFunctionHandler() {}

    
    //check for enviormental 
    
    // Test purpose only.
    LambdaFunctionHandler(AmazonS3 s3) {
        this.s3 = s3;
    }

    @Override
    public String handleRequest(S3Event event, Context context) {
        // context.getLogger().log("Received event: " + event);
    	// Get S3
        String sourceBucket = event.getRecords().get(0).getS3().getBucket().getName();
		String sourceKey = event.getRecords().get(0).getS3().getObject().getKey();
		if(!sourceKey.contains(".json"))
		{
			context.getLogger().log("Error:Not JSON file");
			System.exit(-1);
			return "";
		}
		if(sourceKey.contains("test"))
		{
			context.getLogger().log("ignoring test file");
			System.exit(-1);
			return "";
		}
		
	    // Get manifest template from AWS Bucket
		String[] splittedSourceFilename=sourceKey.replaceAll(".json", "").split("/");
		String sourceFilename= splittedSourceFilename[splittedSourceFilename.length-1];//filenamewithout .csv extension
		context.getLogger().log("Started copying file:" + sourceFilename+"\n manifestbucket: " +manifestBucket);
		String manifestTemplate="";
		try {
			manifestTemplate = s3.getObjectAsString(sourceBucket, "manifest/"+sourceFilename+".json");    	  
		}
		catch (Exception e){
			System.err.println("Error:Fatal: could not read manifest template");
			System.out.println("trying to read manifesttemplate from bucket:"+sourceBucket +"\n with key: "+sourceFilename);
			System.exit(-1);
		}
		
		Calendar c= Calendar.getInstance();
		
		checkIfFileiSFullscan(sourceFilename);
		String destinationfilename= "table."+sourceFilename+"."+ c.getTime().getTime()+".batch."+c.getTime().getTime()+".fullscanned."+fullscanned+".json";
		String destinationKey=sourceFilename+"/"+destinationfilename;
		try {	
			context.getLogger().log("Copying file from " + sourceBucket + sourceKey +" to : " +destinationBucket+ destinationKey);
			s3.copyObject(sourceBucket, sourceKey, destinationBucket, destinationKey);
		}		catch (Exception e){
			System.err.println("Error:Fatal: could not copy file to ade bucket");

			System.exit(-1);
		}
		
		String manifestKey= "manifest/"+prefix+sourceFilename+"/manifest-table."+prefix+sourceFilename+"."+c.getTime().getTime()+".batch."+c.getTime().getTime()+".fullscanned."+fullscanned+".json.json";
		String manifest=createManifestContent(manifestTemplate,destinationBucket,destinationKey);
		ObjectMetadata metadata = new ObjectMetadata();
		Long contentLength = Long.valueOf(manifest.getBytes().length);
		metadata.setContentLength(contentLength);
		InputStream stream = new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8));
    	PutObjectRequest requestdev = new PutObjectRequest(manifestBucket, manifestKey, stream,metadata);
    	stream = new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8));
    	PutObjectRequest requestQA = new PutObjectRequest(manifestBucketqa, manifestKey, stream,metadata);
    	stream = new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8));
    	PutObjectRequest requestPROD = new PutObjectRequest(manifestBucketprod, manifestKey, stream,metadata);
    	
    	//PutObjectRequest request = new PutObjectRequest(manifestBucket, manifestKey, stream,metadata);
    	
    	Collection<Grant> grantCollection = new ArrayList<Grant>();
		grantCollection.add( new Grant(new CanonicalGrantee(s3.getS3AccountOwner().getId()), Permission.FullControl));
        grantCollection.add( new Grant(new CanonicalGrantee(PRODARN), Permission.FullControl)); //PROD
        grantCollection.add( new Grant(new CanonicalGrantee(QAARN), Permission.FullControl)); //QA
        grantCollection.add( new Grant(new CanonicalGrantee(DevARN), Permission.FullControl)); //DEV
        try {
			context.getLogger().log("manifest file to " +manifestBucket + manifestKey);
			AccessControlList reimariObjectAcl = new AccessControlList();
            reimariObjectAcl.getGrantsAsList().clear();
            reimariObjectAcl.getGrantsAsList().addAll(grantCollection);
            requestdev.setAccessControlList(reimariObjectAcl);
            requestQA.setAccessControlList(reimariObjectAcl);
            requestPROD.setAccessControlList(reimariObjectAcl);
            s3.putObject(requestdev);
            s3.putObject(requestQA);
            s3.putObject(requestPROD);
        }		catch (Exception e){
			System.err.println("Error:Fatal: could not create new manifest file with correct acl \n check permissions \n manifest filename: "+ manifestKey );
			System.exit(-1);
		}
		return "";
    }
    
    /**
	 * Checks if file name is in fullscan list list is constructed so that __ separates files from environmentalvariable set in lambda
	 * Sets fullscanned parameter to true if current file being copied is in the list of fullscan enabled list
	 * @param fileName with out .csv that of the file that is being manifested and copied for usage of ADE
	 * @return
	 */
	protected void checkIfFileiSFullscan(String fileName) {
		String[] fullscanNames=fullscans.split("__");
		for  (String listFile : fullscanNames) {
			if (listFile.toLowerCase().equals(fileName.toLowerCase())) {
				fullscanned = true;
				break;
			}			
		}
	}

	public String createManifestContent(String manifestTemplate,String destinatonBucket,String destinationKey) {
		JSONObject mainObject = new JSONObject(manifestTemplate);
		JSONArray jArray=mainObject.getJSONArray("entries");
		JSONObject entry = new JSONObject();
		entry.put("mandatory", "true");
		entry.put("url", "s3://"+destinatonBucket+"/"+destinationKey);
		jArray.put(entry);		
		return mainObject.toString().replaceAll("(\n)", "\r\n");
	}
}