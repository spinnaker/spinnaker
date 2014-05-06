package com.netflix.kato.config

import com.perforce.p4java.option.UsageOptions
import com.perforce.p4java.server.IOptionsServer
import com.perforce.p4java.server.ServerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PerforceConfiguration {
  @Bean
  IOptionsServer p4server(PerforceProperties perforceProperties) {
    def p4server = ServerFactory.getOptionsServer("p4jrpc://${perforceProperties.host}:${perforceProperties.port}", null,
        new UsageOptions(null).setProgramName(perforceProperties.programName))
    p4server.userName = perforceProperties.userName
    p4server.connect()
    p4server
  }
}
