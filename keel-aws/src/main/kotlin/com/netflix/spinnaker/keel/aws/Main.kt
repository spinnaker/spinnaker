package com.netflix.spinnaker.keel.aws

import com.netflix.spinnaker.keel.plugin.PluginProperties
import com.netflix.spinnaker.kork.PlatformComponents
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import

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
@ComponentScan(basePackages = [
  "com.netflix.spinnaker.config",
  "com.netflix.spinnaker.keel"
])
@Import(PlatformComponents::class)
@EnableConfigurationProperties(PluginProperties::class)
class AmazonAssetPluginApp

fun main(vararg args: String) {
  SpringApplicationBuilder()
    .properties(MainDefaults.PROPS)
    .sources<AmazonAssetPluginApp>()
    .run(*args)
}

inline fun <reified T> SpringApplicationBuilder.sources(): SpringApplicationBuilder =
  sources(T::class.java)
