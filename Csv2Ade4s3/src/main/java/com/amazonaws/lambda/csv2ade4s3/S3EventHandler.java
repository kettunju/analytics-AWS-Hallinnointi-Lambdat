package com.amazonaws.lambda.csv2ade4s3;

import java.util.Calendar;

import org.json.JSONArray;
import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;


public class S3EventHandler implements RequestHandler<S3Event, String> {

	private AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
	private String destinationBucket="reimariadeload";
	private String manifestBucket="reimariadeload";
	
	private boolean fullscanned=false;
	public S3EventHandler() {}

	// Test purpose only.
	S3EventHandler(AmazonS3 s3) {
		this.s3 = s3;
	}
	//manifest/source_entity_name/manifest-table.source_entity_name.1516362603721.batch.1516362602348.fullscanned.false.csv.gz.json
	@Override
	public String handleRequest(S3Event event, Context context) {

		String sourceBucket = event.getRecords().get(0).getS3().getBucket().getName();
		String sourceKey = event.getRecords().get(0).getS3().getObject().getKey();
		String[] splittedSourceFilename=sourceKey.replaceAll(".csv", "").split("/");
		String sourceFilename= splittedSourceFilename[splittedSourceFilename.length-1];//filenamewithout .csv extension

		String manifestTemplate="";
		try {
			manifestTemplate = s3.getObjectAsString(sourceBucket, "manifest/"+sourceFilename+".json");    	  
		}
		catch (Exception e){
			System.err.println("Error:Fatal: could not read manifest template");
			System.out.println("trying to read manifest from bucket:"+sourceBucket +"\n with key: "+sourceFilename);
			System.exit(-1);
		}    	 
		Calendar c= Calendar.getInstance();
		String destinationfilename= "table."+sourceFilename+"."+ c.getTime().getTime()+".batch."+c.getTime().getTime()+".fullscanned."+fullscanned+".csv";
		String destinationKey=sourceFilename+"/"+destinationfilename;
		try {	
			s3.copyObject(sourceBucket, sourceKey, destinationBucket, destinationKey);
		}		catch (Exception e){
			System.err.println("Error:Fatal: could not copy file to ade bucket");
			
			System.exit(-1);
		}

		String manifestKey= "manifest/"+sourceFilename+"/manifest-table."+sourceFilename+"."+c.getTime().getTime()+".batch."+c.getTime().getTime()+".fullscanned.false.csv.json";
		String manifest=createManifestContent(manifestTemplate,destinationBucket,destinationKey);
		try {
			s3.putObject(manifestBucket, manifestKey, manifest);
		}		catch (Exception e){
			System.err.println("Error:Fatal: could not create new manifest file");
			System.exit(-1);
		}
		return "";
	}

	public String createManifestContent(String manifestTemplate,String destinatonBucket,String destinationKey) {
		JSONObject mainObject = new JSONObject(manifestTemplate);
		JSONArray jArray=mainObject.getJSONArray("entries");
		JSONObject entry = new JSONObject();
		entry.put("mandatory", "true");
		entry.put("url", "s3://"+destinatonBucket+"/"+destinationKey);
		jArray.put(entry);		
		return mainObject.toString();
	}
}