package com.netflix.spinnaker.keel.lemur

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_APPLICATION_LOAD_BALANCER_V1_2
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.kork.exceptions.ConstraintViolationException
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(LemurService::class)
class LemurCertificateResolver(
  private val lemurCertificateByName: suspend (String) -> LemurCertificateResponse
) : Resolver<ApplicationLoadBalancerSpec> {
  override val supportedKind = EC2_APPLICATION_LOAD_BALANCER_V1_2

  override fun invoke(resource: Resource<ApplicationLoadBalancerSpec>): Resource<ApplicationLoadBalancerSpec> =
    resource.copy(
      spec = resource.spec.copy(
        listeners = resource.spec.listeners.mapTo(mutableSetOf()) { listener ->
          listener.certificate?.let { name ->
            listener.copy(
              certificate = runBlocking { findCurrentCertificate(name) }
            )
          } ?: listener
        }
      )
    )

  private suspend fun findCurrentCertificate(name: String): String {
    val certificate = lemurCertificateByName(name).items.firstOrNull()
    return when {
      certificate == null -> throw CertificateNotFound(name)
      certificate.active -> certificate.name
      else -> {
        val replacement = certificate.replacedBy.firstOrNull { it.active }
        replacement?.name
          ?: if (certificate.replacedBy.isNotEmpty()) {
            findCurrentCertificate(certificate.replacedBy.first().name)
          } else {
            throw CertificateExpired(certificate)
          }
      }
    }
  }
}

class CertificateNotFound(name: String) :
  ConstraintViolationException("No certificate named \"$name\" is found on Lemur")

class CertificateExpired(certificate: LemurCertificate) :
  ConstraintViolationException("Certificate ${certificate.name} is inactive since ${certificate.validityEnd} and has no replacement specified in Lemur")
