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
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureCustomVMImage
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureNamedImage
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

    /// First search for any matches in the custom image cache
    result.addAll(
      getAllMatchingKeyPattern(
        imageId,
        Keys.Namespace.AZURE_CUSTOMVMIMAGES.ns,
        Keys.getCustomVMImageKey(azureCloudProvider,
          account,
          region,
          "*"),
        true))

    if (!result.isEmpty()) {
      // found at least one match
      return result
    }

    // return a list of virtual machine images as read from the config.yml file
    accountCredentialsProvider.getAll().each { creds ->
      if(creds instanceof AzureNamedAccountCredentials && creds.accountName == account) {
        creds.vmImages.each { vmImage ->
          def imageName = vmImage.offer + "-" + vmImage.sku + "(Recommended)"
          if (imageName == imageId) {
            result += new AzureNamedImage(
              account: account,
              imageName : imageName,
              publisher: vmImage.publisher,
              offer: vmImage.offer,
              sku: vmImage.sku,
              version: vmImage.version
            )
          }
        }
      }
    }

    if (!result.isEmpty()) {
      return result
    }


    /// Search for any matches in the market store VM image cache
    result.addAll(
      getAllMatchingKeyPattern(
        imageId,
        Keys.Namespace.AZURE_VMIMAGES.ns,
        Keys.getVMImageKey(azureCloudProvider,
          account,
          region,
          "*",
          "*"),
        false))

    if (result.isEmpty()) {
      throw new ImageNotFoundException("${imageId} not found in ${account}/${region}")
    }

    result
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  List<AzureNamedImage> list(LookupOptions lookupOptions) {
    def result = new ArrayList<AzureNamedImage>()

    // retrieve the list of custom vm images from the SCS specified in the config.yml file and stored in the cache
    result.addAll(
      getAllMatchingKeyPattern(
        lookupOptions.q,
        Keys.Namespace.AZURE_CUSTOMVMIMAGES.ns,
        Keys.getCustomVMImageKey(azureCloudProvider,
          lookupOptions.account ?: '*',
          lookupOptions.region ?: '*',
          "*"),
        true))


    if (!lookupOptions.customOnly) {
      // return a list of virtual machine images as read from the config.yml file
      accountCredentialsProvider.getAll().each { creds ->
        if (creds instanceof AzureNamedAccountCredentials && (!lookupOptions.account || lookupOptions.account.isEmpty() || lookupOptions.account == creds.accountName)) {
          creds.vmImages.each { vmImage ->
            def imageName = vmImage.offer + "-" + vmImage.sku + "(Recommended)"
            if (lookupOptions.q == null || imageName.toLowerCase().contains(lookupOptions.q.toLowerCase())) {
              result += new AzureNamedImage(
                account: creds.accountName,
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
        // retrieve the list of virtual machine images from the azure respective cache
        result.addAll(
          getAllMatchingKeyPattern(
            lookupOptions.q,
            Keys.Namespace.AZURE_VMIMAGES.ns,
            Keys.getVMImageKey(azureCloudProvider,
              lookupOptions.account ?: '*',
              lookupOptions.region ?: '*',
              "*",
              "*"),
            false))
      }
    }

    result
  }

  List<AzureNamedImage> getAllMatchingKeyPattern(String vmImagePartName, String type, String pattern, Boolean customImage) {
    loadResults(vmImagePartName, type, cacheView.filterIdentifiers(type, pattern), customImage)
  }

  List<AzureNamedImage> loadResults(String vmImagePartName, String type, Collection<String> identifiers, Boolean customImage) {
    def results = [] as List<AzureNamedImage>
    def data = cacheView.getAll(type, identifiers, RelationshipCacheFilter.none())
    data.each {cacheData ->
      def item = customImage? fromCustomImageCacheData(vmImagePartName, cacheData) : fromVMImageCacheData(vmImagePartName, cacheData)

      if (item)
        results += item

      if (results.size() >= MAX_SEARCH_RESULTS)
        return results
    }

    results
  }

  AzureNamedImage fromVMImageCacheData(String vmImagePartName, CacheData cacheData) {
    try {
      AzureVMImage vmImage = objectMapper.convertValue(cacheData.attributes['vmimage'], AzureVMImage)
      def imageName = vmImage.offer + "-" + vmImage.sku + "(${vmImage.publisher}_${vmImage.version})"
      def parts = Keys.parse(azureCloudProvider, cacheData.id)

      if (imageName.toLowerCase().contains(vmImagePartName.toLowerCase())) {
        return new AzureNamedImage(
          imageName: imageName,
          isCustom: false,
          publisher: vmImage.publisher,
          offer: vmImage.offer,
          sku: vmImage.sku,
          version: vmImage.version,
          uri: "na",
          ostype: "na",
          account: parts.account,
          region: parts.region
        )
      }
    } catch (Exception e) {
      log.error("fromVMImageCacheData -> Unexpected exception", e)
    }

    null
  }

  AzureNamedImage fromCustomImageCacheData(String vmImagePartName, CacheData cacheData) {
    try {
      AzureCustomVMImage vmImage = objectMapper.convertValue(cacheData.attributes['vmimage'], AzureCustomVMImage)
      def parts = Keys.parse(azureCloudProvider, cacheData.id)

      if ((vmImage.region == parts.region) && (vmImagePartName == null || vmImage.name.toLowerCase().contains(vmImagePartName.toLowerCase()))) {
        return new AzureNamedImage(
          imageName: vmImage.name,
          isCustom: true,
          publisher: "na",
          offer: "na",
          sku: "na",
          version: "na",
          uri: vmImage.uri,
          ostype: vmImage.osType,
          account: parts.account,
          region: parts.region
        )
      }
    } catch (Exception e) {
      log.error("fromCustomImageCacheData -> Unexpected exception", e)
    }

    null
  }

  @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = 'Image not found')
  @InheritConstructors
  private static class ImageNotFoundException extends RuntimeException { }

  private static class LookupOptions {
    String q
    String account
    String region
    Boolean configOnly = true
    Boolean customOnly = false
  }
}
