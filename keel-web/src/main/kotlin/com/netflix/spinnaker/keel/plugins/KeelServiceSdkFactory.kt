package com.netflix.spinnaker.keel.plugins

import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext

/**
 * Creates [KeelServiceSdk] objects that can be consumed by external plugins.
 */
class KeelServiceSdkFactory(
  private val applicationContext: ApplicationContext
) : SdkFactory {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val keelServiceSdk by lazy {
    val repository = getFirstBeanOfType(KeelRepository::class.java)
    val taskLauncher = getFirstBeanOfType(TaskLauncher::class.java)
    KeelServiceSdkImpl(repository, taskLauncher)
  }

  override fun create(extensionClass: Class<*>, pluginWrapper: PluginWrapper?): Any =
    keelServiceSdk

  private inline fun <reified T> getFirstBeanOfType(clazz: Class<T>): T =
    applicationContext.getBeansOfType(clazz)
      .let {
        if (it.isEmpty()) {
          throw SystemException("Failed to locate bean of type ${T::class.java.name} in application context")
        } else {
          val first = it.entries.first()
          if (it.size > 1) {
            val options = it.keys.joinToString()
            log.warn("Found more than one bean of type ${T::class.java.name} ($options), selecting '${first.key}'")
          }
          first.value
        }
      }
}
