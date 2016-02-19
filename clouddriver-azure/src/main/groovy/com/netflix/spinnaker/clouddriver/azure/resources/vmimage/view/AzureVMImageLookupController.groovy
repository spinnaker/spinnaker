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

package com.netflix.spinnaker.clouddriver.azure.resources.vmimage.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureVMImage
import com.netflix.spinnaker.clouddriver.azure.security.AzureNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController


@Slf4j
@RestController
@RequestMapping("/azure/images")
class AzureVMImageLookupController {
  private static final int MAX_SEARCH_RESULTS = 100
  private static final int MIN_NAME_FILTER = 3

  private final AzureCloudProvider azureCloudProvider
  private final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AzureVMImageLookupController(AzureCloudProvider azureCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @RequestMapping(value = '/{account}/{region}/{imageId:.+}', method = RequestMethod.GET)
  List<AzureNamedImage> getVMImage(@PathVariable String account, @PathVariable String region, @PathVariable String imageId) {
    def result = new ArrayList<AzureNamedImage>()

    // return a list of virtual machine images as read from the config.yml file
    // TODO: retrieve the list of virtual machine images from the azure respective cache
    accountCredentialsProvider.getAll().each { creds ->
      if(creds instanceof AzureNamedAccountCredentials && creds.accountName == account) {
        creds.vmImages.each { vmImage ->
          def imageName = vmImage.offer + " " + vmImage.sku + " (${vmImage.publisher})"
          if (imageName == imageId) {
            result += new AzureNamedImage(
              imageName : vmImage.offer + " " + vmImage.sku + " (${vmImage.publisher})",
              publisher: vmImage.publisher,
              offer: vmImage.offer,
              sku: vmImage.sku,
              version: vmImage.version
            )
          }
        }
      }
    }
    if (result.isEmpty()) {
      throw new ImageNotFoundException("${imageId} not found in ${account}/${region}")
    }

    result
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  List<AzureNamedImage> list(LookupOptions lookupOptions) {
    def result = new ArrayList<AzureNamedImage>()

    // return a list of virtual machine images as read from the config.yml file
    accountCredentialsProvider.getAll().each { creds ->
      if(creds instanceof AzureNamedAccountCredentials) {
        creds.vmImages.each { vmImage ->
          def imageName = vmImage.offer + "-" + vmImage.sku + " (Recommended)"
          if (lookupOptions.q == null || imageName.toLowerCase().startsWith(lookupOptions.q.toLowerCase())) {
            result += new AzureNamedImage(
              imageName: imageName,
              publisher: vmImage.publisher,
              offer: vmImage.offer,
              sku: vmImage.sku,
              version: vmImage.version
            )
            if (result.size() >= MAX_SEARCH_RESULTS)
              return result
          }
        }
      }
    }

    if (!lookupOptions.configOnly && lookupOptions.q != null && lookupOptions.q.length() >= MIN_NAME_FILTER) {
      // TODO: retrieve the list of virtual machine images from the azure respective cache
      result.addAll(getAllMatchingKeyPattern(lookupOptions.q,
                              Keys.getVMImageKey( azureCloudProvider,
                                                  lookupOptions.account?:'*',
                                                  lookupOptions.region?:'*',
                                                  "*",
                                                  "*"
                              )))
      //result.addAll(cachedVMImages.subList(0, Math.min(MAX_SEARCH_RESULTS, cachedVMImages.size())))
    }

    result
  }

  List<AzureNamedImage> getAllMatchingKeyPattern(String vmImagePartName, String pattern) {
    loadResults(vmImagePartName, cacheView.filterIdentifiers(Keys.Namespace.AZURE_VMIMAGES.ns, pattern))
  }

  List<AzureNamedImage> loadResults(String vmImagePartName, Collection<String> identifiers) {
    def results = [] as List<AzureNamedImage>
    def data = cacheView.getAll(Keys.Namespace.AZURE_VMIMAGES.ns, identifiers, RelationshipCacheFilter.none())
    data.each {cacheData ->
      def item = fromCacheData(vmImagePartName, cacheData)

      if (item)
        results += item

      if (results.size() >= MAX_SEARCH_RESULTS)
        return results
    }

    results
  }

  AzureNamedImage fromCacheData(String vmImagePartName, CacheData cacheData) {
    AzureVMImage vmImage = objectMapper.convertValue(cacheData.attributes['vmimage'], AzureVMImage)
    def imageName = vmImage.offer + "-" + vmImage.sku + " (${vmImage.publisher} ${vmImage.version})"
    def parts = Keys.parse(azureCloudProvider, cacheData.id)

    if (imageName.toLowerCase().startsWith(vmImagePartName.toLowerCase())) {

      return new AzureNamedImage(
                  imageName: imageName,
                  publisher: vmImage.publisher,
                  offer: vmImage.offer,
                  sku: vmImage.sku,
                  version: vmImage.version,
                  account: parts.account,
                  region: parts.region
                )
    }

    null
  }

  @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = 'Image not found')
  @InheritConstructors
  private static class ImageNotFoundException extends RuntimeException { }

  private static class AzureNamedImage {
    String imageName
    String publisher
    String offer
    String sku
    String version
    String account
    String region
 }

  private static class LookupOptions {
    String q
    String account
    String region
    Boolean configOnly = true
  }
}
