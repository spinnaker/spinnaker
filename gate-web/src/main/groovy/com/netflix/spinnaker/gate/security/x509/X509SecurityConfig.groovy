package com.netflix.spinnaker.gate.security.x509

import com.netflix.spinnaker.gate.security.WebSecurityAugmentor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter

@Configuration
@EnableConfigurationProperties
class X509SecurityConfig implements WebSecurityAugmentor {
  @Override
  void configure(HttpSecurity http,
                 UserDetailsService userDetailsService,
                 AuthenticationManager authenticationManager) {
    def filter = new X509AuthenticationFilter()
    filter.setAuthenticationManager(authenticationManager)
    http.addFilter(filter)
  }

  @Override
  void configure(AuthenticationManagerBuilder auth) {
    auth.authenticationProvider(new X509AuthenticationProvider())
  }
}
