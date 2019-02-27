package com.amazonaws.lambda.csv2ade4s3;

import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Scanner;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

/**
 * A simple test harness for locally invoking your Lambda function handler.
 */
@RunWith(MockitoJUnitRunner.class)
public class S3EventHandlerTest {


	private final String CONTENT_TYPE = "image/jpeg";
	private S3Event event;
	private String testJson;

	@Mock
	private AmazonS3 s3Client;
	@Mock
	private S3Object s3Object;

	@Captor
	private ArgumentCaptor<GetObjectRequest> getObjectRequest;

	/*@Before
	public void setUp() throws IOException {
		event = TestUtils.parse("/manifesttemplate.json", S3Event.class);
		String text = 
				testJson = new Scanner(S3EventHandlerTest.class.getResourceAsStream("/manifesttemplate.json"), "UTF-8").useDelimiter("\\A").next();;
				ObjectMetadata objectMetadata = new ObjectMetadata();
				objectMetadata.setContentType(CONTENT_TYPE);
				when(s3Object.getObjectMetadata()).thenReturn(objectMetadata);
				when(s3Client.getObject(getObjectRequest.capture())).thenReturn(s3Object);
	}*/

	private Context createContext() {
		TestContext ctx = new TestContext();

		// TODO: customize your context here if needed.
		ctx.setFunctionName("Your Function Name");

		return ctx;
	}

	@Test
	public void testS3EventHandler() {
		S3EventHandler handle = new S3EventHandler();
		String resultManifest=  handle.createManifestContent(testJson, "testbucket", "test/hierarcy/test.json");
		Assert.assertEquals("check that resulting manifest is correct", "{\"entries\":[{\"mandatory\":\"true\",\"url\":\"s3://testbucket/test/hierarcy/test.json\"}],\"columns\":[\"SDT_SORT_ORDER\",\"SDT_STAGE_BATCH_ID\",\"SDT_STAGE_CREATE_TIME\",\"SDT_STAGE_ID\",\"SDT_STAGE_SOURCE\",\"SDT_STAGE_SOURCE_TECH\",\"SDT_STAGE_SOURCE_TYPE\",\"field_1\"]}", resultManifest);
	}
	
	@Test
	public void testfileIsScanned() {
		S3EventHandler handle = new S3EventHandler();
		handle.checkIfFileiSFullscan("navigointilaji");
		Assert.assertEquals("check that resulting manifest is correct",true , handle.fullscanned);
	}
}
