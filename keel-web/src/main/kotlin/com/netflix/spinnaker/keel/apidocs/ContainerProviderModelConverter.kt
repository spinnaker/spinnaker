package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.DigestProvider
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.docker.VersionedTagProvider
import org.springframework.stereotype.Component

@Component
class ContainerProviderModelConverter : SubtypesModelConverter<ContainerProvider>(ContainerProvider::class.java) {
  override val subTypes = listOf(
    ReferenceProvider::class.java,
    DigestProvider::class.java,
    VersionedTagProvider::class.java
  )
}
