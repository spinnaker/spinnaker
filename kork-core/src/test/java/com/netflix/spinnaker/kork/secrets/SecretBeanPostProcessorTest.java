package com.netflix.spinnaker.kork.secrets;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SecretBeanPostProcessorTest {

  @Mock
  private ConfigurableApplicationContext applicationContext;

  @Mock
  private ConfigurableEnvironment environment;

  private Object regularBean = new Object();
  private SecretBeanPostProcessor secretBeanPostProcessor;
  private MutablePropertySources mutablePropertySources = new MutablePropertySources();

  private PropertySource propertySource = new PropertySource("testPropertySource") {
    @Override
    public Object getProperty(String name) {
      return null;
    }
  };

  private EnumerablePropertySource enumerablePropertySource = new EnumerablePropertySource("testEnumerableSource") {
    @Override
    public String[] getPropertyNames() {
      return new String[0];
    }

    @Override
    public Object getProperty(String name) {
      return null;
    }
  };

  @Before
  public void setup() {
    mutablePropertySources.addLast(propertySource);
    mutablePropertySources.addLast(enumerablePropertySource);

    MockitoAnnotations.initMocks(this);
    when(applicationContext.getEnvironment()).thenReturn(environment);
    when(environment.getPropertySources()).thenReturn(mutablePropertySources);

    secretBeanPostProcessor = new SecretBeanPostProcessor(applicationContext);
  }

  @Test
  public void secretManagerBeanShouldGetProcessed() {
    secretBeanPostProcessor.postProcessAfterInitialization(null, "secretManager");
    verify(applicationContext, times(1)).getEnvironment();
  }

  @Test
  public void replaceEnumerableSourceWithSecretAwareSourceInSecretManagerBean() {
    assertTrue(mutablePropertySources.get("testEnumerableSource") instanceof EnumerablePropertySource);
    assertFalse(mutablePropertySources.get("testPropertySource") instanceof EnumerablePropertySource);

    secretBeanPostProcessor.postProcessAfterInitialization(null, "secretManager");

    assertTrue(mutablePropertySources.get("testEnumerableSource") instanceof SecretAwarePropertySource);
    assertFalse(mutablePropertySources.get("testPropertySource") instanceof SecretAwarePropertySource);
  }

  @Test
  public void nonSecretManagerBeanShouldDoNothingAndReturnSameBean() {
    assertTrue(mutablePropertySources.get("testEnumerableSource") instanceof EnumerablePropertySource);
    assertFalse(mutablePropertySources.get("testPropertySource") instanceof EnumerablePropertySource);

    Object returnedBean = secretBeanPostProcessor.postProcessAfterInitialization(regularBean, "notSecretManager");

    assertTrue(mutablePropertySources.get("testEnumerableSource") instanceof EnumerablePropertySource);
    assertFalse(mutablePropertySources.get("testEnumerableSource") instanceof SecretAwarePropertySource);
    assertFalse(mutablePropertySources.get("testPropertySource") instanceof SecretAwarePropertySource);

    verify(applicationContext, never()).getEnvironment();
    assertEquals(regularBean, returnedBean);
  }
}
