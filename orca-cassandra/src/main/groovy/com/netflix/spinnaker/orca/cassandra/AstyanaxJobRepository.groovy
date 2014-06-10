package com.netflix.spinnaker.orca.cassandra

import com.netflix.astyanax.Keyspace
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobInstance
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.repository.JobRestartException

@CompileStatic
@TupleConstructor(includeFields = true)
class AstyanaxJobRepository implements JobRepository {

    private final Keyspace keyspace

    @Override
    boolean isJobInstanceExists(String jobName, JobParameters jobParameters) {
        return false
    }

    @Override
    JobInstance createJobInstance(String jobName, JobParameters jobParameters) {
        return null
    }

    @Override
    JobExecution createJobExecution(JobInstance jobInstance, JobParameters jobParameters, String jobConfigurationLocation) {
        return null
    }

    @Override
    JobExecution createJobExecution(String jobName, JobParameters jobParameters) throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException {
        return null
    }

    @Override
    void update(JobExecution jobExecution) {

    }

    @Override
    void add(StepExecution stepExecution) {

    }

    @Override
    void addAll(Collection<StepExecution> stepExecutions) {

    }

    @Override
    void update(StepExecution stepExecution) {

    }

    @Override
    void updateExecutionContext(StepExecution stepExecution) {

    }

    @Override
    void updateExecutionContext(JobExecution jobExecution) {

    }

    @Override
    StepExecution getLastStepExecution(JobInstance jobInstance, String stepName) {
        return null
    }

    @Override
    int getStepExecutionCount(JobInstance jobInstance, String stepName) {
        return 0
    }

    @Override
    JobExecution getLastJobExecution(String jobName, JobParameters jobParameters) {
        return null
    }
}
