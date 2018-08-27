package com.netflix.spinnaker.keel.grpc

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.ComponentScan

object MainDefaults {
  val PROPS = mapOf(
    "netflix.environment" to "test",
    "netflix.account" to "\${netflix.environment}",
    "netflix.stack" to "test",
    "spring.config.location" to "\${user.home}/.spinnaker/",
    "spring.application.name" to "keel",
    "spring.config.name" to "spinnaker,\${spring.application.name}",
    "spring.profiles.active" to "\${netflix.environment},local"
  )
}

@SpringBootApplication
//@EnableEurekaClient
@ComponentScan(basePackages = [
  "com.netflix.spinnaker.config",
  "com.netflix.spinnaker.keel.api",
  "com.netflix.spinnaker.keel.aws"
])
class KeelGrpcServiceApp {

  private val Any.log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    @JvmStatic
    fun main(vararg args: String) {
      SpringApplicationBuilder()
        .properties(MainDefaults.PROPS)
        .sources<KeelGrpcServiceApp>()
        .run(*args)
//    SpringApplication.run(DemoApp::class.java, args)
    }
  }
}

inline fun <reified T> SpringApplicationBuilder.sources(): SpringApplicationBuilder =
  sources(T::class.java)
