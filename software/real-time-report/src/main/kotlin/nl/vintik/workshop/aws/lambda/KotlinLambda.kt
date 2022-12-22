package nl.vintik.workshop.aws.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent
import org.slf4j.LoggerFactory

@Suppress("UNUSED")
class KotlinLambda : RequestHandler<DynamodbEvent, String> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun handleRequest(event: DynamodbEvent, context: Context): String {
        event.records.map {
            logger.info(
                "eventName: ${it.eventName}"
            )
            if (it.eventName == "MODIFY") {
                val difference = it.dynamodb.newImage.minus(it.dynamodb.oldImage)
                difference.map { (key, _) ->
                    logger.info(
                        "old: ${it.dynamodb.oldImage[key]} | new: ${it.dynamodb.newImage[key]}"
                    )
                }
            }
        }
        return "DynamoDB event!"
    }
}