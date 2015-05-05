package com.netflix.spinnaker.gate.config

import com.netflix.spinnaker.gate.security.WebSecurityAugmentor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

@EnableWebSecurity
@Configuration
@Import(SecurityAutoConfiguration)
class GateSecurityConfig extends WebSecurityConfigurerAdapter {
  @Autowired
  Collection<WebSecurityAugmentor> webSecurityAugmentors

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    webSecurityAugmentors.each {
      it.configure(http, userDetailsService(), authenticationManager())
    }
  }

  @Override
  protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    webSecurityAugmentors.each {
      it.configure(auth)
    }
  }
}
