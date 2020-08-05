package com.netflix.spinnaker.keel.api.support

/**
 * Registers an extension to a model so that it may be used in a delivery config.
 */
interface ExtensionRegistry {
  fun <BASE : Any> register(
    baseType: Class<BASE>,
    extensionType: Class<out BASE>,
    discriminator: String
  )

  fun <BASE : Any> extensionsOf(baseType: Class<BASE>): Map<String, Class<out BASE>>

  fun baseTypes(): Collection<Class<*>>
}

inline fun <reified BASE : Any> ExtensionRegistry.extensionsOf(): Map<String, Class<out BASE>> =
  extensionsOf(BASE::class.java)

inline fun <reified BASE : Any> ExtensionRegistry.register(
  extensionType: Class<out BASE>,
  discriminator: String
) {
  register(BASE::class.java, extensionType, discriminator)
}
