package com.cgi.lambda.create.prod;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.amazonaws.services.lambda.runtime.Context;

/**
 * A simple test harness for locally invoking your Lambda function handler.
 */
public class snapshotCreatorTest {

    private static Object input;

    @BeforeClass
    public static void createInput() throws IOException {
        // TODO: set up your sample input object here.
        input = null;
    }

    private Context createContext() {
        TestContext ctx = new TestContext();

        // TODO: customize your context here if needed.
        ctx.setFunctionName("Your Function Name");

        return ctx;
    }

    @Test
    public void testsnapshotCreator() {
        snapshotCreator handler = new snapshotCreator();
        Context ctx = createContext();

        String output = handler.handleRequest(input, ctx);

        // TODO: validate output here if needed.
        Assert.assertEquals("", output);
    }
    
    @Test
    public void datecomparison() {    	
    	snapshotCreator handler = new snapshotCreator();
		Calendar c= Calendar.getInstance();
		c.add(Calendar.DATE, -1);
		Date deleteDate = c.getTime();		
		DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");  
		String deleteDateString = dateFormat.format(deleteDate);
        Boolean datePassed=handler.deletedayPassed(deleteDateString); // check that function deletes snapshot when date passes
        Assert.assertEquals(true, datePassed);
        c.add(Calendar.DATE, 2);
        deleteDate = c.getTime();
        deleteDateString = dateFormat.format(deleteDate);
        datePassed=handler.deletedayPassed(deleteDateString);
        Assert.assertEquals(false, datePassed);  // checks that function retains snapshot if date has not passed
    	
    }
    
    
}
