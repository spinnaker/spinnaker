/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.plugins.internal

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Get class data from the class path.
 *
 * @author Decebal Suiu
 */
class DefaultClassDataProvider : ClassDataProvider {

  override fun getClassData(className: String): ByteArray {
    val path = className.replace('.', '/') + ".class"
    val classDataStream = javaClass.classLoader.getResourceAsStream(path)
      ?: throw ClassDataProviderException("Cannot find class data")

    try {
      ByteArrayOutputStream().use { outputStream ->
        classDataStream.copyTo(outputStream)
        return outputStream.toByteArray()
      }
    } catch (e: IOException) {
      throw ClassDataProviderException(e.message, e)
    }
  }

  private class ClassDataProviderException(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    constructor(message: String?) : this(message, null)
  }
}
