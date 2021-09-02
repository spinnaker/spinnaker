package com.netflix.spinnaker.keel.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.exceptions.YamlParsingException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.InputStream

/**
 * Reads [yaml] into an object of type [T] while allowing for anchors and aliases in the YAML.
 * This has to be done as a 2-step process as [YAMLMapper] does not directly support anchors and aliases.
 * First the input is resolved using SnakeYaml, which will inline any aliases, and only then passed to the YAMLMapper
 * for conversion.
 */
inline fun <reified T> YAMLMapper.readValueInliningAliases(yaml: String): T {
  try {
    val options = LoaderOptions()
    options.maxAliasesForCollections = 1000
    return convertValue(Yaml(options).load<Map<String, Any?>>(yaml))
  } catch (ex: Exception) {
    throw YamlParsingException(ex)
  }
}

/**
 * Converts a YAML stream into JSON with any anchors and aliases resolved.
 */
fun ObjectMapper.writeYamlAsJsonString(stream: InputStream): String =
  writeValueAsString(Yaml().load<Map<String, Any?>>(stream))
