package com.netflix.spinnaker.orca.smoke

import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.bakery.job.BakeJobBuilder
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.IgnoreIf
import spock.lang.Specification
import static com.netflix.spinnaker.orca.test.Network.isReachable
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS

@IgnoreIf({ !isReachable("http://bakery.test.netflix.net:7001") })
@ContextConfiguration(classes = [BakeryConfiguration, BatchTestConfiguration])
@DirtiesContext(classMode = AFTER_CLASS)
class OrcaSmokeSpec extends Specification {

    @Autowired
    JobLauncher jobLauncher

    @Autowired
    BakeJobBuilder bakeJobBuilder

    @Autowired
    JobBuilderFactory jobs

    def "can bake and monitor to completion"() {
        given:
        def jobBuilder = jobs.get("${getClass().simpleName}Job")
        def job = bakeJobBuilder.build(jobBuilder).build()

        and:
        def jobParameters = new JobParametersBuilder()
            .addString("region", "us-west-1")
            .addString("bake.user", "rfletcher")
            .addString("bake.package", "oort")
            .addString("bake.baseOs", "ubuntu")
            .addString("bake.baseLabel", "release")
            .toJobParameters()

        when:
        def jobStatus = jobLauncher.run(job, jobParameters).status

        then:
        jobStatus == BatchStatus.COMPLETED
    }

}

