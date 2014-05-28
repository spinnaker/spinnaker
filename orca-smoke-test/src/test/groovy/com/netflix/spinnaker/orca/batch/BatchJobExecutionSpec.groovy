package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.smoke.BatchTestConfiguration
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

import static com.netflix.spinnaker.orca.TaskResult.Status.RUNNING
import static com.netflix.spinnaker.orca.TaskResult.Status.SUCCEEDED

@ContextConfiguration(classes = [BatchTestConfiguration])
class BatchJobExecutionSpec extends Specification {

    @Autowired JobLauncher jobLauncher
    @Autowired JobRepository jobRepository
    @Autowired JobBuilderFactory jobs
    @Autowired StepBuilderFactory steps
    def jobLauncherTestUtils = new JobLauncherTestUtils()

    def startTask = Stub(Task)
    def monitorTask = Mock(Task)

    def setup() {
        jobLauncherTestUtils.jobLauncher = jobLauncher
        jobLauncherTestUtils.jobRepository = jobRepository
    }

    def "can start an external service and monitor until completed"() {
        given:
        startTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

        and:
        jobLauncherTestUtils.job = createStartAndMonitorWorkflow(startTask, monitorTask)

        when:
        def jobExecution = jobLauncherTestUtils.launchJob()

        then:
        3 * monitorTask.execute(_) >> new DefaultTaskResult(RUNNING) >> new DefaultTaskResult(RUNNING) >> new DefaultTaskResult(SUCCEEDED)

        and:
        jobExecution.exitStatus == ExitStatus.COMPLETED
    }

    def "abandons monitoring if the monitor task fails"() {
        given:
        startTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

        and:
        jobLauncherTestUtils.job = createStartAndMonitorWorkflow(startTask, monitorTask)

        when:
        def jobExecution = jobLauncherTestUtils.launchJob()

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

        and:
        jobLauncherTestUtils.job = createStartAndMonitorWorkflow(startTask, monitorTask)

        when:
        def jobExecution = jobLauncherTestUtils.launchJob()

        then:
        0 * monitorTask._

        and:
        jobExecution.exitStatus == ExitStatus.FAILED
    }

    private Job createStartAndMonitorWorkflow(Task startTask, Task monitorTask) {
        def step1 = steps.get("StartStep")
            .tasklet(TaskTaskletAdapter.decorate(startTask))
            .build()
        def step2 = steps.get("MonitorStep")
            .tasklet(TaskTaskletAdapter.decorate(monitorTask))
            .build()
        jobs.get("StartAndMonitorJob")
            .start(step1)
            .next(step2)
            .build()
    }
}
