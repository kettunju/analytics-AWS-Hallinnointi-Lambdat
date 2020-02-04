package com.vayla;

//import com.sun.tools.javac.util.Dependencies;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.SecretValue;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BucketProps;
import software.amazon.awscdk.services.s3.NotificationKeyFilter;
import software.amazon.awscdk.services.s3.notifications.LambdaDestination;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.stepfunctions.Activity;
import software.amazon.awscdk.services.stepfunctions.Chain;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.Task;
import software.amazon.awscdk.services.stepfunctions.tasks.InvokeActivity;

public class VelhoAnalyticsStack extends Stack {
	public VelhoAnalyticsStack(final Construct parent, final String id) {
		this(parent, id, null);
	}

	public VelhoAnalyticsStack(final Construct parent, final String id, final StackProps props) {
		super(parent, id, props);

		final BucketProps s3BucketProps = BucketProps.builder().encryption(BucketEncryption.S3_MANAGED).versioned(true)
				// .lifecycleRules(lifecycleRules) lets add this later so old files are cleared
				// after defined time
				.build();

		Bucket landingBucket = new Bucket(this, "velholandingbucket", s3BucketProps);
		Bucket workBucket = new Bucket(this, "velhoworkbucket", s3BucketProps);

		final Function evenPasserLambda = Function.Builder.create(this, "EventPasser")
				.functionName("VelhoLandingBucketEventPasser").timeout(Duration.minutes(5))
				.code(Code.fromAsset("lambdas" + File.separator + "eventpasser" + File.separator + "target"
						+ File.separator + "lambda-java-evenpasser-1.0-SNAPSHOT.jar"))
				.runtime(software.amazon.awscdk.services.lambda.Runtime.JAVA_8)
				.handler("com.vayla.Lambdas.eventpasser.EventPass").build();

		// Metadataloderin ymparistomuuttujat
		Map<String, String> environment = new HashMap<String, String>();
		environment.put("velhoHost", "api.stg.velho.vayla.fi");
		environment.put("workBucket", workBucket.getBucketName());


		final Function getMetadataLambda = Function.Builder.create(this, "MetadataLoaderLambda")
				.functionName("VelhoMetadataLoader").timeout(Duration.minutes(5)).memorySize(1024)
				.code(Code.fromAsset("lambdas" + File.separator + "velhometadata" + File.separator + "target"
						+ File.separator + "velho.metadata-1.0.0.jar"))
				.runtime(software.amazon.awscdk.services.lambda.Runtime.JAVA_8).environment(environment)
				.handler("com.vayla.lambda.velho.metadata.LambdaFunctionHandler").build();
		// lisataan kayttooikeudet workbuckettiin
		// TODO: korjattava rajoitetummaksi, nyt on kaikki oikeudet kaikkiin bucketteihin
		getMetadataLambda
				.addToRolePolicy(PolicyStatement.Builder.create()
						.effect(Effect.ALLOW).actions(Arrays.asList("s3:*"))
						// .resources(Arrays.asList(workBucket.getBucketArn()))
						.resources(Arrays.asList("*")).build());

		
		// Dataloader ymparistomuuttujat
		// eli pelkka workbucket, jos / kun raakadatan sijainti tulee
		// triggerin s3eventissa
		environment = new HashMap<String, String>();
		environment.put("workbucket", workBucket.getBucketName());
		environment.put("debug", "false");
		
		final Function velhoDataLoderLambda = Function.Builder.create(this, "DataLoaderLambda")
				.functionName("VelhoDataLoader").timeout(Duration.minutes(5)).memorySize(1024)
				.code(Code.fromAsset("lambdas" + File.separator + "velhodataloader" + File.separator + "target"
						+ File.separator + "velho.dataloader-1.0.0.jar"))
				.runtime(software.amazon.awscdk.services.lambda.Runtime.JAVA_8).environment(environment)
				.handler("com.vayla.lambda.velho.dataloader.LambdaFunctionHandler").build();

		// lisataan kayttooikeudet kaikkiin bucketteihin
		// todo: rajatut bucketit
		velhoDataLoderLambda
				.addToRolePolicy(PolicyStatement.Builder.create()
						.effect(Effect.ALLOW)
						.actions(Arrays.asList("s3:*"))
						.resources(Arrays.asList("*")).build());
		
		
		// Collector (raakadan haku) ymparistomuuttujat
		environment = new HashMap<String, String>();
		environment.put("velhodataurl", "http://latauspalvelu.stg.velho.vayla.fi/viimeisin/varustetiedot/kaiteet.json"); //velho latauspalvelu
		environment.put("landingbucket", landingBucket.getBucketName()); // raakadatan landing bucket, josta triggerit kasittely lambdoihin
		
		final Function velhoCollectorLambda = Function.Builder.create(this, "CollectorLambda")
				.functionName("VelhoCollector").timeout(Duration.minutes(5)).memorySize(1024)
				.code(Code.fromAsset("lambdas" + File.separator + "velhocollector" + File.separator + "target"
						+ File.separator + "velho.collector-1.0.0.jar"))
				.runtime(software.amazon.awscdk.services.lambda.Runtime.JAVA_8).environment(environment)
				.handler("com.vayla.lambda.velho.dataloader.LambdaFunctionHandler").build();
		
		// lisataan kayttooikeudet kaikkiin bucketteihin
		// todo: rajatut bucketit
		velhoCollectorLambda
				.addToRolePolicy(PolicyStatement.Builder.create()
						.effect(Effect.ALLOW)
						.actions(Arrays.asList("s3:*"))
						.resources(Arrays.asList("*")).build());

		// viestinvalitys
		final Queue queue = Queue.Builder.create(this, "VelhoAnalyticsQueue").visibilityTimeout(Duration.seconds(300))
				.build();

		final Topic topic = Topic.Builder.create(this, "VelhoAnalyticsTopic").displayName("DQL for Stepfunctions alert")
				.build();

		NotificationKeyFilter ntfilter = NotificationKeyFilter.builder().prefix("/*").build();

		/**
		 * Here we create notification to trigger lambda when any file is added to
		 * bucket
		 */
		landingBucket.addEventNotification(software.amazon.awscdk.services.s3.EventType.OBJECT_CREATED_PUT,
				new LambdaDestination(velhoDataLoderLambda), ntfilter);

		// topic.addSubscription(new EmailSubscription("")); /** */

	}

}
