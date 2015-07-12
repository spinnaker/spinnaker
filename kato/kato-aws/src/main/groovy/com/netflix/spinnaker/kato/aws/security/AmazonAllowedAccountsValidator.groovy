package com.netflix.spinnaker.kato.aws.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.kato.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.kato.security.AllowedAccountsValidator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Slf4j
@Component
class AmazonAllowedAccountsValidator implements AllowedAccountsValidator {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  private final AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AmazonAllowedAccountsValidator(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider
  }

  @Override
  void validate(String user, Collection<String> allowedAccounts, Object description, Errors errors) {
    if (!accountCredentialsProvider.all.find { it.requiredGroupMembership }) {
      // no accounts have group restrictions so no need to validate / log
      return
    }

    if (description instanceof AbstractAmazonCredentialsDescription) {
      def json = null
      try {
        json = OBJECT_MAPPER.writeValueAsString(description)
      } catch (Exception ignored) {}

      /*
       * Access should be allowed iff
       * - the account is not restricted (has no requiredGroupMembership)
       * - the user has been granted specific access (has the target account in it's set of allowed accounts)
       */
      def requiredGroups = description.credentials.requiredGroupMembership*.toLowerCase()
      def targetAccount = description.credentialAccount
      def isAuthorized = !requiredGroups || allowedAccounts.find { it.equalsIgnoreCase(targetAccount) }
      def message = "${user} is ${isAuthorized ? '' : 'not '}authorized (account: ${targetAccount}, description: ${description.class.simpleName}, allowedAccounts: ${allowedAccounts}, requiredGroups: ${requiredGroups}, json: ${json})"
      if (!isAuthorized) {
        log.warn(message)
        errors.rejectValue("credentials", "unauthorized", "${user} is not authorized (account: ${description.credentialAccount}, description: ${description.class.simpleName})")
      } else {
        log.info(message)
      }
    }
  }
}
