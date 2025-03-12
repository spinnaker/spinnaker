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
    def ldap = new Ldap(userDnPattern: userDnPattern, userSearchBase: userSearchBase, userSearchFilter: userSearchFilter, managerDn: managerDn, managerPassword: managerPassword, groupSearchBase: groupSearchBase)
    ldap.url = ldapUrl ? new URI(ldapUrl) : null
    ldap.enabled = enabled

    when:
    validator.validate(problemSetBuilder, ldap)
    ProblemSet problemSet = problemSetBuilder.build()

    then:
    problemSet.empty

    where:
    description                | enabled | ldapUrl                             | userDnPattern  | userSearchBase | userSearchFilter | managerDn | managerPassword | groupSearchBase
    "not enabled"              | false   | null                                | null           | null           | null             | null      | null            | null
    "user DN pattern"          | true    | "ldaps://ldap.some.com:123"         | "some pattern" | null           | null             | null      | null            | null
    "search and filter"        | true    | "ldap://ldap.some.com:123"          | null           | "sub"          | "ou=foo"         | null      | null            | null
    "search and filter"        | true    | "ldap://ldap.some.com:123"          | null           | "sub"          | "ou=foo"         | "admin"   | "secret"        | "ou=company"
    "search and root in url"   | true    | "ldap://ldap.some.com:123/root_dn"  | null           | null           | "ou=foo"         | "admin"   | "secret"        | "ou=company"
    "search and root no mgr"   | true    | "ldap://ldap.some.com:123/root_dn"  | null           | null           | "ou=foo"         | null      | null            | "ou=company"
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
    !problemSet.empty
    problemSet.problems.size() == 1
    problemSet.problems[0].severity == Problem.Severity.ERROR
    problemSet.problems[0].message.toLowerCase().contains(errorMessageMatches)

    where:
    description           | ldapUrl                     | userDnPattern  | userSearchBase | userSearchFilter || errorMessageMatches
    "missing ldap url"    | null                        | "some pattern" | "sub"          | "ou=foo"         || "ldap url"
    "invalid url"         | "not_a_real_url"            | "some pattern" | "sub"          | "ou=foo"         || "ldap url"
    "wrong url protocol"  | "https://ldap.some.com:123" | "some pattern" | "sub"          | "ou=foo"         || "ldap url"
    "missing port number" | "ldaps://ldap.some.com"     | "some pattern" | "sub"          | "ou=foo"         || "ldap url"
    "invalid user method" | "ldaps://ldap.some.com:123" | null           | null           | null             || "user search method"
  }
}
