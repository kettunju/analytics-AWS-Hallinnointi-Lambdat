package com.vayla;

//import com.sun.tools.javac.util.Dependencies;
import java.io.File;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.s3.*;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.stepfunctions.Activity;
import software.amazon.awscdk.services.stepfunctions.Chain;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.Task;
import software.amazon.awscdk.services.stepfunctions.tasks.InvokeActivity;
import software.amazon.awscdk.services.s3.notifications.*;

public class VelhoAnalyticsStack extends Stack {
        public VelhoAnalyticsStack(final Construct parent, final String id) {
                this(parent, id, null);
        }

        public VelhoAnalyticsStack(final Construct parent, final String id, final StackProps props) {
                super(parent, id, props);

                final BucketProps s3BucketProps = BucketProps.builder().encryption(BucketEncryption.S3_MANAGED)
                                .versioned(true)
                                // .lifecycleRules(lifecycleRules) lets add this later so old files are cleared
                                // after defined time
                                .build();

                Bucket landingBucket = new Bucket(this, "velholandingbucket", s3BucketProps);

                final Function evenPasserLambda = Function.Builder.create(this, "EventPasser")
                                .functionName("VelhoLandingBucketEventPasser").timeout(Duration.minutes(5))
                                .code(Code.fromAsset("lambdas" + File.separator + "eventpasser" + File.separator
                                                + "target" + File.separator
                                                + "lambda-java-evenpasser-1.0-SNAPSHOT.jar"))
                                .runtime(software.amazon.awscdk.services.lambda.Runtime.JAVA_8)
                                .handler("com.vayla.Lambdas.eventpasser.EventPass").build();

                final Function getMetadataLambda = Function.Builder.create(this, "MetadataLoaderLambda")
                                .functionName("VelhoMetadataLoader").timeout(Duration.minutes(5))
                                .code(Code.fromAsset("lambdas" + File.separator + "velhometadata" + File.separator
                                                + "target" + File.separator + "velho.metadata-1.0.0.jar"))
                                .runtime(software.amazon.awscdk.services.lambda.Runtime.JAVA_8)
                                .handler("com.vayla.lambda.velho.metadata.LambdaFunctionHandler").build();

                final Queue queue = Queue.Builder.create(this, "VelhoAnalyticsQueue")
                                .visibilityTimeout(Duration.seconds(300)).build();

                final Topic topic = Topic.Builder.create(this, "VelhoAnalyticsTopic")
                                .displayName("DQL for Stepfunctions alert").build();

                NotificationKeyFilter ntfilter = NotificationKeyFilter.builder().prefix("/*").build();

                /**
                 * Here we create notification to trigger lambda when any file is added to
                 * bucket
                 */
                landingBucket.addEventNotification(software.amazon.awscdk.services.s3.EventType.OBJECT_CREATED_PUT,
                                new LambdaDestination(evenPasserLambda), ntfilter);

                // topic.addSubscription(new EmailSubscription("")); /** */

                //////// Step function
                try {

                        Activity submitJobActivity = Activity.Builder.create(this, "SubmitJob").build();

                        Task submitJob = Task.Builder.create(this, "Submit Job")
                                        .task(InvokeActivity.Builder.create(submitJobActivity).build())
                                        .resultPath("$.guid").build();

                        // Task to invoke lambda (InvokeFunction) that will get metadata from Velho
                        // REST/API
                        Task getMetadataTask = Task.Builder.create(this, "MetadataLoderTask")
                                        // .task(InvokeFunction.Builder.create(getMetadataLambda).build())
                                        // TODO this has to be fixed
                                        .task(InvokeActivity.Builder.create(submitJobActivity).build())
                                        .resultPath("$.guid").build();

                        Task convertToADE = Task.Builder.create(this, "convertToAde")
                                        .task(InvokeActivity.Builder.create(submitJobActivity).build())
                                        .resultPath("$.guid").build();

                        Task createManifest = Task.Builder.create(this, "Create Manifest")
                                        .task(InvokeActivity.Builder.create(submitJobActivity).build())
                                        .resultPath("$.guid").build();

                        Task getStatus = Task.Builder.create(this, "NotYetUsederror checking")
                                        .task(InvokeActivity.Builder.create(submitJobActivity).build())
                                        .inputPath("$.guid").resultPath("$.status").build();

                        // Choice isComplete = Choice.Builder.create(this, "Job Complete?").build();
                        /*
                         * Fail jobFailed = Fail.Builder.create(this,
                         * "Job Failed").cause("AWS Batch Job Failed")
                         * .error("DescribeJob returned FAILED").build();
                         */

                        Chain chain = Chain.start(submitJob).next(getMetadataTask).next(convertToADE)
                                        .next(createManifest);

                        StateMachine.Builder.create(this, "StateMachine").definition(chain)
                                        .timeout(Duration.seconds(30)).build();

                } catch (Exception e) {
                        e.printStackTrace();
                }

        }

}
