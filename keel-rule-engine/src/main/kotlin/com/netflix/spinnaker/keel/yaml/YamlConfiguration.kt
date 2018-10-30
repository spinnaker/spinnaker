package com.netflix.spinnaker.keel.yaml

import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

@Configuration
class YamlConfiguration : WebMvcConfigurerAdapter() {
  override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
    converters.add(YamlHttpMessageConverter())
  }
}
