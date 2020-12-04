/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.credentials;

import com.netflix.spinnaker.credentials.definition.*;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.ResolvableType;

/**
 * CredentialsTypeBaseConfiguration is a convenient way to configure {@link CredentialsRepository}
 * and {@link AbstractCredentialsLoader} for all the {@link CredentialsTypeProperties} configured in
 * the system.
 *
 * <p>It will try to reuse any {@link CredentialsRepository}, {@link CredentialsDefinitionSource},
 * {@link CredentialsParser}, {@link CredentialsLifecycleHandler}, or {@link
 * AbstractCredentialsLoader} that may have been added as a bean and create default implementations
 * otherwise.
 */
@RequiredArgsConstructor
public class CredentialsTypeBaseConfiguration implements ApplicationContextAware {
  protected final List<CredentialsTypeProperties<?, ?>> credentialsTypeProperties;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    credentialsTypeProperties.forEach(
        prop -> initializeCredentialsRepository(prop, applicationContext));
  }

  @SuppressWarnings("unchecked")
  protected <T extends Credentials, U extends CredentialsDefinition>
      void initializeCredentialsRepository(
          CredentialsTypeProperties<T, U> properties, ApplicationContext applicationContext) {
    // Get or build lifecycle handler
    CredentialsLifecycleHandler<?> lifecycleHandler =
        getParameterizedBean(
                applicationContext,
                CredentialsLifecycleHandler.class,
                properties.getCredentialsClass())
            .orElseGet(NoopCredentialsLifecycleHandler::new);

    // Get or build credentials repository
    CredentialsRepository<T> credentialsRepository =
        getParameterizedBean(
                applicationContext, CredentialsRepository.class, properties.getCredentialsClass())
            .orElseGet(
                () ->
                    registerCredentialsRepository(
                        applicationContext, properties, lifecycleHandler));

    // Get or build credential source
    CredentialsDefinitionSource<U> credentialsDefinitionSource =
        getParameterizedBean(
                applicationContext,
                CredentialsDefinitionSource.class,
                properties.getCredentialsDefinitionClass())
            .orElse(properties.getDefaultCredentialsSource());

    // Get or build credentials parser
    CredentialsParser<U, T> credentialsParser =
        getParameterizedBean(
                applicationContext,
                CredentialsParser.class,
                properties.getCredentialsDefinitionClass(),
                properties.getCredentialsClass())
            .orElse(properties.getCredentialsParser());

    // Get or build credentials loader
    AbstractCredentialsLoader<T> credentialsLoader =
        getParameterizedBean(
                applicationContext,
                AbstractCredentialsLoader.class,
                properties.getCredentialsClass())
            .orElseGet(
                () ->
                    registerCredentialsLoader(
                        applicationContext,
                        properties,
                        credentialsDefinitionSource,
                        credentialsParser,
                        credentialsRepository));

    // Get or build poller
    credentialsLoader.load();
  }

  /**
   * Registers a new MapBackedCredentialsRepository under the name "credentialsRepository.[type]"
   *
   * @param context
   * @param properties
   * @param lifecycleHandler
   * @param <T>
   * @return Credentials repository registered in Spring
   */
  @SuppressWarnings("unchecked")
  protected <T extends Credentials> CredentialsRepository<T> registerCredentialsRepository(
      ApplicationContext context,
      CredentialsTypeProperties<T, ?> properties,
      CredentialsLifecycleHandler<?> lifecycleHandler) {

    RootBeanDefinition bd = new RootBeanDefinition();
    bd.setTargetType(
        ResolvableType.forClassWithGenerics(
            CredentialsRepository.class, properties.getCredentialsClass()));
    bd.setBeanClass(MapBackedCredentialsRepository.class);
    ConstructorArgumentValues values = new ConstructorArgumentValues();
    values.addGenericArgumentValue(properties.getType());
    values.addGenericArgumentValue(lifecycleHandler);
    bd.setConstructorArgumentValues(values);

    String beanName = "credentialsRepository." + properties.getType();
    ((DefaultListableBeanFactory) ((AbstractApplicationContext) context).getBeanFactory())
        .registerBeanDefinition(beanName, bd);
    return context.getBean(beanName, MapBackedCredentialsRepository.class);
  }

  @SuppressWarnings("unchecked")
  protected <T extends Credentials, U extends CredentialsDefinition>
      AbstractCredentialsLoader<T> registerCredentialsLoader(
          ApplicationContext context,
          CredentialsTypeProperties<T, U> properties,
          CredentialsDefinitionSource<U> credentialsDefinitionSource,
          CredentialsParser<U, T> credentialsParser,
          CredentialsRepository<T> credentialsRepository) {

    RootBeanDefinition bd = new RootBeanDefinition();
    bd.setTargetType(
        ResolvableType.forClassWithGenerics(
            AbstractCredentialsLoader.class, properties.getCredentialsClass()));
    bd.setBeanClass(BasicCredentialsLoader.class);
    ConstructorArgumentValues values = new ConstructorArgumentValues();
    values.addGenericArgumentValue(credentialsDefinitionSource);
    values.addGenericArgumentValue(credentialsParser);
    values.addGenericArgumentValue(credentialsRepository);
    bd.setConstructorArgumentValues(values);

    String beanName = "credentialsLoader." + properties.getType();
    ((DefaultListableBeanFactory) ((AbstractApplicationContext) context).getBeanFactory())
        .registerBeanDefinition(beanName, bd);
    return context.getBean(beanName, AbstractCredentialsLoader.class);
  }

  @SuppressWarnings("unchecked")
  protected <T> Optional<T> getParameterizedBean(
      ApplicationContext applicationContext, Class<T> paramClass, Class<?>... generics) {
    ResolvableType resolvableType = ResolvableType.forClassWithGenerics(paramClass, generics);
    String[] beanNames = applicationContext.getBeanNamesForType(resolvableType);
    if (beanNames.length == 1) {
      return Optional.of((T) applicationContext.getBean(beanNames[0]));
    }
    if (beanNames.length == 0) {
      return Optional.empty();
    }
    throw new IllegalArgumentException(
        beanNames.length
            + " beans found of class "
            + paramClass
            + " ("
            + generics.length
            + " generics)");
  }
}
