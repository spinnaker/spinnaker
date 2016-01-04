package com.netflix.spinnaker.clouddriver.cf.utils

import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

/**
 * @author Greg Turnquist
 */
class DefaultRestTemplateFactory implements RestTemplateFactory {

  @Override
  RestTemplate createRestTemplate() {
    new RestTemplate(requestFactory: new SimpleClientHttpRequestFactory(bufferRequestBody: false))
  }

}
