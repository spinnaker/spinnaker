package com.netflix.spinnaker.internal.config

import com.netflix.spinnaker.gate.security.oauth2.IdentityResourceServerTokenServices
import com.netflix.spinnaker.security.User
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter
import org.springframework.security.oauth2.provider.token.UserAuthenticationConverter
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate

@Slf4j
@CompileStatic
@Configuration
@EnableResourceServer
@ConditionalOnExpression('${oauth2.enabled:false}')
class Oauth2SecurityConfig {
  @Bean
  IdentityResourceServerTokenServices identityResourceServerTokenServices(RestOperations restOperations) {
    def defaultAccessTokenConverter = new DefaultAccessTokenConverter()
    defaultAccessTokenConverter.userTokenConverter = new UserAuthenticationConverter() {
      @Override
      Map<String, ?> convertUserAuthentication(Authentication userAuthentication) {
        return [:]
      }

      @Override
      Authentication extractAuthentication(Map<String, ?> map) {
        def allowedAccounts = (map.scope ?: []).collect { String scope -> scope.replace("spinnaker_", "")}
        def user = new User(map.client_id as String, null, null, [], allowedAccounts)
        return new UsernamePasswordAuthenticationToken(user, "N/A", [])
      }
    }

    return new IdentityResourceServerTokenServices(
      identityServerConfiguration(), restOperations, defaultAccessTokenConverter
    )
  }

  @Bean
  @ConfigurationProperties('oauth2')
  IdentityResourceServerTokenServices.IdentityServerConfiguration identityServerConfiguration() {
    new IdentityResourceServerTokenServices.IdentityServerConfiguration()
  }

  @Bean
  @ConditionalOnMissingBean(RestOperations)
  RestOperations restTemplate() {
    new RestTemplate()
  }
}
