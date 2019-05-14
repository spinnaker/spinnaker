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

package com.netflix.spinnaker.clouddriver.azure.templates

import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities

class DependingResource extends Resource{
  ArrayList<String> dependsOn = new ArrayList<String>()

  void addDependency(Resource dep)
  {
    dependsOn.add("[concat('" + dep.type + "/'," + StripBraces(dep.name) +")]")
  }

  static String StripBraces(String source){
    if(source.startsWith('''[''')){
      source = source.substring(1)
    }
    if (source.endsWith(''']''')){
      source = source.substring(0, source.length()-1)
    }

    return source
  }
}

class Resource {
  String apiVersion
  String name
  String type
  String location
  Map<String, String> tags
}

class Sku {
  String name

  Sku(String name) {
    this.name = name
  }
}

class IdRef{
  String id

  IdRef() {}

  public IdRef(String refID)
  {
    id = refID
  }
}

class PublicIpResource extends Resource{
  PublicIPProperties properties = new PublicIPProperties()
  Sku sku

  PublicIpResource() {
    apiVersion = "[variables('apiVersion')]"
    name = "[variables('publicIPAddressName')]"
    type = "Microsoft.Network/publicIPAddresses"
    location = "[parameters('location')]"
    sku = new Sku("Basic")
  }
}

class PublicIPProperties {
  String publicIPAllocationMethod = '''[variables('publicIPAddressType')]'''
}

class PublicIPPropertiesWithDns extends PublicIPProperties{
  DnsSettings dnsSettings = new DnsSettings()
}

class DnsSettings{
  String domainNameLabel = '''[variables('dnsNameForLBIP')]'''

  static String getUniqueDNSName(String name) {
    String noDashName = name.replaceAll("-", "").toLowerCase()
    "[concat('${AzureUtilities.DNS_NAME_PREFIX}', uniqueString(concat(resourceGroup().id, subscription().id, '$noDashName')))]"
  }

}

class StringParameter {
  String type = "string"
  Map<String, String> metadata
  StringParameter() {
    metadata = [:]
  }

  StringParameter(Map<String, String> metadata) {
    this.metadata = metadata
  }
}

class StringParameterWithDefault extends StringParameter {
  String defaultValue
  StringParameterWithDefault(Map<String, String> metadata, String defaultValue) {
    super(metadata)
    this.defaultValue = defaultValue
  }
}

class SecureStringParameter extends StringParameter{
  String defaultValue
  SecureStringParameter(Map<String, String> metadata) {
    super(metadata)
    type = "securestring"
  }

  SecureStringParameter(Map<String, String> metadata, String defaultValue) {
    this(metadata)
    this.defaultValue = defaultValue
  }
}

