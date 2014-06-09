package com.netflix.spinnaker.orca

import org.springframework.batch.core.BatchStatus
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@ContextConfiguration(classes = [SimpleJobConfiguration])
class CassandraBackedRepositorySpec extends Specification {

    @Autowired
    JobLauncherTestUtils jobLauncherTestUtils

    def "can run a job using a Cassandra backed job repository"() {
        when:
        def jobStatus = jobLauncherTestUtils.launchJob().status

        then:
        jobStatus == BatchStatus.COMPLETED
    }
}
