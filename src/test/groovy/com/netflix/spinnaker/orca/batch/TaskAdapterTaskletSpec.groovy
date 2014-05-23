package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.repeat.RepeatStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution

class TaskAdapterTaskletSpec extends Specification {

    def step = Mock(Task)

    @Subject
    def tasklet = new TaskAdapterTasklet(step)

    def stepExecution = createStepExecution()
    def stepContext = new StepContext(stepExecution)
    def stepContribution = new StepContribution(stepExecution)
    def chunkContext = new ChunkContext(stepContext)

    def "should invoke the step when executed"() {
        when:
        tasklet.execute(stepContribution, chunkContext)

        then:
        1 * step.execute(*_) >> new TaskResult(status: TaskResult.Status.SUCCEEDED)
    }

    @Unroll("should convert a result of #taskResultStatus to repeat status #repeatStatus and exitStatus #exitStatus")
    def "should convert step return status to equivalent batch status"() {
        given:
        step.execute(*_) >> new TaskResult(status: taskResultStatus)

        expect:
        tasklet.execute(stepContribution, chunkContext) == repeatStatus

        and:
        stepContribution.exitStatus == exitStatus

        where:
        taskResultStatus            | repeatStatus             | exitStatus
        TaskResult.Status.SUCCEEDED | RepeatStatus.FINISHED    | ExitStatus.COMPLETED
        TaskResult.Status.FAILED    | RepeatStatus.FINISHED    | ExitStatus.FAILED
        TaskResult.Status.RUNNING   | RepeatStatus.CONTINUABLE | ExitStatus.EXECUTING
    }

    @Unroll
    def "should write any task outputs to the step context if the task status is #taskStatus"() {
        given:
        def taskResult = new TaskResult(status: taskStatus)
        taskResult.outputs.putAll(outputs)
        step.execute(*_) >> taskResult

        when:
        tasklet.execute(stepContribution, chunkContext)

        then:
        stepContext.stepExecutionContext == outputs
        stepContext.jobExecutionContext.isEmpty()

        where:
        taskStatus << [TaskResult.Status.RUNNING]
        outputs = [foo: "bar", baz: "qux"]
    }

    @Unroll
    def "should write any task outputs to the job context if the task status is #taskStatus"() {
        given:
        def taskResult = new TaskResult(status: taskStatus)
        taskResult.outputs.putAll(outputs)
        step.execute(*_) >> taskResult

        when:
        tasklet.execute(stepContribution, chunkContext)

        then:
        stepContext.stepExecutionContext.isEmpty()
        stepContext.jobExecutionContext == outputs

        where:
        taskStatus << [TaskResult.Status.FAILED, TaskResult.Status.SUCCEEDED]
        outputs = [foo: "bar", baz: "qux"]
    }

}
