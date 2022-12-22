package nl.vintik.workshop.aws.infra

import software.amazon.awscdk.Duration
import software.amazon.awscdk.RemovalPolicy
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.appsync.alpha.*
import software.amazon.awscdk.services.dynamodb.*
import software.amazon.awscdk.services.events.EventBus
import software.amazon.awscdk.services.events.EventPattern
import software.amazon.awscdk.services.events.Rule
import software.amazon.awscdk.services.events.Schedule
import software.amazon.awscdk.services.events.targets.LambdaFunction
import software.amazon.awscdk.services.lambda.*
import software.amazon.awscdk.services.lambda.Function
import software.amazon.awscdk.services.lambda.eventsources.DynamoEventSource
import software.amazon.awscdk.services.logs.RetentionDays
import software.constructs.Construct

// https://docs.aws.amazon.com/cdk/api/v2/docs/aws-construct-library.html
class InfrastructureChristmasStack(scope: Construct, id: String, props: StackProps) : Stack(scope, id, props) {
    init {
        //create lambda function
        val functionId = "christmas-lambda"
        val function = Function.Builder.create(this, functionId)
            .description("Kotlin Lambda for Christmas")
            .handler("nl.vintik.workshop.aws.lambda.KotlinLambda::handleRequest")
            .runtime(Runtime.JAVA_11)
            .code(Code.fromAsset("../build/dist/function.zip"))
            .architecture(Architecture.ARM_64)
            .logRetention(RetentionDays.ONE_WEEK)
            .memorySize(512)
            .timeout(Duration.seconds(120))
            .build()

        //Task 3. create event bus
        val eventBus =
            EventBus.Builder.create(this, "eventBus")
                .eventBusName("ChristmasEventBus")
                .build()

        //add rule and configure our lambda as target
        Rule.Builder.create(this, "eventRule")
            .eventBus(eventBus)
            .description("${eventBus.eventBusName} Rule with Lambda target")
            .eventPattern(
                EventPattern.builder()
                    .source(listOf("Santa"))
                    .build()
            )
            .targets(listOf(LambdaFunction(function)))
            .build()

        //Task 4. Add DynamoDB table to store Reindeers
        val tableName = "Reindeer"
        val reindeerTable = Table.Builder.create(this, tableName)
            .tableName(tableName)
            .partitionKey(
                Attribute.builder()
                    .type(AttributeType.STRING)
                    .name("id")
                    .build()
            )
            //Note: for workshop DESTROY setting is good because when we clean up we do not want to retain anything.
            //On production usually one would use RETAIN or SNAPSHOT so that the data is not lost if the stack is deleted.
            .removalPolicy(RemovalPolicy.DESTROY)
            .pointInTimeRecovery(false)
            //Setting to keep ourselves within the free tier
            .billingMode(BillingMode.PROVISIONED)
            .readCapacity(12)
            .writeCapacity(12)
            //Task 6.1. enable DynamoDB stream
            .stream(StreamViewType.NEW_AND_OLD_IMAGES)
            .build()

        reindeerTable.grantWriteData(function)

        //Task 5. GraphQL API to get Reindeers
        val apiName = "ReindeerApi"
        val reindeerApi = GraphqlApi.Builder.create(this, apiName)
            .name(apiName)
            .schema(SchemaFile.fromAsset(this::class.java.getResource("/schemas/reindeer.graphql")!!.path))
            .authorizationConfig(
                AuthorizationConfig.builder()
                    .defaultAuthorization(
                        AuthorizationMode.builder()
                            //API Key is the simplest authorisation option, good enough for our workshop
                            .authorizationType(AuthorizationType.API_KEY).build()
                    ).build()
            ).logConfig(
                LogConfig
                    .builder()
                    .fieldLogLevel(FieldLogLevel.ERROR)
                    .build()
            )
            .build()

        // This is a resolver definition for our GraphQL query
        reindeerApi.addDynamoDbDataSource("getReindeerById", reindeerTable).createResolver(
            "resolveById",
            BaseResolverProps.builder()
                .typeName("Query")
                .fieldName("getReindeerById")
                .requestMappingTemplate(MappingTemplate.dynamoDbGetItem("id", "id"))
                .responseMappingTemplate(MappingTemplate.dynamoDbResultItem())
                .build()
        )

        //Task 6.1. Create lambda to process DynamoDB Stream
        val realTimeReportFunction = Function.Builder.create(this, "real-time-report-lambda")
            .description("Real time report for Christmas")
            .handler("nl.vintik.workshop.aws.lambda.KotlinLambda::handleRequest")
            .runtime(Runtime.JAVA_11)
            .code(Code.fromAsset("../build/dist/real-time-report.zip"))
            .architecture(Architecture.ARM_64)
            .logRetention(RetentionDays.ONE_WEEK)
            .memorySize(512)
            .timeout(Duration.seconds(120))
            .build()

        //https://docs.aws.amazon.com/cdk/api/v2/docs/aws-cdk-lib.aws_lambda_event_sources-readme.html
        realTimeReportFunction.addEventSource(
            DynamoEventSource.Builder
                .create(reindeerTable)
                //process all event data
                .startingPosition(StartingPosition.TRIM_HORIZON)
                .batchSize(1)
                .build()
        )

        //Task 6.2. Create GSI to table
        reindeerTable.addGlobalSecondaryIndex(
            GlobalSecondaryIndexProps.builder()
                .indexName("reindeer-name-index")
                .partitionKey(
                    Attribute.builder()
                        .name("name")
                        .type(AttributeType.STRING)
                        .build()
                )
                .projectionType(ProjectionType.ALL)
                .build()
        )

        // Task 6.2. Add resolver to GraphQl
        reindeerApi.addDynamoDbDataSource("getReindeerByName", reindeerTable).createResolver(
            "resolveByName",
            BaseResolverProps.builder()
                .typeName("Query")
                .fieldName("getReindeerByName")
                .requestMappingTemplate(MappingTemplate.dynamoDbQuery(KeyCondition.ge("name", "name"), "reindeer-name-index"))
                .responseMappingTemplate(MappingTemplate.dynamoDbResultList())
                .build()
        )

        //Task 6.3. Add scheduled report
        val scheduledReportFunction = Function.Builder.create(this, "scheduled-report-lambda")
            .description("Scheduled Report for Christmas Challenge")
            .handler("nl.vintik.workshop.aws.lambda.KotlinLambda::handleRequest")
            .runtime(Runtime.JAVA_11)
            .code(Code.fromAsset("../build/dist/scheduled-report.zip"))
            .architecture(Architecture.ARM_64)
            .logRetention(RetentionDays.ONE_WEEK)
            .memorySize(512)
            .timeout(Duration.seconds(120))
            .build()

        val ruleName = "reportSchedule"
        Rule.Builder.create(this, ruleName)
            .ruleName(ruleName)
            .description("Scheduled for Christmas Challenge")
            .schedule(Schedule.expression("cron(0 * * * ? *)"))
            .targets(listOf(LambdaFunction(scheduledReportFunction)))
            .build()
    }
}