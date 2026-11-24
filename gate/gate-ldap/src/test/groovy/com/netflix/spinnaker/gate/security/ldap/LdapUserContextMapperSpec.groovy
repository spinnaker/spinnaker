package com.netflix.spinnaker.gate.security.ldap

import com.netflix.spinnaker.gate.security.AllowedAccountsSupport
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.security.AllowedAccountsAuthorities
import com.netflix.spinnaker.security.SpinnakerAuthorities
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.ldap.userdetails.InetOrgPerson
import org.springframework.ldap.core.DirContextAdapter
import org.springframework.ldap.core.DirContextOperations
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

class LdapUserContextMapperSpec extends Specification {

  def permissionService = Mock(PermissionService)
  def allowedAccountsSupport = Mock(AllowedAccountsSupport)

  def subject = new LdapSsoConfig.LdapUserContextMapper(
    permissionService: permissionService,
    allowedAccountsSupport: allowedAccountsSupport
  )

  def "mapUserFromContext should map user details, sanitize roles, and call PermissionService and AllowedAccountsSupport"() {
    given:
    DirContextOperations ctx = new DirContextAdapter()
    ctx.setAttributeValue("mail", "joe.dohn@test.com")
    ctx.setAttributeValue("givenName", "Joe")
    ctx.setAttributeValue("sn", "Dohn")

    def authorities = [
      new SimpleGrantedAuthority("ROLE_ADMIN"),
      new SimpleGrantedAuthority("role_Dev"),
      new SimpleGrantedAuthority("viewer")
    ]

    when:
    def details = subject.mapUserFromContext(ctx, "joe", authorities)

    then:
    1 * permissionService.loginWithRoles("joe", ["admin", "dev", "viewer"] as Set)
    1 * allowedAccountsSupport.filterAllowedAccounts("joe", ["admin", "dev", "viewer"] as Set) >> (["acc1", "acc2"] as Set)

    and:
    details instanceof InetOrgPerson
    details.username == "joe"
    details.mail == "joe.dohn@test.com"
    details.givenName == "Joe"
    details.sn == "Dohn"
    (SpinnakerAuthorities.getRoleNames(auth(details)) as Set) == (["admin", "dev", "viewer"] as Set)
    AllowedAccountsAuthorities.getAllowedAccountNames(auth(details)) == ["acc1", "acc2"]
  }

  def "mapUserFromContext tolerates a directory entry without cn and sn"() {
    given:
    DirContextOperations ctx = new DirContextAdapter()
    allowedAccountsSupport.filterAllowedAccounts("joe", _) >> ([] as Set)

    when:
    def details = subject.mapUserFromContext(ctx, "joe", [new SimpleGrantedAuthority("dev")])

    then:
    details instanceof InetOrgPerson
    details.username == "joe"
    details.mail == null
  }

  def "sanitizeRoles should remove ROLE_ prefix and lower-case; empty string is preserved"() {
    given:
    DirContextOperations ctx = new DirContextAdapter()
    ctx.setAttributeValue("mail", "joe.dohn@test.com")
    ctx.setAttributeValue("givenName", "Joe")
    ctx.setAttributeValue("sn", "Dohn")

    def authorities = [
      new SimpleGrantedAuthority("ROLE_"),
      new SimpleGrantedAuthority("ROLE_ADMIN")
    ]

    allowedAccountsSupport.filterAllowedAccounts("joe", ["", "admin"] as Set) >> ([] as Set)

    when:
    def details = subject.mapUserFromContext(ctx, "joe", authorities)

    then:
    1 * permissionService.loginWithRoles("joe", ["", "admin"] as Set)
    (details.authorities*.authority as Set) == (["admin"] as Set)
  }

  private static Authentication auth(UserDetails details) {
    new UsernamePasswordAuthenticationToken(details, "", details.authorities)
  }

  def "mapUserToContext should throw UnsupportedOperationException"() {
    when:
    subject.mapUserToContext(null, new DirContextAdapter())

    then:
    def ex = thrown(UnsupportedOperationException)
    ex.message == "Cannot save to LDAP server"
  }
}
