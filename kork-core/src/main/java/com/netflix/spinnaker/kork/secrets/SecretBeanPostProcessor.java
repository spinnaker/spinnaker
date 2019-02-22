package com.netflix.spinnaker.kork.secrets;

import com.netflix.spinnaker.config.secrets.SecretManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.List;

public class SecretBeanPostProcessor implements BeanPostProcessor {

  private ConfigurableApplicationContext applicationContext;

  SecretBeanPostProcessor(ConfigurableApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
    return bean;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if ("secretManager".equals(beanName)) {
      MutablePropertySources propertySources = applicationContext.getEnvironment().getPropertySources();
      List<EnumerablePropertySource> enumerableSources = new ArrayList<>();

      for (PropertySource ps : propertySources) {
        if (ps instanceof EnumerablePropertySource) {
          enumerableSources.add((EnumerablePropertySource) ps);
        }
      }

      for (EnumerablePropertySource s : enumerableSources) {
        propertySources.replace(s.getName(), new SecretAwarePropertySource(s, (SecretManager) bean));
      }
    }

    return bean;
  }


}
