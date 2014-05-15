package com.netflix.asgard.orca.bakery.tasks

import com.netflix.asgard.orca.bakery.api.BakeService
import com.netflix.asgard.orca.bakery.api.BakeState
import com.netflix.asgard.orca.bakery.api.BakeStatus
import com.netflix.asgard.orca.bakery.tasks.BakeTask
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import spock.lang.Specification
import spock.lang.Subject
import rx.*

import static com.netflix.asgard.orca.bakery.api.BakeState.RUNNING

class BakeTaskSpec extends Specification {

    @Subject
    def task = new BakeTask()

    final region = "us-west-1"
    def jobParameters = new JobParametersBuilder().addString("region", region).toJobParameters()
    def jobExecution = new JobExecution(1, jobParameters)
    def stepExecution = new StepExecution("bakeStep", jobExecution)
    def stepContext = new StepContext(stepExecution)
    def stepContribution = new StepContribution(stepExecution)
    def chunkContext = new ChunkContext(stepContext)

    def setup() {
    }

    def "creates a bake for the correct region"() {
        given:
        def mockBakeService = Mock(BakeService)
        task.bakeService = mockBakeService

        when:
        task.execute(stepContribution, chunkContext)

        then:
        1 * mockBakeService.createBake(region) >> Observable.empty()
    }

    def "stores the status of the bake in the job context"() {
        given:
        def bakeStatus = new BakeStatus(state: RUNNING)
        task.bakeService = Stub(BakeService) {
            createBake(region) >> Observable.from(bakeStatus)
        }

        when:
        task.execute(stepContribution, chunkContext)

        then:
        stepContext.jobExecutionContext["bake.status"] == bakeStatus
    }

}
