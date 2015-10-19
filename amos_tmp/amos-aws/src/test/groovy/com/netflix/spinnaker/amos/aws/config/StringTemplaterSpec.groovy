package com.netflix.spinnaker.amos.aws.config

import spock.lang.Specification

class StringTemplaterSpec extends Specification {

    def 'it should work'(String template, Map<String, String> params, String expected) {
        expect:
        CredentialsLoader.StringTemplater.render(template, params) == expected

        where:
        template                                      | params                              || expected
        'foo'                                         | [:]                                 || 'foo'
        'http://{{region}}.{{name}}.netflix.net:7001' | [region: 'us-east-1', name: 'prod'] || 'http://us-east-1.prod.netflix.net:7001'
    }
}
