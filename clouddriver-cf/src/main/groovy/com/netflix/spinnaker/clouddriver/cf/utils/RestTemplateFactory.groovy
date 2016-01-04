package com.netflix.spinnaker.clouddriver.cf.utils

import org.springframework.web.client.RestTemplate

/**
 * @author Greg Turnquist
 */
interface RestTemplateFactory {

  RestTemplate createRestTemplate()

}