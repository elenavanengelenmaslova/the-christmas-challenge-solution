package nl.vintik.workshop.aws.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import nl.vintik.workshop.aws.lambda.model.Reindeer.Companion.reindeerTable
import org.slf4j.LoggerFactory

@Suppress("UNUSED")
class KotlinLambda : RequestHandler<ScheduledEvent, String> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun handleRequest(event: ScheduledEvent, context: Context): String {
        var count = 0
        reindeerTable.scan().items().subscribe { count++ }.join()
        logger.info("total reindeer: $count")
        return "Scheduled event!"
    }
}