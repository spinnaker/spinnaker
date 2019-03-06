package com.netflix.spinnaker.keel.redis.spring;

import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

/**
 * Disables security for all endpoints for use in [org.springframework.test.web.servlet.MockMvc]
 * type tests.
 *
 * You're supposed to be able to use [org.springframework.security.test.context.support.WithMockUser]
 * but I couldn't get it to work with our security setup.
 */
@Configuration
@Order(0)
class SecurityDisabledConfiguration : WebSecurityConfigurerAdapter() {
  @Throws(Exception::class)
  override fun configure(http: HttpSecurity) {
    http.csrf().disable()
      .authorizeRequests()
      .antMatchers("/**").permitAll()
      .anyRequest().authenticated()
  }
}
