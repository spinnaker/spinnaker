package com.netflix.spinnaker.kato.aws.security

import com.netflix.spinnaker.kato.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.kato.security.AllowedAccountsValidator
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Slf4j
@Component
class AmazonAllowedAccountsValidator implements AllowedAccountsValidator {
  @Override
  void validate(String user, Collection<String> allowedAccounts, Object description, Errors errors) {
    if (description instanceof AbstractAmazonCredentialsDescription && allowedAccounts) {
      if (!allowedAccounts.contains(description.credentialAccount.toLowerCase())) {
        log.warn("${user} is not authorized (account: ${description.credentialAccount}, description: ${description.class.simpleName})")
        // errors.rejectValue("credentials", "unauthorized", "${user} is not authorized (account: ${description.credentialAccount}, description: ${description.class.simpleName})")
      }
    }
  }
}
