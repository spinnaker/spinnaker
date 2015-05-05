package com.netflix.spinnaker.gate.security

import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.UserDetailsService

interface WebSecurityAugmentor {
  void configure(HttpSecurity http, UserDetailsService userDetailsService, AuthenticationManager authenticationManager)
  void configure(AuthenticationManagerBuilder authenticationManagerBuilder)
}
