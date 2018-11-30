package com.netflix.spinnaker.config

import io.kubernetes.client.ApiClient
import io.kubernetes.client.apis.ApiextensionsV1beta1Api
import io.kubernetes.client.apis.CustomObjectsApi
import io.kubernetes.client.util.Config
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit.SECONDS
import io.kubernetes.client.Configuration as K8sConfiguration

@Configuration
@EnableConfigurationProperties(KubernetesConfigurationProperties::class)
class KubernetesConfig(
  private val properties: KubernetesConfigurationProperties
) {

  @Bean
  fun kubernetesClient(): ApiClient = Config.defaultClient().also {
    with(it.httpClient) {
      setConnectTimeout(properties.connectTimeoutSeconds, SECONDS)
      setReadTimeout(properties.readTimeoutSeconds, SECONDS)
    }
    K8sConfiguration.setDefaultApiClient(it)
  }

  @Bean
  fun extensionsApi(kubernetesClient: ApiClient): ApiextensionsV1beta1Api =
    ApiextensionsV1beta1Api(kubernetesClient)

  @Bean
  fun customObjectsApi(kubernetesClient: ApiClient): CustomObjectsApi =
    CustomObjectsApi(kubernetesClient)
}
