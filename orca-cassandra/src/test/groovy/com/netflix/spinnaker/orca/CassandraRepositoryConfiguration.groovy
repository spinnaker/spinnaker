package com.netflix.spinnaker.orca

import javax.sql.DataSource
import com.jolbox.bonecp.BoneCPDataSource
import groovy.transform.CompileStatic
import org.springframework.batch.core.configuration.ListableJobLocator
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.support.SimpleJobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment
import org.springframework.core.io.ResourceLoader
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

@Configuration
@EnableBatchProcessing
@PropertySource("classpath:batch.properties")
@CompileStatic
class CassandraRepositoryConfiguration {

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
        populator.addScript(resourceLoader.getResource(env.getProperty("batch.schema.script")))
        DatabasePopulatorUtils.execute(populator, ds)

        return ds
    }

    @Bean
    JobExplorerFactoryBean jobExplorerFactoryBean(DataSource dataSource) {
        new JobExplorerFactoryBean(dataSource: dataSource)
    }

    @Bean
    JobOperator jobOperator(JobLauncher jobLauncher, JobRepository jobRepository, JobExplorer jobExplorer, ListableJobLocator jobRegistry) {
        def jobOperator = new SimpleJobOperator()
        jobOperator.jobLauncher = jobLauncher
        jobOperator.jobRepository = jobRepository
        jobOperator.jobExplorer = jobExplorer
        jobOperator.jobRegistry = jobRegistry
        return jobOperator
    }
}
