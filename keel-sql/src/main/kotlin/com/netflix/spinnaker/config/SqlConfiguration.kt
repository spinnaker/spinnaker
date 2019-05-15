package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.sql.SqlArtifactRepository
import com.netflix.spinnaker.keel.sql.SqlResourceRepository
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import org.jooq.DSLContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.Clock

@Configuration
@ConditionalOnProperty("sql.enabled")
@Import(DefaultSqlConfiguration::class)
class SqlConfiguration {
  @Bean
  fun resourceRepository(jooq: DSLContext, clock: Clock, objectMapper: ObjectMapper) =
    SqlResourceRepository(jooq, clock, objectMapper)

  @Bean
  fun artifactRepository(jooq: DSLContext) =
    SqlArtifactRepository(jooq)
}
