package com.netflix.spinnaker.keel.yaml

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLParser
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.http.MediaType
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter

const val APPLICATION_YAML_VALUE = "application/x-yaml"
val APPLICATION_YAML = MediaType.parseMediaType(APPLICATION_YAML_VALUE)

class YamlHttpMessageConverter : AbstractJackson2HttpMessageConverter(
  YAMLMapper().registerKotlinModule(),
  APPLICATION_YAML
)
