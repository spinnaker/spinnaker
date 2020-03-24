package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ImageProvider
import com.netflix.spinnaker.keel.api.ec2.JenkinsImageProvider
import com.netflix.spinnaker.keel.api.ec2.ReferenceArtifactImageProvider
import org.springframework.stereotype.Component

@Component
class ImageProviderModelConverter : SubtypesModelConverter<ImageProvider>(ImageProvider::class.java) {
  override val subTypes = listOf(
    ArtifactImageProvider::class.java,
    ReferenceArtifactImageProvider::class.java,
    JenkinsImageProvider::class.java
  )
}
