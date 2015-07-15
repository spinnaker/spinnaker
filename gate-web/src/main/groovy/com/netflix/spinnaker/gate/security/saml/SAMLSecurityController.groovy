package com.netflix.spinnaker.gate.security.saml

import com.netflix.spinnaker.gate.config.GateConfig
import com.netflix.spinnaker.gate.config.SAMLSecurityConfig
import com.netflix.spinnaker.gate.security.anonymous.AnonymousSecurityConfig
import com.netflix.spinnaker.gate.services.internal.KatoService
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.opensaml.saml2.binding.encoding.HTTPRedirectDeflateEncoder
import org.opensaml.saml2.core.Assertion
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.RememberMeServices
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@ConditionalOnExpression('${saml.enabled:false}')
@RequestMapping("/auth")
@RestController
@Slf4j
class SAMLSecurityController {
  private static final String SPINNAKER_SSO_CALLBACK_KEY = "_SPINNAKER_SSO_CALLBACK"

  private final String url
  private final String certificate
  private final SAMLSecurityConfig.SAMLSecurityConfigProperties oneLoginProperties
  private final KatoService katoService

  @Autowired
  SAMLSecurityController(SAMLSecurityConfig.SAMLSecurityConfigProperties properties, KatoService katoService) {
    this.url = properties.url
    this.certificate = properties.certificate
    this.oneLoginProperties = properties
    this.katoService = katoService
  }

  @Autowired
  RememberMeServices rememberMeServices

  @Autowired(required = false)
  AnonymousSecurityConfig anonymousSecurityConfig

  @RequestMapping(method = RequestMethod.GET)
  void get(
    @RequestParam(value = "callback", required = false) String cb,
    @RequestParam(value = "path", required = false) String hash,
    HttpServletRequest request, HttpServletResponse response) {

    def callback = cb && hash ? cb + '/#' + hash : cb
    request.session.setAttribute(SPINNAKER_SSO_CALLBACK_KEY, callback)

    URL redirect
    if (oneLoginProperties.redirectBase) {
      redirect = (oneLoginProperties.redirectBase + '/auth/signIn').toURI().normalize().toURL()
    } else {
      redirect = new URL(request.scheme, request.serverName, request.serverPort, request.contextPath + '/auth/signIn')
    }

    def authnRequest = SAMLUtils.buildAuthnRequest(url, redirect, oneLoginProperties.issuerId)
    def context = SAMLUtils.buildSAMLMessageContext(authnRequest, response, url)
    oneLoginProperties.with {
      def credential = SAMLUtils.buildCredential(keyStoreType, keyStore, keyStorePassword, keyStoreAliasName)
      if (credential.present) {
        context.setOutboundSAMLMessageSigningCredential(credential.get())
      }
    }

    new HTTPRedirectDeflateEncoder().encode(context)
  }

  @RequestMapping(value = "/signIn", method = RequestMethod.POST)
  void signIn(@RequestParam("SAMLResponse") String samlResponse,
              HttpServletRequest request,
              HttpServletResponse response) {
    def assertion = SAMLUtils.buildAssertion(samlResponse)
    def user = buildUser(assertion, oneLoginProperties.userAttributeMapping, anonymousSecurityConfig?.getAllowedAccounts(), katoService.getAccounts())
    if (!hasRequiredRole(anonymousSecurityConfig, oneLoginProperties, user)) {
      throw new BadCredentialsException("Credentials are bad")
    }
    def auth = new UsernamePasswordAuthenticationToken(user, "", [new SimpleGrantedAuthority("USER")])
    SecurityContextHolder.context.authentication = auth
    rememberMeServices.loginSuccess(request, response, auth)

    String callback = request.session.getAttribute(SPINNAKER_SSO_CALLBACK_KEY)
    if (!callback) {
      response.sendError(200, "ok")
      return
    }

    response.sendRedirect callback
  }

  static boolean hasRequiredRole(AnonymousSecurityConfig anonymousSecurityConfig,
                                 SAMLSecurityConfig.SAMLSecurityConfigProperties samlSecurityConfigProperties,
                                 User user) {
    if (anonymousSecurityConfig) {
      def hasAuthenticated = user.email != anonymousSecurityConfig.defaultEmail
      if (!hasAuthenticated) {
        return false
      }

      if (anonymousSecurityConfig.allowedAccounts) {
        return true
      }
    }

    if (samlSecurityConfigProperties.requiredRoleByAccount) {
      def allAllowedAccountRoles = samlSecurityConfigProperties.requiredRoleByAccount.values()*.toLowerCase()
      if (samlSecurityConfigProperties.requiredRoleByAccount && !user.roles?.find {
        allAllowedAccountRoles.contains(it.toLowerCase())
      }) {
        log.error("User '${user.email}' does not have a required role (userRoles: ${user.roles.join(",")}, requiredRoles: ${samlSecurityConfigProperties.requiredRoleByAccount.values().join(",")})")
        return false
      }
    }

    return true
  }

  @RequestMapping(value = "/info", method = RequestMethod.GET)
  User getUser(HttpServletRequest request, HttpServletResponse response) {
    Object whoami = SecurityContextHolder.context.authentication.principal
    if (!whoami || !(whoami instanceof User) || !(hasRequiredRole(anonymousSecurityConfig, oneLoginProperties, whoami))) {
      response.addHeader GateConfig.AUTHENTICATION_REDIRECT_HEADER_NAME, "/auth"
      response.sendError 401
      null
    } else {
      (User) whoami
    }
  }

  static User buildUser(Assertion assertion,
                        SAMLSecurityConfig.UserAttributeMapping userAttributeMapping,
                        Collection<String> anonymousAllowedAccounts,
                        Collection<KatoService.Account> allAccounts) {
    def attributes = SAMLUtils.extractAttributes(assertion)
    def roles = attributes[userAttributeMapping.roles].collect { String roles ->
      def commonNames = roles.split(";")
      commonNames.collect {
        return it.indexOf("CN=") < 0 ? it : it.substring(it.indexOf("CN=") + 3, it.indexOf(","))
      }
    }.flatten()*.toLowerCase()

    def allowedAccounts = (anonymousAllowedAccounts ?: []) as Set<String>
    allAccounts.findAll {
      it.requiredGroupMembership.find {
        roles.contains(it.toLowerCase())
      }
    }.each {
      allowedAccounts << it.name
    }

    def user = new User(
      assertion.getSubject().nameID.value,
      attributes[userAttributeMapping.firstName]?.get(0),
      attributes[userAttributeMapping.lastName]?.get(0),
      roles,
      allowedAccounts
    )

    return user
  }
}
