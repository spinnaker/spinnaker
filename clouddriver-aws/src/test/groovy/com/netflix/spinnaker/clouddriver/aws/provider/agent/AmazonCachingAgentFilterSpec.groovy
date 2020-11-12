package com.netflix.spinnaker.clouddriver.aws.provider.agent

import spock.lang.Shared
import spock.lang.Specification

class AmazonCachingAgentFilterSpec extends Specification {

  @Shared
  AmazonCachingAgentFilter filter = new AmazonCachingAgentFilter()

  void "should retain based on tag criteria"() {
    given:
    filter.includeTags = includeTags
    filter.excludeTags = excludeTags

    when:
    def result = filter.shouldRetainResource(resourceTags)

    then:
    result == expected

    where:
    resourceTags                      | includeTags                     | excludeTags                     | expected
    [resourceTag("hello", "goodbye")] | null                            | null                            | true
    [resourceTag("hello", "goodbye")] | [filterTag("hello")]            | null                            | true
    [resourceTag("hello", "goodbye")] | [filterTag("hello", "goodbye")] | null                            | true
    [resourceTag("hello", "goodbye")] | [filterTag("hello", "goo")]     | null                            | false
    [resourceTag("hello", "goodbye")] | [filterTag("hello", ".*bye")]   | null                            | true
    [resourceTag("hello", "goodbye")] | [filterTag(".*a.*")]            | null                            | false
    [resourceTag("hello", "goodbye")] | null                            | [filterTag("hello")]            | false
    [resourceTag("hello", "goodbye")] | null                            | [filterTag("hello", "goodbye")] | false
    [resourceTag("hello", "goodbye")] | null                            | [filterTag(".*a.*")]            | true
    [resourceTag("hello", "goodbye")] | [filterTag("hello", "goodbye")] | [filterTag("Name")]             | true
    [resourceTag("hello", "goodbye")] | [filterTag("hello", "goodbye")] | [filterTag("hello")]            | false
    [resourceTag("hello", "goodbye")] | [filterTag(".*", "ciao")]       | [filterTag("hello", ".*")]      | false
    [resourceTag("hello", "goodbye"),
     resourceTag("Name", "primary"),] | [filterTag("hello")]            | null                            | true
    [resourceTag("hello", "goodbye"),
     resourceTag("Name", "primary"),] | null                            | [filterTag("hello")]            | false
  }

  private static def resourceTag(String name = null, String value = null) {
    return new AmazonCachingAgentFilter.ResourceTag(name, value)
  }

  private static def filterTag(String name = null, String value = null) {
    return new AmazonCachingAgentFilter.TagFilterOption(name, value)
  }
}
