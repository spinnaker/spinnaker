package com.netflix.spinnaker.keel.bakery

import com.netflix.spinnaker.keel.bakery.api.BaseLabel

interface BaseImageCache {
  /**
   * @param os the desired base image operating system.
   * @param label the desired base image label.
   * @return the id of the requested base image, if it exists.
   * @throws UnknownBaseImage if there is no known base image for [os] and [label].
   */
  fun getBaseImage(os: String, label: BaseLabel): String
}
