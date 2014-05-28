package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.smoke.BatchTestConfiguration
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.MetaDataInstanceFactory
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

    def setup() {
        jobLauncherTestUtils.jobLauncher = jobLauncher
        jobLauncherTestUtils.jobRepository = jobRepository
    }

    def "can start an external service and monitor until completed"() {
        given:
        def startTask = Stub(Task) {
            execute(_) >> new DefaultTaskResult(SUCCEEDED)
        }
        def monitorTask = Mock(Task)

        and:
        def step1 = steps.get("StartStep")
            .tasklet(TaskTaskletAdapter.decorate(startTask))
            .build()
        def step2 = steps.get("MonitorStep")
            .tasklet(TaskTaskletAdapter.decorate(monitorTask))
            .build()
        jobLauncherTestUtils.job = jobs.get("StartAndMonitorJob")
            .start(step1)
            .next(step2)
            .build()

        when:
        def jobExecution = jobLauncherTestUtils.launchJob()

        then:
        3 * monitorTask.execute(_) >> new DefaultTaskResult(RUNNING) >> new DefaultTaskResult(RUNNING) >> new DefaultTaskResult(SUCCEEDED)

        and:
        jobExecution.exitStatus == ExitStatus.COMPLETED
    }

}
