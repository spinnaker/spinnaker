package com.netflix.kato

import com.netflix.kato.data.task.InMemoryTaskRepository
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.security.DefaultNamedAccountCredentialsHolder
import com.netflix.kato.security.NamedAccountCredentialsHolder
import com.netflix.kato.security.aws.AmazonNamedAccountCredentials
import groovy.util.logging.Log4j
import javax.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.*

@Log4j
@Configuration
@ComponentScan("com.netflix.kato")
@EnableAutoConfiguration
class Main {

  @Value('${Aws.AccessId}') String accessId
  @Value('${Aws.SecretKey}') String secretKey

  @Autowired
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  @PostConstruct
  void addCredentials() {
    namedAccountCredentialsHolder.put "test", new AmazonNamedAccountCredentials(accessId, secretKey, "test")
  }

  static void main(_) {
    SpringApplication.run this, [] as String[]
  }

  @Bean
  TaskRepository taskRepository() {
    new InMemoryTaskRepository()
  }

  @Bean
  NamedAccountCredentialsHolder namedAccountCredentialsHolder() {
    new DefaultNamedAccountCredentialsHolder()
  }
}
