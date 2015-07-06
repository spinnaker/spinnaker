package com.netflix.spinnaker.gate.security.anonymous

import com.netflix.spinnaker.gate.security.WebSecurityAugmentor
import com.netflix.spinnaker.security.User
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AnonymousAuthenticationProvider
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter

@ConditionalOnExpression('${anonymous.enabled:false}')
@Configuration
@ConfigurationProperties(prefix = "anonymous")
class AnonymousSecurityConfig implements WebSecurityAugmentor {
  String key = "spinnaker-anonymous"
  Collection<String> allowedAccounts = []

  @Override
  void configure(HttpSecurity http,
                 UserDetailsService userDetailsService,
                 AuthenticationManager authenticationManager) {
    def filter = new AnonymousAuthenticationFilter(
      key, new User("anonymous", null, null, ["anonymous"], allowedAccounts), [new SimpleGrantedAuthority("anonymous")]
    )
    http.addFilter(filter)
    http.csrf().disable()
  }

  @Override
  void configure(AuthenticationManagerBuilder auth) {
    auth.authenticationProvider(new AnonymousAuthenticationProvider(key))
  }
}
