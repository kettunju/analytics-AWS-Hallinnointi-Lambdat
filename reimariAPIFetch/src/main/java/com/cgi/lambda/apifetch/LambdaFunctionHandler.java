package com.cgi.lambda.apifetch;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class LambdaFunctionHandler implements RequestHandler<Object, String> {	

	private String password=System.getenv("salasana"); 
	private String username =System.getenv("nimi");
	private String region = System.getenv("Cregion");
	private String s3bucket = System.getenv("s3bucket");
	private String address= System.getenv("osoite");
	private ArrayList<String> allowedFilesToRead = new ArrayList<String>();
	/**
	 * main method
	 * calls to fetch file list,  creates thread queue for each file so that they are fetched and uploaded to S3
	 */
	@Override
	public String handleRequest(Object input, Context context) {
		addAllowedStrings();
		context.getLogger().log("Input: " + input);
		try {
			ArrayList<String> csvFiles=downloadCSVFileList(username,password);
			ExecutorService pool = Executors.newFixedThreadPool(50);
			context.getLogger().log("Files to be uploaded: "+ csvFiles.size());
			for (String filename : csvFiles) 
			{
				String savelocation= new SimpleDateFormat("dd.MM.yyyy").format(new Date()) +"/" +filename;
				Runnable loader = new CSVLoader(region,s3bucket,savelocation,address+filename,username,password,context);
				pool.execute(loader);

			}
			pool.shutdown();			
			while (!pool.isTerminated()) {
				try {
					pool.awaitTermination(4, TimeUnit.MINUTES);
				} catch (InterruptedException e) {
					String errorMessage="Error: Threading failure";
					context.getLogger().log(errorMessage);
					System.err.println(errorMessage);
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			System.err.println("Error:FATAL:Failed to download content");
		}
		return "";
	}

	/**
	 * Retrives list of csv files on turvalaiteanalyysi page requires username and password for password authentication
	 * @param username username of the API
	 * @param password password of the API
	 * @return CSV list of the files found that were on whitelist
	 * @throws IOException if list cannot be downloaded
	 */

	public ArrayList<String> downloadCSVFileList(String username, String password) throws IOException {
		Document doc;    	
		String login = username + ":" + password;
		String base64login = new String(Base64.encodeBase64(login.getBytes()));    	
		doc = Jsoup.connect(address).header("Authorization", "Basic " + base64login).get();
		Elements links = doc.select("a[href~=(?i)\\.(csv)]");
		ArrayList<String> csvFiles= new ArrayList<String>();
		String previousContent="";
		for (Element link:links) {
			String linkString=  link.toString();
			String[] content=	 linkString.split("\"");
			for (String part:content ) {
				if (part.endsWith(".csv") && filenameLike(part) && !part.contains(">") && !part.equals(previousContent)) { //cleaning up
					csvFiles.add(part);
					previousContent=part;
					break;
				}
			}
		}
		return csvFiles;    	   
	}
	/**
	 * Adds allowed strings file can have
	 */

	private void addAllowedStrings() {
		allowedFilesToRead.add("navigointilaji.csv");
		allowedFilesToRead.add("t_comp_class.csv");
		allowedFilesToRead.add("t_component.csv");
		allowedFilesToRead.add("t_safety_device.csv");
		allowedFilesToRead.add("t_safety_device_observation.csv");
		allowedFilesToRead.add("t_sd_component.csv");
		allowedFilesToRead.add("t_sd_group.csv");
		allowedFilesToRead.add("t_sd_obs_solid.csv");
		allowedFilesToRead.add("t_sdgroup_sd.csv");
		allowedFilesToRead.add("turvalaite.csv");
		allowedFilesToRead.add("tyyppi.csv");
		allowedFilesToRead.add("vayla.csv");
		allowedFilesToRead.add("vayla_tlyhteys.csv");
		allowedFilesToRead.add("t_sd_obs_floating.csv");
	}
	/**
	 * Checks that filename is substring of prefedined files we are fetching
	 * @param httpFileName
	 * @return true if file is allowed, otherwise false
	 */
	private boolean filenameLike(String httpFileName) {
		for (String filename:allowedFilesToRead)
		{
			if (httpFileName.equals(filename)) 
				System.out.println("found whitelisted csv file:" + httpFileName);	
				return true;			
		}
		System.out.println("ignored file:" + httpFileName);
		return false;
	}    
}