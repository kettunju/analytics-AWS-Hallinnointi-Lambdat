package com.vayla;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BucketProps;

public class VaylaTalvimerenkulkuStack extends Stack {
    public VaylaTalvimerenkulkuStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public VaylaTalvimerenkulkuStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // S3 oletusominaisuudet
        final BucketProps s3BucketProps = BucketProps.builder().encryption(BucketEncryption.S3_MANAGED)
        		.versioned(true)
				.build();

        Bucket workBucket = new Bucket(this, "fmi-saatiedot", s3BucketProps);
        
        /****************************************************************************************************/
        // Havainto Lambdan ymparistomuuttujat
 		Map<String, String> environment = new HashMap<String, String>();
 		environment.put("fmiHost", "data.fmi.fi");
 		environment.put("apiKey", "/fmi-apikey/xxxx");
 		environment.put("havainnotURL", "/wfs?request=getfeature&storedquery_id=fmi::observations::weather::daily::timevaluepair&fmisid=101237&parameters=tday");
 		environment.put("prefix", "havainnot");
 		environment.put("workBucket", workBucket.getBucketName());
 		environment.put("adeBucket", "someadebucket");
 		
 		// havaintodata Lambda
 		final Function havaintoLambda = Function.Builder.create(this, "VaylaFMIHavaintodataLambda")
				.functionName("VaylaFMIHavaintodata").timeout(Duration.minutes(5)).memorySize(1024)
				.code(Code.fromAsset("lambda" + File.separator + "fmihavaintodata" + File.separator + "fmi-havaintodata.zip"))
				.runtime(software.amazon.awscdk.services.lambda.Runtime.NODEJS_12_X).environment(environment)
				.handler("index.handler").build();
 		
 		// S3 oikeudet
 		havaintoLambda
		.addToRolePolicy(PolicyStatement.Builder.create()
				.effect(Effect.ALLOW).actions(Arrays.asList("s3:*"))
				.resources(Arrays.asList("*")).build());
 		
 		/****************************************************************************************************/
 		// Havaintoasema lambdan ymparistomuuttujat
 		environment = new HashMap<String, String>();
 		environment.put("fmiHost", "data.fmi.fi");
 		environment.put("apiKey", "/fmi-apikey/xxxx");
 		environment.put("havaintoasemaURL", "/wfs?service=WFS&version=2.0.0&request=getFeature&storedquery_id=fmi::ef::stations&fmisid=101237");
 		environment.put("prefix", "asemat");
 		environment.put("workBucket", workBucket.getBucketName());
 		environment.put("adeBucket", "someadebucket");
 		
 		// havaintodata Lambda
 		final Function asemaLambda = Function.Builder.create(this, "VaylaFMIHavaintoasematLambda")
				.functionName("VaylaFMIHavaintoasemat").timeout(Duration.minutes(5)).memorySize(1024)
				.code(Code.fromAsset("lambda" + File.separator + "fmihavaintoasemat" + File.separator + "fmi-havaintoasemat.zip"))
				.runtime(software.amazon.awscdk.services.lambda.Runtime.NODEJS_12_X).environment(environment)
				.handler("index.handler").build();
 		
 		// S3 oikeudet
 		asemaLambda
		.addToRolePolicy(PolicyStatement.Builder.create()
				.effect(Effect.ALLOW).actions(Arrays.asList("s3:*"))
				.resources(Arrays.asList("*")).build());
 		
 		/****************************************************************************************************/
 		// Ennuste lambdan ymparistomuuttujat
 		environment = new HashMap<String, String>();
 		environment.put("fmiHost", "data.fmi.fi");
 		environment.put("apiKey", "/fmi-apikey/xxxx");
 		environment.put("ennusteetURL", "/wfs?request=getFeature&storedquery_id=fmi::forecast::ecmwf::europe::daily00::multipointcoverage&latlons=61.040298,28.129162");
 		environment.put("prefix", "ennusteet");
 		environment.put("fmisid", "101237");
 		environment.put("workBucket", workBucket.getBucketName());
 		environment.put("adeBucket", "someadebucket");
 		
 		// Ennuste Lambda
 		final Function ennusteLambda = Function.Builder.create(this, "VaylaFMISaaennusteLambda")
				.functionName("VaylaFMISaaennuste").timeout(Duration.minutes(5)).memorySize(1024)
				.code(Code.fromAsset("lambda" + File.separator + "fmisaaennusteet" + File.separator + "fmi-saaennusteet.zip"))
				.runtime(software.amazon.awscdk.services.lambda.Runtime.NODEJS_12_X).environment(environment)
				.handler("index.handler").build();
 		
 		// S3 oikeudet
 		ennusteLambda
		.addToRolePolicy(PolicyStatement.Builder.create()
				.effect(Effect.ALLOW).actions(Arrays.asList("s3:*"))
				.resources(Arrays.asList("*")).build());
 		
 		/****************************************************************************************************/
 		// Fraktiilit lambdan ymparistomuuttujat
 		environment = new HashMap<String, String>();
 		environment.put("fmiHost", "data.fmi.fi");
 		environment.put("apiKey", "/fmi-apikey/xxxx");
 		environment.put("fraktiilitURL", "/wfs?request=getFeature&storedquery_id=fmi::forecast::ecmwf::europe::surface::fractile::coastadjusted::multipointcoverage&latlons=61.040298,28.129162");
 		environment.put("prefix", "fraktiilit");
 		environment.put("fmisid", "101237");
 		environment.put("workBucket", workBucket.getBucketName());
 		environment.put("adeBucket", "someadebucket");
 		
 		// Fraktiilit Lambda
 		final Function fraktiiliLambda = Function.Builder.create(this, "VaylaFMIFraktiiliLambda")
				.functionName("VaylaFMIFraktiilit").timeout(Duration.minutes(5)).memorySize(1024)
				.code(Code.fromAsset("lambda" + File.separator + "fmifraktiilit" + File.separator + "fmi-fraktiilit.zip"))
				.runtime(software.amazon.awscdk.services.lambda.Runtime.NODEJS_12_X).environment(environment)
				.handler("index.handler").build();
 		
 		// S3 oikeudet
 		fraktiiliLambda
		.addToRolePolicy(PolicyStatement.Builder.create()
				.effect(Effect.ALLOW).actions(Arrays.asList("s3:*"))
				.resources(Arrays.asList("*")).build());
 		
    }
}
