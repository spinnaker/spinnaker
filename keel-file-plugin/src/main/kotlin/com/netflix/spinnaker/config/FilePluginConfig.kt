package com.netflix.spinnaker.config

import com.netflix.spinnaker.keel.plugin.CustomResourceDefinitionLocator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FilePluginConfig {

  @Bean
  fun messageCRDLocator() =
    object : CustomResourceDefinitionLocator {
      override fun locate() =
        javaClass.getResourceAsStream("/message.yml").bufferedReader()
    }

}
