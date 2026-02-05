package com.netflix.spinnaker.gate.security.ldap

import com.netflix.spinnaker.gate.security.AllowedAccountsSupport
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.security.User
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
    details instanceof User
    def user = details as User
    user.username == "joe"
    user.email == "joe.dohn@test.com"
    user.firstName == "Joe"
    user.lastName == "Dohn"
    (user.roles as Set) == (["admin", "dev", "viewer"] as Set)
    user.allowedAccounts == (["acc1", "acc2"] as Set)
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
    def user = details as User
    (user.roles as Set) == (["", "admin"] as Set)
  }

  def "mapUserToContext should throw UnsupportedOperationException"() {
    when:
    subject.mapUserToContext(null, new DirContextAdapter())

    then:
    def ex = thrown(UnsupportedOperationException)
    ex.message == "Cannot save to LDAP server"
  }
}
