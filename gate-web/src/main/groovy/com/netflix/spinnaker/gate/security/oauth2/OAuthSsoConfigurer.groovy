package com.netflix.spinnaker.gate.security.oauth2;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

interface OAuthSsoConfigurer {
  void configure(HttpSecurity http) throws Exception
}
