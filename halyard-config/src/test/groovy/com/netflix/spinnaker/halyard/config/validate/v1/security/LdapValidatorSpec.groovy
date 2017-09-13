package com.netflix.spinnaker.halyard.config.validate.v1.security

import com.netflix.spinnaker.halyard.config.model.v1.security.Ldap
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder
import com.netflix.spinnaker.halyard.core.problem.v1.Problem
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet
import spock.lang.Specification
import spock.lang.Unroll

class LdapValidatorSpec extends Specification {

  LdapValidator validator
  ConfigProblemSetBuilder problemSetBuilder

  void setup() {
    problemSetBuilder = new ConfigProblemSetBuilder()
    validator = new LdapValidator()
  }

  @Unroll
  void "valid case: #description"() {
    setup:
    def ldap = new Ldap(userDnPattern: userDnPattern, userSearchBase: userSearchBase, userSearchFilter: userSearchFilter)
    ldap.url = ldapUrl ? new URI(ldapUrl) : null
    ldap.enabled = enabled

    when:
    validator.validate(problemSetBuilder, ldap)
    ProblemSet problemSet = problemSetBuilder.build()

    then:
    problemSet.empty

    where:
    description               | enabled | ldapUrl                       | userDnPattern  | userSearchBase | userSearchFilter
    "not enabled"             | false   | null                          | null           | null           | null
    "minimal set"             | true    | "ldaps://ldap.some.com:123"   | "some pattern" | null           | null
    "all properties set"      | true    | "ldaps://ldap.some.com:123"   | "some pattern" | "sub"          | "ou=foo"
    "plain LDAP"              | true    | "ldap://ldap.some.com:123"    | "some pattern" | "sub"          | "ou=foo"
  }

  @Unroll
  void "invalid case: #description"() {
    setup:
    def ldap = new Ldap(userDnPattern: userDnPattern, userSearchBase: userSearchBase, userSearchFilter: userSearchFilter)
    ldap.url = ldapUrl ? new URI(ldapUrl) : null
    ldap.enabled = true

    when:
    validator.validate(problemSetBuilder, ldap)
    ProblemSet problemSet = problemSetBuilder.build()

    then:
    ! problemSet.empty
    problemSet.problems.size() == 1
    problemSet.problems[0].severity == Problem.Severity.ERROR
    problemSet.problems[0].message.toLowerCase().contains(errorMessageMatches)

    where:
    description               | ldapUrl                     | userDnPattern  | userSearchBase | userSearchFilter || errorMessageMatches
    "missing user dn pattern" | "ldaps://ldap.some.com:123" | null           | "sub"          | "ou=foo"         || "user dn pattern"
    "missing ldap url"        | null                        | "some pattern" | "sub"          | "ou=foo"         || "ldap url"
    "invalid url"             | "not_a_real_url"            | "some pattern" | "sub"          | "ou=foo"         || "ldap url"
    "wrong url protocol"      | "https://ldap.some.com:123" | "some pattern" | "sub"          | "ou=foo"         || "ldap url"
    "missing port number"     | "ldaps://ldap.some.com"     | "some pattern" | "sub"          | "ou=foo"         || "ldap url"
    "search base empty"       | "ldaps://ldap.some.com:123" | "some pattern" | ""             | "ou=foo"         || "ldap user search base"
    "search filter empty"     | "ldaps://ldap.some.com:123" | "some pattern" | "sub"          | ""               || "ldap user search filter"
  }
}
