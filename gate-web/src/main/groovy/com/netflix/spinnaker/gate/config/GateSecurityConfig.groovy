package com.netflix.spinnaker.gate.config

import com.netflix.spinnaker.gate.security.WebSecurityAugmentor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration
import org.springframework.boot.context.embedded.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer

import javax.servlet.Filter

@ConditionalOnExpression('${saml.enabled:false} || ${x509.enabled:false}')
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

  @Bean
  public FilterRegistrationBean securityFilterChain(
    @Qualifier(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME) Filter securityFilter) {
    FilterRegistrationBean registration = new FilterRegistrationBean(securityFilter);
    registration.setOrder(0);
    registration
      .setName(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME);
    return registration;
  }
}
