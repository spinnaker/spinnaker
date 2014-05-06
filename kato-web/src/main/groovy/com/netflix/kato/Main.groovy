package com.netflix.kato

import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.appinfo.InstanceInfo
import com.netflix.kato.data.task.InMemoryTaskRepository
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.security.DefaultNamedAccountCredentialsHolder
import com.netflix.kato.security.NamedAccountCredentialsHolder
import com.netflix.kato.security.aws.AmazonNamedAccountCredentials
import groovy.util.logging.Log4j
import javax.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.*

@Log4j
@Configuration
@ComponentScan("com.netflix.kato")
@EnableAutoConfiguration
class Main extends SpringBootServletInitializer {

  @Autowired
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  @Autowired
  AWSCredentialsProvider awsCredentialsProvider

  @PostConstruct
  void addCredentials() {
    namedAccountCredentialsHolder.put "test", new AmazonNamedAccountCredentials(awsCredentialsProvider, "test")
  }

  static void main(_) {
    SpringApplication.run this, [] as String[]
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    application.sources Main
  }

  @Bean
  TaskRepository taskRepository() {
    new InMemoryTaskRepository()
  }

  @Bean
  NamedAccountCredentialsHolder namedAccountCredentialsHolder() {
    new DefaultNamedAccountCredentialsHolder()
  }

  @Bean
  InstanceInfo.InstanceStatus instanceStatus() {
    InstanceInfo.InstanceStatus.UNKNOWN
  }
}
