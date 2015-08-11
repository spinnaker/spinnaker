/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.security.oauth2

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.common.OAuth2AccessToken
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException
import org.springframework.security.oauth2.provider.OAuth2Authentication
import org.springframework.security.oauth2.provider.token.AccessTokenConverter
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestOperations

@Slf4j
@CompileStatic
class IdentityResourceServerTokenServices implements ResourceServerTokenServices {

  @Canonical
  static class IdentityServerConfiguration {
    String accessTokenUri
    String clientId
    String clientSecret
    String grantType
  }

  private final IdentityServerConfiguration identityServerConfiguration
  private final RestOperations restTemplate

  private final AccessTokenConverter accessTokenConverter

  public IdentityResourceServerTokenServices(IdentityServerConfiguration identityServerConfiguration,
                                             RestOperations restTemplate,
                                             AccessTokenConverter accessTokenConverter = new DefaultAccessTokenConverter()) {
    this.identityServerConfiguration = identityServerConfiguration
    this.restTemplate = restTemplate
    this.accessTokenConverter = accessTokenConverter
  }

  @Override
  OAuth2Authentication loadAuthentication(String accessToken) throws AuthenticationException, InvalidTokenException {
    def request = buildRequest(accessToken)

    Map response
    try {
      response = (Map) restTemplate.exchange(identityServerConfiguration.accessTokenUri, HttpMethod.POST, request, Map).getBody()
    } catch (HttpClientErrorException hce) {
      log.debug("Caught http error checking token: $hce.responseBodyAsString")
      throw new InvalidTokenException(accessToken, hce)
    }

    if (response.error) {
      log.debug("Received error checking token: $response")
      throw new InvalidTokenException(accessToken)
    }

    return decodeResponse(response)
  }

  @Override
  OAuth2AccessToken readAccessToken(String accessToken) {
    throw new UnsupportedOperationException("Not supported: read access token")
  }

  private HttpEntity<MultiValueMap<String, String>> buildRequest(String accessToken) {
    def postBody = new LinkedMultiValueMap<String, String>()
    postBody.add('client_id', identityServerConfiguration.clientId)
    postBody.add('client_secret', identityServerConfiguration.clientSecret)
    postBody.add('grant_type', identityServerConfiguration.grantType)
    postBody.add('token', accessToken)

    def headers = new HttpHeaders()
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED)

    return new HttpEntity<MultiValueMap<String,String>>(postBody, headers)
  }

  private OAuth2Authentication decodeResponse(Map<String, Object> response) {
    def scope = (response.scope as String) ?: ''
    response.scope = Arrays.asList(scope.split(' '))
    return accessTokenConverter.extractAuthentication(response)
  }
}
