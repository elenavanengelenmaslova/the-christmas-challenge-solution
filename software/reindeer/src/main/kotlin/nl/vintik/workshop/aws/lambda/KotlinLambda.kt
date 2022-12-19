package nl.vintik.workshop.aws.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import nl.vintik.workshop.aws.lambda.model.EventBridgeMessage
import nl.vintik.workshop.aws.lambda.model.Reindeer
import nl.vintik.workshop.aws.lambda.model.Reindeer.Companion.reindeerTable
import org.slf4j.LoggerFactory

@Suppress("UNUSED")
class KotlinLambda : RequestHandler<EventBridgeMessage<Reindeer>, String> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun handleRequest(event: EventBridgeMessage<Reindeer>, context: Context): String {
        //if payload is present, add or replace it in the Reindeer table (put)
        event.detail?.let {
            reindeerTable.putItem(it)
            logger.info("Put item $it")
        }
        return "Merry Christmas!"
    }
}