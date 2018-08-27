/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.orchestration

import com.netflix.spinnaker.clouddriver.core.CloudProvider
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.exceptions.CloudProviderNotFoundException
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Autowired

import java.lang.annotation.Annotation

@Slf4j
class AnnotationsBasedAtomicOperationsRegistry extends ApplicationContextAtomicOperationsRegistry {

  @Autowired
  List<CloudProvider> cloudProviders

  @Override
  AtomicOperationConverter getAtomicOperationConverter(String description, String cloudProvider, ProviderVersion version) {
    // Legacy naming convention which is not generic and description name is specific to cloud provider
    try {
      AtomicOperationConverter converter = super.getAtomicOperationConverter(description, cloudProvider, version)
      if (converter) return converter
    } catch (NoSuchBeanDefinitionException e) {
      /**
       * If 'cloudProvider' is not specified then it means that caller was querying the bean as per the old cloud provider
       * specific name and if no bean found then we can't do anything here other than throwing the NoSuchBeanDefinitionException
       *
       * TO-DO: Once all the operations have been migrated as per the new naming scheme that is not cloud provider specific, then
       * make the 'description' and 'cloudProvider' arguments mandatory for this method
       */
      if (!cloudProvider) {
        throw e
      }
    }

    Class<? extends Annotation> providerAnnotationType = getCloudProviderAnnotation(cloudProvider)

    List converters = applicationContext.getBeansWithAnnotation(providerAnnotationType).findAll { key, value ->
      value.getClass().getAnnotation(providerAnnotationType).value() == description &&
      value instanceof AtomicOperationConverter
    }.values().toList()

    converters = VersionedOperationHelper.findVersionMatches(version, converters)

    if (!converters) {
      throw new AtomicOperationConverterNotFoundException(
          "No atomic operation converter found for description '${description}' and cloud provider '${cloudProvider}'. " +
          "It is possible that either 1) the account name used for the operation is incorrect, or 2) the account name used for the operation is unhealthy/unable to communicate with ${cloudProvider}."
      )
    }

    if (converters.size() > 1) {
      throw new RuntimeException(
        "More than one (${converters.size()}) atomic operation converters found for description '${description}' and cloud provider " +
          "'${cloudProvider}' at version '${version}'"
      )
    }

    return (AtomicOperationConverter) converters[0]
  }

  @Override
  DescriptionValidator getAtomicOperationDescriptionValidator(String validator, String cloudProvider, ProviderVersion version) {
    // Legacy naming convention which is not generic and validator name is specific to cloud provider
    try {
      DescriptionValidator descriptionValidator = super.getAtomicOperationDescriptionValidator(validator, cloudProvider, version)
      if (descriptionValidator) {
        return descriptionValidator
      }
    } catch (NoSuchBeanDefinitionException e) {}

    if (!cloudProvider) return null

    Class<? extends Annotation> providerAnnotationType = getCloudProviderAnnotation(cloudProvider)

    List validators = applicationContext.getBeansWithAnnotation(providerAnnotationType).findAll { key, value ->
      DescriptionValidator.getValidatorName(value.getClass().getAnnotation(providerAnnotationType).value()) == validator &&
      value instanceof DescriptionValidator
    }.values().toList()

    validators = VersionedOperationHelper.findVersionMatches(version, validators)

    return validators ? (DescriptionValidator) validators[0] : null
  }

  protected Class<? extends Annotation> getCloudProviderAnnotation(String cloudProvider) {
    List<CloudProvider> cloudProviderInstances = cloudProviders.findAll { it.id == cloudProvider }
    if (!cloudProviderInstances) {
      throw new CloudProviderNotFoundException("No cloud provider named '${cloudProvider}' found")
    }
    if (cloudProviderInstances.size() > 1) {
      throw new RuntimeException(
        "More than one (${cloudProviderInstances.size()}) cloud providers found for the identifier '${cloudProvider}'"
      )
    }
    cloudProviderInstances[0].getOperationAnnotationType()
  }

}
