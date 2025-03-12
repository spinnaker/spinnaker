/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter
import groovy.util.logging.Slf4j
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@Slf4j
@RestController
@RequestMapping("/features")
class FeaturesController implements ApplicationContextAware {
  private ApplicationContext applicationContext

  @Autowired
  Collection<AtomicOperationConverter> atomicOperationConverters = []

  @RequestMapping(value = "/stages", method = RequestMethod.GET)
  Collection<Map> stages() {
    return atomicOperationConverters.collect { AtomicOperationConverter atomicOperationConverter ->
      try {
        def value = atomicOperationConverter.class.annotations.findResult {
          def operationInterface = it.class.interfaces.find {
            // look for a cloud provider-specific annotation indicating it's an AtomicOperation
            it.name.endsWith("Operation")
          }

          if (operationInterface) {
            def annotation = atomicOperationConverter.class.getAnnotation(operationInterface)
            return AnnotationUtils.getValue(annotation)
          }

          return null
        }

        value = value ?: atomicOperationConverter.class.getAnnotation(Component)?.value()
        if (!value) {
          def beanNames = applicationContext.getBeanNamesForType(atomicOperationConverter.class)
          if (beanNames.size() == 1) {
            value = beanNames[0]
          } else {
            // unable to determine bean/stage name, do not include it in available stages (very strange if it happens!)
            value = atomicOperationConverter.class.simpleName
          }
        }

        return [
          name   : value,
          enabled: true
        ]
      } catch (Exception e) {
        log.warn("Unable to determine bean/stage name for ${atomicOperationConverter.class}", e)
        return [
          name : atomicOperationConverter.class.simpleName,
          enabled: true
        ]
      }
    }

  }

  @Override
  void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext
  }
}
