package com.netflix.spinnaker.gate.config

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

@Configuration
@ComponentScan
public class GateWebConfig extends WebMvcConfigurerAdapter {
  @Autowired
  ExtendedRegistry extendedRegistry

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
      new MetricsInterceptor(
        extendedRegistry, "controller.invocations", ["account", "region"], ["BasicErrorController"]
      )
    )
  }
}
