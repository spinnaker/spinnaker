/*
 * Copyright 2015 The original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kato.azure.deploy.description.templates

import com.netflix.spinnaker.kato.azure.deploy.description.UpsertAzureLoadBalancerDescription
import groovy.transform.CompileStatic

@CompileStatic
public abstract class AzureResourceBaseTemplate {
  /* TODO These need to be updated to use a "Base" description object. See com.netflix.spinnaker.clouddriver.azure.client.models */
  protected abstract String getTemplate(UpsertAzureLoadBalancerDescription description);
  protected abstract String getVariablesTemplate(UpsertAzureLoadBalancerDescription description);
  protected abstract String getParametersTemplate(UpsertAzureLoadBalancerDescription description);
  protected abstract String getResourcesTemplate(UpsertAzureLoadBalancerDescription description);

  protected static String apiVersion = "2015-05-01-preview"

  protected static String baseTemplate = "{\n" +
    "  \"\$schema\": \"https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#\",\n" +
    "  \"contentVersion\": \"1.0.0.0\", \n %s \n }"

  protected static String baseParametersTemplate = "  \"parameters\": {\n %s \n  }"
  protected static String baseVariablesTemplate = "  \"variables\": {\n %s \n  }"
  protected static String baseResourceTemplate = "  \"resources\": [\n %s \n  ]"

  protected static String resourceEntry = "    {\n %s\n    }"
  protected static String resourceHeader = "      \"apiVersion\": \"%s\", \n" +
    "      \"name\": \"[%s]\", \n" +
    "      \"type\": \"%s\", \n" +
    "      \"location\": \"[parameters('location')]\", \n"

  protected static String getResourceEntry(String value, String delimiter) {
    String.format(resourceEntry, value) + delimiter + "\n"
  }

  protected static String dependsOn = "      \"dependsOn\": [\n" +
    "        \"%s\"\n" +
    "      ],\n"

  protected static String resourceProperties = "      \"properties\": {\n%s      }\n"

  protected static String resourceArrayString = " [\n%s\n        ]"

  protected static String concatString = "[concat(%s,%s)]"

  protected static String variableString = "variables('%s')"

  // format for entries in variable section - "<name>" : "<value>"
  protected static String variableEntry = "\"%s\": \"%s\""

  // first parameter - resource type
  // second parameter - resource name
  protected static resourceIdLookupString = "[resourceID('%s',variables('%s'))]"

  protected static String varEntryIndent = "     "

  protected static String addVarEntry(String entry) {
    String formattedEntry = varEntryIndent + entry + "\n"
    formattedEntry
  }

}
