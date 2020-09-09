package com.netflix.spinnaker.gate.security.x509

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import spock.lang.Specification

import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class X509AuthenticationUserDetailsServiceSpec extends Specification {

  def registry = new NoopRegistry()

  def "should debounce login calls"() {
    given:
    def config = Stub(DynamicConfigService) {
      getConfig(Long, 'x509.loginDebounce.debounceWindowSeconds', _) >> TimeUnit.MINUTES.toSeconds(5)
      isEnabled('x509.loginDebounce', _) >> true
    }
    def email = 'foo@bar.net'
    def view = new UserPermission(id: email).view
    def perms = Mock(PermissionService)
    def clock = new TestClock()
    def cert = Mock(X509Certificate)
    def userDetails = new X509AuthenticationUserDetailsService(clock)
    def fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)
    def fiatStatus = Mock(FiatStatus)
    userDetails.setPermissionService(perms)
    userDetails.setDynamicConfigService(config)
    userDetails.setFiatPermissionEvaluator(fiatPermissionEvaluator)
    userDetails.registry = registry
    userDetails.setFiatStatus(fiatStatus)
    fiatPermissionEvaluator.getPermission(email) >> view
    fiatStatus.isEnabled() >> true

    when: "initial login"
    userDetails.handleLogin(email, cert)

    then: "should call login"
    1 * perms.login(email)

    when: "subsequent login during debounce window"
    clock.advanceTime(Duration.ofSeconds(30))
    userDetails.handleLogin(email, cert)

    then: "should not call login"
    1 * fiatPermissionEvaluator.hasCachedPermission(email) >> true
    0 * perms.login(email)

    when: "subsequent login after debounce window"
    clock.advanceTime(Duration.ofMinutes(5))
    userDetails.handleLogin(email, cert)

    then: "should call login"
    1 * fiatPermissionEvaluator.hasCachedPermission(email) >> true
    1 * perms.login(email)

    when: "login with no cached permission"
    fiatPermissionEvaluator.hasCachedPermission(email) >> false
    userDetails.handleLogin(email, cert)

    then: "should call login"
    1 * perms.login(email)
  }

  def "should always login if debounceDisabled"() {
    given:
    def config = Mock(DynamicConfigService)
    def email = 'foo@bar.net'
    def perms = Mock(PermissionService)
    def clock = new TestClock()
    def cert = Mock(X509Certificate)
    def userDetails = new X509AuthenticationUserDetailsService(clock)
    def fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)
    def fiatStatus = Mock(FiatStatus)
    userDetails.setPermissionService(perms)
    userDetails.setDynamicConfigService(config)
    userDetails.setFiatPermissionEvaluator(fiatPermissionEvaluator)
    userDetails.setFiatStatus(fiatStatus)
    userDetails.registry = registry

    when:
    userDetails.handleLogin(email, cert)

    then:
    1 * config.isEnabled('x509.loginDebounce', _) >> false
    1 * perms.login(email)

    when:
    userDetails.handleLogin(email, cert)

    then:
    1 * config.isEnabled('x509.loginDebounce', _) >> false
    1 * perms.login(email)

    when:
    clock.advanceTime(Duration.ofSeconds(30))
    userDetails.handleLogin(email, cert)

    then:
    1 * config.isEnabled('x509.loginDebounce', _) >> false
    1 * perms.login(email)
  }

  def "should loginWithRoles if roleExtractor provided"() {
    given:
    def config = Mock(DynamicConfigService)
    def email = 'foo@bar.net'
    def perms = Mock(PermissionService)
    def clock = new TestClock()
    def cert = Mock(X509Certificate)
    def rolesExtractor = Mock(X509RolesExtractor)
    def userDetails = new X509AuthenticationUserDetailsService(clock)
    def fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)
    def fiatStatus = Mock(FiatStatus)
    def roles
    userDetails.setPermissionService(perms)
    userDetails.setDynamicConfigService(config)
    userDetails.setRolesExtractor(rolesExtractor)
    userDetails.setFiatPermissionEvaluator(fiatPermissionEvaluator)
    userDetails.setFiatStatus(fiatStatus)
    userDetails.registry = registry

    def view = new UserPermission(id: email, roles: [new Role('bish')]).view

    when:
    fiatStatus.isEnabled() >> true
    roles = userDetails.handleLogin(email, cert)

    then:
    1 * rolesExtractor.fromCertificate(cert) >> ['foo', 'bar']
    1 * config.isEnabled('x509.loginDebounce', _) >> false
    1 * perms.loginWithRoles(email, [email, 'foo', 'bar'])
    1 * fiatPermissionEvaluator.getPermission(email) >> view

    and: 'the roles retrieved from fiatPermissionEvaluator are also added to the list of returned roles'
    roles == [email, 'foo', 'bar', 'bish']

    when:
    fiatStatus.isEnabled() >> false
    roles = userDetails.handleLogin(email, cert)

    then:
    1 * rolesExtractor.fromCertificate(cert) >> ['foo', 'bar']
    1 * config.isEnabled('x509.loginDebounce', _) >> false
    1 * perms.loginWithRoles(email, [email, 'foo', 'bar'])
    0 * fiatPermissionEvaluator.getPermission(email) >> view

    and: 'no roles are retrieved from fiatPermissionEvaluator when fiat is disabled'
    roles == [email, 'foo', 'bar']
  }


  static class TestClock extends Clock {
    ZoneId zone = ZoneId.of('UTC')
    final AtomicLong currentMillis

    TestClock() {
      this(ZoneId.of('UTC'), System.currentTimeMillis())
    }

    TestClock(ZoneId zone, long currentMillis) {
      this.zone = zone
      this.currentMillis = new AtomicLong(currentMillis)
    }

    @Override
    ZoneId getZone() {
      return zone
    }

    @Override
    Clock withZone(ZoneId zone) {
      if (zone.equals(this.zone)) {
        return this
      }
      return new TestClock(zone, millis())
    }

    @Override
    long millis() {
      return currentMillis.get()
    }

    @Override
    Instant instant() {
      return Instant.ofEpochMilli(millis())
    }

    void setTime(long millis) {
      currentMillis.set(millis)
    }

    void advanceTime(Duration duration) {
      currentMillis.addAndGet(duration.toMillis())
    }
  }
}
