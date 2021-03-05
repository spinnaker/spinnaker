package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.artifacts.BakedImage
import com.netflix.spinnaker.keel.artifacts.DebianArtifact

/**
 * A repository used for storing details about amis that keel bakes so that we can circumvent
 * the aws + clouddriver caching and immediately know about amis that we created.
 */
interface BakedImageRepository {
  /**
   * Stores information about an image that keel successfully baked
   */
  fun store(image: BakedImage)

  /**
   * Retrieves the information we have about baked images for an artifact versions,
   * or null.
   */
  fun getByArtifactVersion(version: String, artifact: DebianArtifact): BakedImage?
}
