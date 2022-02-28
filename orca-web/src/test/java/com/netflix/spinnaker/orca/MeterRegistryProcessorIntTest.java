/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {Main.class})
@ContextConfiguration(classes = {MeterRegistryProcessorTestConfiguration.class})
@TestPropertySource(
    properties = {"spring.config.location=classpath:orca-test.yml", "redis.enabled: false"})
public class MeterRegistryProcessorIntTest {

  @Autowired TestBeanPostProcessor testBeanPostProcessor;

  @Test
  public void test() {
    assertEquals(true, testBeanPostProcessor.invoked);
  }

  /*
   Helper bean post processor used as spy
  */
  public static class TestBeanPostProcessor implements BeanPostProcessor {
    boolean invoked = false;

    public TestBeanPostProcessor() {}

    @Override
    public final Object postProcessBeforeInitialization(Object bean, String beanName)
        throws BeansException {

      if (bean instanceof MeterRegistry) {
        invoked = true;
      }

      return bean;
    }
  }
}
