package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.smoke.BatchTestConfiguration
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.configuration.JobFactory
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS

/**
 * A base class for integration specs that configure a {@link Job} programmatically and execute it.
 */
@ContextConfiguration(classes = [BatchTestConfiguration])
@DirtiesContext(classMode = AFTER_CLASS)
abstract class BatchExecutionSpec extends Specification implements JobFactory {

    @Autowired private JobBuilderFactory jobs
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

    /**
     * Launches the configured {@code Job}.
     * @return the execution instance you can use to monitor and control the job.
     */
    final JobExecution launchJob() {
        jobLauncherTestUtils.launchJob()
    }

    /**
     * Re-launches the configured {@code Job} if it has stopped.
     * @return the new execution instance you can use to monitor and control the re-launched job.
     */
    final JobExecution resumeJob(JobExecution jobExecution) {
        def executionId = jobOperator.restart(jobExecution.jobId)
        jobExplorer.getJobExecution(executionId)
    }

    /**
     * Implement this method to configure the job you want to test along with all its steps.
     * @param jobBuilder a builder for the {@code Job} with the name already set.
     * @return the configured {@code Job}.
     */
    protected abstract Job configureJob(JobBuilder jobBuilder)

    @Override
    final Job createJob() {
        configureJob(jobs.get(jobName))
    }

    @Override
    final String getJobName() {
        getClass().simpleName - "Spec" + "Job"
    }
}
