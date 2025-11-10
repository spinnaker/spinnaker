/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.listeners

import com.netflix.spinnaker.clouddriver.core.CloudProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter
import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig
import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig.OperationsSecurityConfigurationProperties
import com.netflix.spinnaker.clouddriver.security.resources.AccountNameable
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable
import com.netflix.spinnaker.clouddriver.security.resources.MissingSecurityCheck
import com.netflix.spinnaker.clouddriver.security.resources.ResourcesNameable
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.ClassUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component

import jakarta.el.MethodNotFoundException
import java.lang.annotation.Annotation
import java.lang.reflect.Method

@Slf4j
@Component
class OperationsTypeChecker implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {

  private static List<Class> FIAT_SECURED_INTERFACES = [
      AccountNameable,
      ApplicationNameable,
      ResourcesNameable
  ]

  @Autowired
  List<CloudProvider> cloudProviders

  @Autowired
  ApplicationContext applicationContext

  @Autowired
  OperationsSecurityConfigurationProperties opsSecurityConfigProps

  /**
   * This method searches the ApplicationContext for items that should appear in the
   * {@link AtomicOperationsRegistry} and checks them for the proper Fiat interface implementations.
   */
  @Override
  void onApplicationEvent(ContextRefreshedEvent event) {
    // Docker's annotation is just the Annotation class, which is unhelpful and thus removed.
    def annotationTypes = (cloudProviders).findResults { it.getOperationAnnotationType() } - Annotation
    log.info("Found ${annotationTypes.size()} cloud provider annotations: ${annotationTypes*.simpleName}")

    annotationTypes.each { annotationType ->
      def beanMap = applicationContext.getBeansWithAnnotation(annotationType)
      log.debug("~~~")
      log.debug("Found ${beanMap.size()} beans (converters and validators) in ${annotationType.simpleName}")

      beanMap.each { beanName, instance ->
        if (!isConverter(instance)) {
          return
        }

        def convertedType = getConvertDescriptionReturnType(instance)
        if (!convertedType || Object.class == convertedType) {
          log.warn("Can't determine return type for converter $beanName, so Fiat security checks cannot be performed")
          return
        }

        if (!isFiatSecured(convertedType)) {
          def msg = "Operation description ${convertedType.simpleName} is NOT secured by Fiat authorization checks."

          switch(opsSecurityConfigProps.onMissingSecuredCheck) {
            case SecurityConfig.SecurityAction.WARN:
              log.warn(msg)
              break
            case SecurityConfig.SecurityAction.FAIL:
              throw new MissingSecurityCheck(msg)
          }
        }
      }
    }
  }

  static Class getConvertDescriptionReturnType(Object instance) {
    try {
      Method convertDescriptionMethod = instance.class.getMethod("convertDescription", Map)
      return convertDescriptionMethod.returnType
    } catch (MethodNotFoundException mnfe) {
      log.debug("Cannot find 'convertDescription' method", mnfe)
    }
    return null
  }

  static boolean isConverter(Object instance) {
    return instance instanceof AtomicOperationConverter
  }

  static boolean isFiatSecured(Class convertedType) {
    List interfaces = ClassUtils.getAllInterfaces(convertedType)
    def intersection =  FIAT_SECURED_INTERFACES.intersect(interfaces)
    if (!intersection.isEmpty()) {
      log.debug("Description $convertedType.simpleName implements ${intersection*.simpleName}")
    }
    return !intersection.isEmpty()
  }
}
