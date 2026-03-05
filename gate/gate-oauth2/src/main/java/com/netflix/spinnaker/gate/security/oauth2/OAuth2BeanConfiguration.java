package com.netflix.spinnaker.gate.security.oauth2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;

@Configuration
@Conditional(OAuthConfigEnabled.class)
public class OAuth2BeanConfiguration {
  @Bean
  public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
      tokenResponseClient() {
    return new DefaultAuthorizationCodeTokenResponseClient();
  }
}
