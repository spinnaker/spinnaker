package com.netflix.kato.deploy.aws.converters

import com.netflix.kato.deploy.aws.description.UpsertAmazonDNSDescription
import com.netflix.kato.deploy.aws.ops.dns.UpsertAmazonDNSAtomicOperation
import com.netflix.kato.orchestration.AtomicOperation
import com.netflix.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("upsertAmazonDNSDescription")
class UpsertAmazonDNSAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  AtomicOperation convertOperation(Map input) {
    new UpsertAmazonDNSAtomicOperation(convertDescription(input))
  }

  @Override
  UpsertAmazonDNSDescription convertDescription(Map input) {
    input.credentials = getCredentialsForEnvironment(input.credentials as String)
    new UpsertAmazonDNSDescription(input)
  }
}
