package nl.vintik.workshop.aws.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent
import org.slf4j.LoggerFactory

@Suppress("UNUSED")
class KotlinLambda : RequestHandler<DynamodbEvent, String> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun handleRequest(event: DynamodbEvent, context: Context): String {
        context.logger.log("event records : ${event.records.size}")
        logger.info("Processing event with no records: ${event.records.size}")
        event.records.map {
            logger.info(
                "eventName: ${it.eventName}"
            )
        }
        return "DynamoDB event!"
    }
}