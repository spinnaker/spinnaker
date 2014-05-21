package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.repeat.RepeatStatus
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static java.util.UUID.randomUUID

class MonitorBakeTaskSpec extends Specification {

    @Subject
    def task = new MonitorBakeTask()

    final region = "us-west-1"
    def jobParameters = new JobParametersBuilder().addString("region", region).toJobParameters()
    def jobExecution = new JobExecution(1, jobParameters)
    def stepExecution = new StepExecution("bakeStep", jobExecution)
    def stepContext = new StepContext(stepExecution)
    def stepContribution = new StepContribution(stepExecution)
    def chunkContext = new ChunkContext(stepContext)

    @Unroll
    def "should return #repeatStatus if bake is #bakeState"() {
        given:
        def previousStatus = new BakeStatus(id: id, state: BakeStatus.State.PENDING)
        jobExecution.executionContext.put("bake.status", previousStatus)

        and:
        task.bakery = Stub(BakeryService) {
            lookupStatus(region, id) >> Observable.from(new BakeStatus(id: id, state: bakeState))
        }

        expect:
        task.execute(stepContribution, chunkContext) == repeatStatus

        where:
        bakeState                  | repeatStatus
        BakeStatus.State.PENDING   | RepeatStatus.CONTINUABLE
        BakeStatus.State.RUNNING   | RepeatStatus.CONTINUABLE
        BakeStatus.State.COMPLETED | RepeatStatus.FINISHED
        BakeStatus.State.CANCELLED | RepeatStatus.FINISHED
        BakeStatus.State.SUSPENDED | RepeatStatus.CONTINUABLE

        id = randomUUID().toString()
    }

    def "should store the updated status in the job context"() {
        given:
        def previousStatus = new BakeStatus(id: id, state: BakeStatus.State.PENDING)
        jobExecution.executionContext.put("bake.status", previousStatus)

        and:
        task.bakery = Stub(BakeryService) {
            lookupStatus(region, id) >> Observable.from(new BakeStatus(id: id, state: BakeStatus.State.COMPLETED))
        }

        when:
        task.execute(stepContribution, chunkContext)

        then:
        with(stepContext.jobExecutionContext["bake.status"]) {
            id == previousStatus.id
            state == BakeStatus.State.COMPLETED
        }

        where:
        id = randomUUID().toString()
    }

}
