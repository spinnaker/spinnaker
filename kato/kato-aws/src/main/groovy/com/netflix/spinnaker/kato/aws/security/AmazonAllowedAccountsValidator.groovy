package com.netflix.spinnaker.kato.aws.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kato.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.kato.security.AllowedAccountsValidator
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Slf4j
@Component
class AmazonAllowedAccountsValidator implements AllowedAccountsValidator {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  @Override
  void validate(String user, Collection<String> allowedAccounts, Object description, Errors errors) {
    if (description instanceof AbstractAmazonCredentialsDescription && allowedAccounts) {
      if (!allowedAccounts.contains(description.credentialAccount.toLowerCase())) {
        def json = null
        try {
          json = OBJECT_MAPPER.writeValueAsString(description)
        } catch (Exception ignored) {}

        log.warn("${user} is not authorized (account: ${description.credentialAccount}, description: ${description.class.simpleName}, allowedAccounts: ${allowedAccounts}, json: ${json})")
        // errors.rejectValue("credentials", "unauthorized", "${user} is not authorized (account: ${description.credentialAccount}, description: ${description.class.simpleName})")
      } else {
        log.info("${user} is authorized (account: ${description.credentialAccount}, description: ${description.class.simpleName}, allowedAccounts: ${allowedAccounts})")
      }
    }
  }
}
