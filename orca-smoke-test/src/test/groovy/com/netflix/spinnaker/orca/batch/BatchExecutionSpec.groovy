package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.smoke.BatchTestConfiguration
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.configuration.JobFactory
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@ContextConfiguration(classes = [BatchTestConfiguration])
abstract class BatchExecutionSpec extends Specification implements JobFactory {

    @Autowired protected JobBuilderFactory jobs
    @Autowired protected StepBuilderFactory steps

    @Autowired private JobLauncher jobLauncher
    @Autowired private JobRepository jobRepository
    @Autowired private JobExplorer jobExplorer
    @Autowired private JobOperator jobOperator
    @Autowired private JobRegistry jobRegistry

    private jobLauncherTestUtils = new JobLauncherTestUtils()

    def setup() {
        jobRegistry.register(this)

        jobLauncherTestUtils.jobLauncher = jobLauncher
        jobLauncherTestUtils.jobRepository = jobRepository
        jobLauncherTestUtils.job = createJob()
    }

    def cleanup() {
        jobRegistry.unregister(jobName)
    }

    protected JobExecution launchJob() {
        jobLauncherTestUtils.launchJob()
    }

    protected JobExecution resumeJob(JobExecution jobExecution) {
        def executionId = jobOperator.restart(jobExecution.jobId)
        jobExplorer.getJobExecution(executionId)
    }

    @Override
    String getJobName() {
        getClass().simpleName - "Spec" + "Job"
    }
}
