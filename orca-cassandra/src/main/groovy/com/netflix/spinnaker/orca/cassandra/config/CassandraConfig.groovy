package com.netflix.spinnaker.orca.cassandra.config

import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException
import com.netflix.spinnaker.kork.astyanax.AstyanaxKeyspaceFactory
import com.netflix.spinnaker.orca.cassandra.AstyanaxJobRepository
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment

@Configuration
@Import(AstyanaxComponents)
class CassandraConfig {

    @Value('${spinnaker.cassandra.cluster}')
    String clusterName

    @Value('${spinnaker.cassandra.keyspace}')
    String keySpaceName

    @Autowired
    Environment environment

    @Autowired(required = false)
    AstyanaxKeyspaceFactory factory

    @Bean
    Keyspace keySpace() throws ConnectionException {
        factory.getKeyspace(clusterName, keySpaceName)
    }

    @Bean
    JobRepository jobRepository(Keyspace keyspace) {
        new AstyanaxJobRepository(keyspace)
    }

}
