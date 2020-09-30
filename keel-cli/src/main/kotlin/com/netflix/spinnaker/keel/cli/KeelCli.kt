package com.netflix.spinnaker.keel.cli

import com.netflix.spinnaker.keel.cli.commands.DataGenCommand
import kotlinx.cli.ArgParser
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.WebApplicationType.NONE
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.core.env.Environment
import java.lang.annotation.ElementType.TYPE
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy.RUNTIME
import java.lang.annotation.Target
import kotlin.system.exitProcess


private val DEFAULT_PROPS = mapOf(
  "netflix.environment" to "test",
  "netflix.account" to "\${netflix.environment}",
  "netflix.stack" to "test",
  "spring.config.additional-location" to "\${user.home}/.spinnaker/",
  "spring.application.name" to "keel-cli",
  "spring.config.name" to "spinnaker,keel",
  "spring.profiles.active" to "\${netflix.environment},local,cli",
  // TODO: not sure why we need this when it should get loaded from application.properties
  "spring.main.allow-bean-definition-overriding" to "true",
  "spring.groovy.template.check-template-location" to "false"
)

/**
 * A simple Spring Boot console application providing CLI access to Keel.
 */
@KeelCliComponentScan
@SpringBootApplication
class KeelCli(
  val springEnv: Environment,
  val dataGenCommand: DataGenCommand
) : CommandLineRunner {
  override fun run(args: Array<String>) {
    if (springEnv.activeProfiles.none { it == "local" }) {
      println("You must enable the 'local' Spring profile to run this tool.")
      return
    }

    val parser = ArgParser("keel-cli")
    parser.subcommands(dataGenCommand)
    parser.parse(args)
  }
}

@Retention(RUNTIME)
@Target(TYPE)
@ComponentScan(
  basePackages = [
    "com.netflix.spinnaker.config",
    "com.netflix.spinnaker.keel"
  ],
  excludeFilters = [ComponentScan.Filter(
    type = FilterType.REGEX,
    pattern = [
      "com.netflix.spinnaker.config.DefaultServiceClientProvider",
      "com.netflix.spinnaker.config.MetricsEndpointConfiguration",
      "com.netflix.spinnaker.keel.artifacts.ArtifactListener"
    ]
  )]
)
annotation class KeelCliComponentScan

fun main(args: Array<String>) {
  SpringApplicationBuilder(KeelCli::class.java)
    .properties(DEFAULT_PROPS)
    .web(NONE)
    .run(*args)
}