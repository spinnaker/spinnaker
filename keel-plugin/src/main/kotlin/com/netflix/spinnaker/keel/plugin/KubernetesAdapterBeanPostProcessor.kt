package com.netflix.spinnaker.keel.plugin

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.stereotype.Component

@Component
internal class KubernetesAdapterBeanPostProcessor()
  : BeanDefinitionRegistryPostProcessor {
  override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
    log.info("postProcessBeanDefinitionRegistry")
  }

  override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
    beanFactory
      .getBeanNamesForType(AssetPlugin::class.java)
      .also {
        log.info("Found these plugins: {}", it.joinToString())
      }
      .forEach { name ->
        when (beanFactory) {
          is BeanDefinitionRegistry -> beanFactory.registerBeanDefinition(
            "${name}Adapter",
            BeanDefinitionBuilder
              .genericBeanDefinition(AssetPluginKubernetesAdapter::class.java)
              .addConstructorArgReference("extensionsApi")
              .addConstructorArgReference("customObjectsApi")
              .addConstructorArgReference(name)
              .beanDefinition
          )
          else -> throw IllegalStateException("This is some weird Spring setup that doesn't use a regular bean factory")
        }
      }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
