package com.netflix.spinnaker.gate.security.oauth2

import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoRestTemplateFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext
import org.springframework.security.oauth2.client.OAuth2RestTemplate
import spock.lang.Specification
import spock.lang.Subject

import javax.servlet.FilterChain

class ExternalAuthTokenFilterSpec extends Specification {

  def "should ensure Bearer token is forwarded properly"() {
    def request = new MockHttpServletRequest()
    request.addHeader("Authorization", "bearer foo")
    def response = new MockHttpServletResponse()
    def chain = Mock(FilterChain)
    def restTemplateFactory = Mock(UserInfoRestTemplateFactory)
    def restTemplate = Mock(OAuth2RestTemplate)
    def oauth2ClientContext = new DefaultOAuth2ClientContext()

    @Subject ExternalAuthTokenFilter filter = new ExternalAuthTokenFilter(userInfoRestTemplateFactory: restTemplateFactory)

    when:
    filter.doFilter(request, response, chain)

    then:
    chain.doFilter(request, response)
    restTemplateFactory.getUserInfoRestTemplate() >> restTemplate
    restTemplate.getOAuth2ClientContext() >> oauth2ClientContext
    def token = oauth2ClientContext.accessToken
    token.tokenType == "Bearer"
    token.value == "foo"
  }
}
