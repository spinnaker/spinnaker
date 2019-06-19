package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ImageResult
import com.netflix.spinnaker.keel.api.ec2.NamedImage
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectFactory

class NamedImageHandler(
  private val cloudDriverService: CloudDriverService,
  override val objectMapper: ObjectMapper,
  override val normalizers: List<ResourceNormalizer<*>>,
  private val resourceRepositoryProvider: ObjectFactory<ResourceRepository>
) : ResourceHandler<NamedImage> {

  private val resourceRepository: ResourceRepository by lazy { resourceRepositoryProvider.getObject() }
  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }
  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "namedImage",
    "namedImages"
  ) to NamedImage::class.java

  override fun generateName(spec: NamedImage) = ResourceName(
    "ec2:image:${spec.account}:${spec.name}"
  )

  override suspend fun current(resource: Resource<NamedImage>): NamedImage? =
    resource.spec.copy(
      currentImage = resource.spec.currentState()
    )

  private suspend fun NamedImage.currentState(): ImageResult? =
    cloudDriverService.namedImages(name, account).sortedByDescending {
      it.attributes["creationDate"]?.toString() ?: "0000-00-00T00:00:00.000Z"
    }.firstOrNull()?.let {
      objectMapper.convertValue<ImageResult>(it)
    }

  override suspend fun upsert(resource: Resource<NamedImage>, resourceDiff: ResourceDiff<NamedImage>): List<TaskRef> {
    resourceDiff.current?.also {
      resourceRepository.store(resource.copy(spec = it))
    }
    return emptyList()
  }

  override suspend fun delete(resource: Resource<NamedImage>) {
    TODO("not implemented")
  }
}
