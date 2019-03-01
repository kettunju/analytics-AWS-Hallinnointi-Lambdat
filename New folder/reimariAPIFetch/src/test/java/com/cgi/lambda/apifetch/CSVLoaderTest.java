package com.cgi.lambda.apifetch;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.amazonaws.services.lambda.runtime.Context;

public class CSVLoaderTest {

	private Context createContext() {
		TestContext ctx = new TestContext();

		// TODO: customize your context here if needed.
		ctx.setFunctionName("Your Function Name");

		return ctx;
	}



	@Test
	public void testLambdaFunctionHandler() {
		String clientRegion="eu-central-1";
		String bucketname= "reimaritestbucket";
		String url="https://ava.liikennevirasto.fi/turvalaiteanalyysi/navigointilaji.csv";
		String savePath= "/test/testfile.csv";
		Context ctx = createContext();
		CSVLoader loader = new CSVLoader(clientRegion,bucketname,savePath,url,"ava-turvalaiteanalyysi","Te!d3nPovTaHe1lUg",ctx);
		String test;
		try {
			test = loader.getCSV();
			System.out.println(test);
			Assert.assertEquals(test.length()>0, true);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
}
