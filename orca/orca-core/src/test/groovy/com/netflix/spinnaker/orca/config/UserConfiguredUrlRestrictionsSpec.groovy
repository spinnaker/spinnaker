package com.netflix.spinnaker.orca.config

import com.google.common.net.InetAddresses
import com.netflix.spinnaker.kork.web.url.UrlRestrictions
import spock.lang.Specification
import spock.lang.Unroll

import java.net.InetAddress

class UserConfiguredUrlRestrictionsSpec extends Specification {

  // Don't try to actually resolve hosts: IP literals resolve to themselves, names to a public address
  UserConfiguredUrlRestrictions withoutDns(UserConfiguredUrlRestrictions.Builder builder) {
    builder.withHostResolver({ String host ->
      [InetAddresses.isInetAddress(host) ? InetAddresses.forString(host) : InetAddresses.forString("93.184.215.14")] as InetAddress[]
    } as UrlRestrictions.HostResolver).build()
  }

  @Unroll
  def 'should verify uri #uri as per restrictions provided'() {
    given:
    UserConfiguredUrlRestrictions config = withoutDns(new UserConfiguredUrlRestrictions.Builder()
         .withAllowedHostnamesRegex('^(.+).(.+).com(.*)$'))

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
    UserConfiguredUrlRestrictions config = withoutDns(new UserConfiguredUrlRestrictions.Builder()
        .withAllowedHostnamesRegex(""))

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
    UserConfiguredUrlRestrictions config = withoutDns(new UserConfiguredUrlRestrictions.Builder())

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
  def 'should block ip ranges if set'() {
    given:
    UserConfiguredUrlRestrictions config = new UserConfiguredUrlRestrictions.Builder().withRejectVerbatimIps(false).withRejectedIps(List.of("192.168.0.0/16", "10.0.0.0/8")).build()

    when:
    config.validateURI(uri)

    then:
    thrown(IllegalArgumentException.class)

    where:
    uri << [
      "https://192.168.16.22",
      "https://10.1.2.3",
//      "http://0a010203.0a010204.rbndr.us"
    ]
  }

  @Unroll
  def 'should allow non-internal URLs by default'() {
    given:
    UserConfiguredUrlRestrictions config = withoutDns(new UserConfiguredUrlRestrictions.Builder())

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
    UserConfiguredUrlRestrictions config = withoutDns(new UserConfiguredUrlRestrictions.Builder())

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
  def 'validate authority bypass is rejected when hostname does not match'() {
    given:
    UserConfiguredUrlRestrictions config = withoutDns(new UserConfiguredUrlRestrictions.Builder().withAllowedHostnamesRegex("example.com"))

    when:
    config.validateURI(uri)

    then:
    thrown(IllegalArgumentException.class)

    where:
    uri << [
        'https://example.com:badpassword@host_with_underscore.com'
    ]
  }
  @Unroll
  def 'validate authority bypass is allowed when hostname matches'() {
    given:
    UserConfiguredUrlRestrictions config = withoutDns(new UserConfiguredUrlRestrictions.Builder().withAllowedHostnamesRegex("host_with_underscore.com"))

    when:
    URI validatedUri = config.validateURI(uri)

    then:
    noExceptionThrown()
    validatedUri

    where:
    uri << [
        'https://example.com:badpassword@host_with_underscore.com'
    ]
  }

  @Unroll
  def 'allows verbatim IP addresses if configured'() {
    given:
    UserConfiguredUrlRestrictions config = withoutDns(new UserConfiguredUrlRestrictions.Builder()
        .withRejectVerbatimIps(false))

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
      // IPV6 when using HttpUrl - which we use for validation - requires the ipv6 raw addresses to be quoted correctly.  OTherwise you get into some dangerous parsing issues
      'https://example.com:badpassword@[fd12:3456:789a:1::1]:8080',
//        'https://fd12:3456:789a:1::1',
//        'https://fc12:3456:789a:1::1',
        'https://[fd12:3456:789a:1::1]:8080',
        'https://[fc12:3456:789a:1::1]:8080'
    ]
  }

  @Unroll
  def 'excludes domains based on env vars (#envVar=#envVal) (#uri)'() {
    given:
    UserConfiguredUrlRestrictions.Builder builder = Spy(new UserConfiguredUrlRestrictions.Builder())
    builder.getEnvValue(envVar) >> envVal
    UserConfiguredUrlRestrictions config = withoutDns(builder
        .withExcludedDomainsFromEnvironment(List.of(
            "POD_NAMESPACE",
            "ISTIO_META_MESH_ID"
        )))

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
    UserConfiguredUrlRestrictions config = withoutDns(new UserConfiguredUrlRestrictions.Builder()
        // Test patterns that exclude any number and any hyphen
        .withExtraExcludedPatterns(List.of(".+\\d+.+", ".+-+.+")))

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
    UserConfiguredUrlRestrictions config = withoutDns(new UserConfiguredUrlRestrictions.Builder()
        .withExtraExcludedPatterns(List.of("\\d+", "-+")))

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
