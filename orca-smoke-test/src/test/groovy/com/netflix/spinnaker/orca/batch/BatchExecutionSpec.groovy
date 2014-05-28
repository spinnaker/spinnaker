package com.netflix.spinnaker.orca.batch

import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

abstract class BatchExecutionSpec extends Specification {

    @Autowired JobLauncher jobLauncher
    @Autowired JobRepository jobRepository
    @Autowired protected JobBuilderFactory jobs
    @Autowired protected StepBuilderFactory steps
    private jobLauncherTestUtils = new JobLauncherTestUtils()

    def setup() {
        jobLauncherTestUtils.jobLauncher = jobLauncher
        jobLauncherTestUtils.jobRepository = jobRepository
        jobLauncherTestUtils.job = createJob()
    }

    protected JobExecution launchJob() {
        jobLauncherTestUtils.launchJob()
    }

    protected abstract Job createJob()
}
