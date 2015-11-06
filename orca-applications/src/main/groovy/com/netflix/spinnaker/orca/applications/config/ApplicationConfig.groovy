package com.netflix.spinnaker.orca.applications.config

import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import groovy.transform.CompileStatic
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import


@Configuration
@Import(RetrofitConfiguration)
@ComponentScan([
  "com.netflix.spinnaker.orca.applications.pipeline",
  "com.netflix.spinnaker.orca.applications.tasks",
  "com.netflix.spinnaker.orca.applications"
])
@CompileStatic
class ApplicationConfig {
}
