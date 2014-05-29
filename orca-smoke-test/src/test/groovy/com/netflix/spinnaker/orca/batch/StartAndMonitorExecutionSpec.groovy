package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobBuilder

import static com.netflix.spinnaker.orca.TaskResult.Status.RUNNING
import static com.netflix.spinnaker.orca.TaskResult.Status.SUCCEEDED

class StartAndMonitorExecutionSpec extends BatchExecutionSpec {

    def startTask = Stub(Task)
    def monitorTask = Mock(Task)

    def "can start an external service and monitor until completed"() {
        given:
        startTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

        when:
        def jobExecution = launchJob()

        then:
        3 * monitorTask.execute(_) >> new DefaultTaskResult(RUNNING) >> new DefaultTaskResult(RUNNING) >> new DefaultTaskResult(SUCCEEDED)

        and:
        jobExecution.exitStatus == ExitStatus.COMPLETED
    }

    def "abandons monitoring if the monitor task fails"() {
        given:
        startTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

        when:
        def jobExecution = launchJob()

        then:
        2 * monitorTask.execute(_) >> new DefaultTaskResult(RUNNING) >> {
            throw new RuntimeException("something went wrong")
        }

        and:
        jobExecution.exitStatus == ExitStatus.FAILED
    }

    def "does not start monitoring if the start task fails"() {
        given:
        startTask.execute(_) >> { throw new RuntimeException("something went wrong") }

        when:
        def jobExecution = launchJob()

        then:
        0 * monitorTask._

        and:
        jobExecution.exitStatus == ExitStatus.FAILED
    }

    @Override
    protected Job configureJob(JobBuilder jobBuilder) {
        def step1 = steps.get("StartStep")
            .tasklet(TaskTaskletAdapter.decorate(startTask))
            .build()
        def step2 = steps.get("MonitorStep")
            .tasklet(TaskTaskletAdapter.decorate(monitorTask))
            .build()
        jobBuilder.start(step1)
            .next(step2)
            .build()
    }
}
