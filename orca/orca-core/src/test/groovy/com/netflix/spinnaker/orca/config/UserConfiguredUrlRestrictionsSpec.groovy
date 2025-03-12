package com.netflix.spinnaker.orca.config

import spock.lang.Specification
import spock.lang.Unroll

class UserConfiguredUrlRestrictionsSpec extends Specification {

  // Don't try to actually resolve hosts, and control the result of determining whether a host is localhost or link local
  def spyOn(UserConfiguredUrlRestrictions subject, isLocalhost = false, isLinkLocal = false, isValidIpAddress = true) {
    def spy = Spy(subject)
    spy.resolveHost(_) >> null
    spy.isLocalhost(_) >> isLocalhost
    spy.isLinkLocal(_) >> isLinkLocal
    spy.isValidIpAddress(_) >> isValidIpAddress
    spy
  }

  @Unroll
  def 'should verify uri #uri as per restrictions provided'() {
    given:
    UserConfiguredUrlRestrictions config = spyOn(new UserConfiguredUrlRestrictions.Builder()
         .withAllowedHostnamesRegex('^(.+).(.+).com(.*)$')
         .build())

    when:
    URI validatedUri = config.validateURI(uri)

    then:
    noExceptionThrown()
    validatedUri

    where:
    uri << ['https://www.test.com', 'https://foobar.com', 'https://www.test_underscore.com']
  }

  @Unroll
  def 'should verify allowedHostnamesRegex is set'() {
    given:
    UserConfiguredUrlRestrictions config = spyOn(new UserConfiguredUrlRestrictions.Builder()
        .withAllowedHostnamesRegex("")
        .build())

    when:
    config.validateURI(uri)

    then:
    thrown(IllegalArgumentException.class)

    where:
    uri << ['https://www.test.com', 'https://201.152.178.212', 'https://www.test_underscore.com']
  }

  @Unroll
  def 'should exclude common internal URL schemes by default'() {
    given:
    UserConfiguredUrlRestrictions config = spyOn(new UserConfiguredUrlRestrictions.Builder().build())

    when:
    config.validateURI(uri)

    then:
    thrown(IllegalArgumentException.class)

    where:
    uri << [
        'https://orca',
        'https://spin-orca',
        'https://clouddriver.svc.cluster.local',
        'https://echo.internal',
        'https://orca.spinnaker',
        'https://orca/admin',
        'https://orca.spinnaker/admin'
    ]
  }

  @Unroll
  def 'should allow non-internal URLs by default'() {
    given:
    UserConfiguredUrlRestrictions config = spyOn(new UserConfiguredUrlRestrictions.Builder().build())

    when:
    URI validatedUri = config.validateURI(uri)

    then:
    noExceptionThrown()
    validatedUri

    where:
    uri << ['https://google.com', 'https://spinnaker.io/foo/bar', 'https://echo.external', 'http://foo.bar']
  }

  @Unroll
  def 'should reject localhost by default'() {
    given:
    // Note that this does not use the spy, since we want to actually resolve these
    UserConfiguredUrlRestrictions config = new UserConfiguredUrlRestrictions.Builder().build()

    when:
    config.validateURI(uri)

    then:
    thrown(IllegalArgumentException.class)

    where:
    uri << ['https://localhost', 'http://localhost', 'http://127.0.0.1', 'https://::1']
  }

  @Unroll
  def 'should accept localhost when configured, regardless of name filter'() {
    given:
    // Note that this does not use the spy, since we want to actually resolve these
    UserConfiguredUrlRestrictions config = new UserConfiguredUrlRestrictions.Builder()
        .withAllowedHostnamesRegex("this_definitely_doesnt_match_localhost")
        .withRejectLocalhost(false)
        .build()

    when:
    URI validatedUri = config.validateURI(uri)

    then:
    noExceptionThrown()
    validatedUri

    where:
    uri << ['https://localhost', 'http://localhost']
  }

  @Unroll
  def 'rejects verbatim IP addresses by default'() {
    given:
    UserConfiguredUrlRestrictions config = spyOn(new UserConfiguredUrlRestrictions.Builder().build())

    when:
    config.validateURI(uri)

    then:
    thrown(IllegalArgumentException.class)

    where:
    uri << [
        'https://192.168.0.1',
        'http://172.16.0.1',
        'http://10.0.0.1',
        'http://155.155.155.155',
        'https://fd12:3456:789a:1::1',
        'https://[fd12:3456:789a:1::1]',
        'https://[fd12:3456:789a:1::1]:8080'
    ]
  }

  @Unroll
  def 'allows verbatim IP addresses if configured'() {
    given:
    UserConfiguredUrlRestrictions config = spyOn(new UserConfiguredUrlRestrictions.Builder()
        .withRejectVerbatimIps(false)
        .build())

    when:
    URI validatedUri = config.validateURI(uri)

    then:
    noExceptionThrown()
    validatedUri

    where:
    uri << [
        'https://192.168.0.1',
        'http://172.16.0.1',
        'http://10.0.0.1',
        'https://fd12:3456:789a:1::1',
        'https://fc12:3456:789a:1::1',
        'https://[fd12:3456:789a:1::1]:8080',
        'https://[fc12:3456:789a:1::1]:8080'
    ]
  }

  @Unroll
  def 'excludes domains based on env vars (#envVar=#envVal) (#uri)'() {
    given:
    UserConfiguredUrlRestrictions.Builder builder = Spy(new UserConfiguredUrlRestrictions.Builder())
    builder.getEnvValue(envVar) >> envVal
    UserConfiguredUrlRestrictions config = spyOn(builder
        .withExcludedDomainsFromEnvironment(List.of(
            "POD_NAMESPACE",
            "ISTIO_META_MESH_ID"
        ))
        .build())

    when:
    def isValidated = true
    try {
      config.validateURI(uri)
    } catch(IllegalArgumentException ignored) {
      isValidated = false
    }

    then:
    isValidated == shouldValidate
    uri

    where:
    envVar               | envVal          | uri                                    | shouldValidate
    "POD_NAMESPACE"      | "kittens"       | "http://fluffy.kittens"                | false
    "POD_NAMESPACE"      | "puppies"       | "http://fluffy.kittens"                | true
    "ISTIO_META_MESH_ID" | "istio.mesh"    | "http://fluffy.kittens.istio.colander" | true
    "ISTIO_META_MESH_ID" | "istio.mesh"    | "http://fluffy.kittens.istio.mesh"     | false
    "ISTIO_META_MESH_ID" | "istio.mesh"    | "http://fluffy.kittens.istiozmesh"     | true
    "RANDOM_ENV_VAR"     | "kittens"       | "http://fluffy.kittens"                | true
  }

  @Unroll
  def 'excludes based on arbitrary extra patterns'() {
    given:
    UserConfiguredUrlRestrictions config = spyOn(new UserConfiguredUrlRestrictions.Builder()
        // Test patterns that exclude any number and any hyphen
        .withExtraExcludedPatterns(List.of(".+\\d+.+", ".+-+.+"))
        .build())

    when:
    config.validateURI(uri)

    then:
    thrown(IllegalArgumentException.class)

    where:
    uri << [
        "http://asdf2345.com",
        "https://foo-bar.com"
    ]
  }

  @Unroll
  def 'validate normal URLs when arbitrary extra patterns are specified'() {
    given:
    UserConfiguredUrlRestrictions config = spyOn(new UserConfiguredUrlRestrictions.Builder()
        .withExtraExcludedPatterns(List.of("\\d+", "-+"))
        .build())

    when:
    URI validatedUri = config.validateURI(uri)

    then:
    noExceptionThrown()
    validatedUri

    where:
    uri << [
        "http://foobar.com",
        "https://barfoo.com"
    ]
  }
}
