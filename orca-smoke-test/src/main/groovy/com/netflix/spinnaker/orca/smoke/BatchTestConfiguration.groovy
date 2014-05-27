package com.netflix.spinnaker.orca.smoke

import com.jolbox.bonecp.BoneCPDataSource
import groovy.transform.CompileStatic
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment
import org.springframework.core.io.ResourceLoader
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

import javax.sql.DataSource

@Configuration
@EnableBatchProcessing
@PropertySource("classpath:batch.properties")
@CompileStatic
class BatchTestConfiguration {

    @Autowired
    private Environment env

    @Autowired
    private ResourceLoader resourceLoader

    @Bean(destroyMethod = "close")
    DataSource dataSource() {
        def ds = new BoneCPDataSource(
            driverClass: env.getProperty("batch.jdbc.driver"),
            jdbcUrl: env.getProperty("batch.jdbc.url"),
            username: env.getProperty("batch.jdbc.user"),
            password: env.getProperty("batch.jdbc.password")
        )

        def populator = new ResourceDatabasePopulator()
        populator.addScript(resourceLoader.getResource(env.getProperty('batch.schema.script')))
        DatabasePopulatorUtils.execute(populator, ds)

        return ds
    }
}
