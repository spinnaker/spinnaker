package com.netflix.spinnaker.clouddriver.artifacts.config;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HttpUrlRestrictionsTest {

  @Test
  public void verifyThatIpRangesBlockAccess() {
    var restrictions =
        HttpUrlRestrictions.builder()
            .rejectVerbatimIps(false)
            .rejectedIps(List.of("192.168.0.0/16", "10.0.0.0/8"))
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> restrictions.validateURI(URI.create("http://192.168.0.1")));
    assertThrows(
        IllegalArgumentException.class,
        () -> restrictions.validateURI(URI.create("http://10.2.3.4")));
  }

  @Test
  public void blockVerbatimIpsWhenNoIpListSet() {
    var restrictions =
        HttpUrlRestrictions.builder().rejectVerbatimIps(true).rejectedIps(List.of()).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> restrictions.validateURI(URI.create("http://192.168.0.1")));
    assertThrows(
        IllegalArgumentException.class,
        () -> restrictions.validateURI(URI.create("http://10.2.3.4")));
  }

  @Test
  public void testWHenNoAllowedRegexButWhiteListIsSet() {
    var restrictions =
        HttpUrlRestrictions.builder()
            .allowedHostnamesRegex("")
            .allowedDomains(List.of("google.com"))
            .build();
    assertThat(restrictions.validateURI(URI.create("http://google.com"))).hasHost("google.com");
    assertThrows(
        IllegalArgumentException.class,
        () -> restrictions.validateURI(URI.create("http://microsoft.com")));
  }

  @Test
  public void allowIpsWhenVerbatimIpsIsFalse() {
    var restrictions =
        HttpUrlRestrictions.builder().rejectVerbatimIps(false).rejectedIps(List.of()).build();
    assertThat(restrictions.validateURI(URI.create("http://192.168.0.1"))).hasHost("192.168.0.1");
  }

  @Test
  public void blockIPRangesWhenResolved() {
    var restrictions =
        HttpUrlRestrictions.builder()
            .rejectVerbatimIps(true)
            .rejectedIps(List.of("10.0.0.0/8"))
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            restrictions.validateURI(
                URI.create(
                    "http://0a010203.0a010204.rbndr.us"))); // Make sure a host lookup that returns
    // a 10. address ALSO fails when
    // restricted.
    assertThat(restrictions.validateURI(URI.create("http://google.com"))).hasHost("google.com");
  }

  @Test
  public void whiteListBlockEverythingElse() {
    var restrictions = HttpUrlRestrictions.builder().allowedDomains(List.of("example.com")).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> restrictions.validateURI(URI.create("http://google.com")));
    assertThat(restrictions.validateURI(URI.create("http://example.com"))).hasHost("example.com");
  }

  @Test
  public void allowAHostIfResolvesInIpList() {
    var restrictions =
        HttpUrlRestrictions.builder()
            .rejectVerbatimIps(true)
            .rejectedIps(List.of("192.168.0.0/16"))
            .build();
    assertThat(restrictions.validateURI(URI.create("http://0a010203.0a010204.rbndr.us")))
        .hasHost("0a010203.0a010204.rbndr.us");
    assertThat(restrictions.validateURI(URI.create("http://google.com"))).hasHost("google.com");
  }

  @Test
  void blockDefaultRestrictedDomains() {
    // explicitly deny the test server we're hitting.
    HttpUrlRestrictions restrictions = HttpUrlRestrictions.builder().build();
    List<String> invalidDomains =
        List.of(
            "http://spin-clouddriver",
            "http://spin-clouddriver.prod",
            "http://spin-clouddriver.spinnaker.svc.cluster.local",
            "http://spin-clouddriver.spinnaker",
            "http://spinnaker-clouddriver:12345",
            "http://spinnaker-clouddriver.spinnaker:12345",
            "http://spin-clouddriver.local");
    invalidDomains.forEach(
        domain -> {
          Assertions.assertThrows(
              IllegalArgumentException.class, () -> restrictions.validateURI(URI.create(domain)));
        });

    assertThat(restrictions.validateURI(URI.create("http://example.com"))).hasHost("example.com");
    assertThat(restrictions.validateURI(URI.create("http://0a010203.0a010204.rbndr.us")))
        .hasHost("0a010203.0a010204.rbndr.us");
  }
}
