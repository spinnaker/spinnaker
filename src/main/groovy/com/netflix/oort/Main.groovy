package com.netflix.oort

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@EnableAutoConfiguration
@Configuration
@ComponentScan
@EnableScheduling
class Main {

  @Value('${Aws.AccessId}') String accessId
  @Value('${Aws.SecretKey}') String secretKey

  static void main(_) {
    SpringApplication.run this, [] as String[]
  }

  @Bean
  AmazonSimpleDB amazonSimpleDB() {
    new AmazonSimpleDBClient(new BasicAWSCredentials(accessId, secretKey))
  }
}
