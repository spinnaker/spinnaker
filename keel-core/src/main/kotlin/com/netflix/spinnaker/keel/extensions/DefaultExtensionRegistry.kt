package com.netflix.spinnaker.keel.extensions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DefaultExtensionRegistry(
  private val mappers: List<ObjectMapper>
) : ExtensionRegistry {
  private val baseToExtensionTypes = mutableMapOf<Class<*>, MutableMap<String, Class<*>>>()

  override fun <BASE : Any> register(
    baseType: Class<BASE>,
    extensionType: Class<out BASE>,
    discriminator: String
  ) {
    baseToExtensionTypes
      .getOrPut(baseType, ::mutableMapOf)
      .also { it[discriminator] = extensionType }
    log.info("Registering extension \"$discriminator\" for ${baseType.simpleName} using ${extensionType.simpleName}")
    mappers.forEach {
      it.registerSubtypes(NamedType(extensionType, discriminator))
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <BASE : Any> extensionsOf(baseType: Class<BASE>): Map<String, Class<out BASE>> =
    baseToExtensionTypes[baseType] as Map<String, Class<out BASE>>? ?: emptyMap()

  override fun baseTypes(): Collection<Class<*>> = baseToExtensionTypes.keys

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
