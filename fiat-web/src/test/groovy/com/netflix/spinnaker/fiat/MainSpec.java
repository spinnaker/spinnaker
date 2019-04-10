package com.netflix.spinnaker.fiat;

import com.netflix.hystrix.strategy.HystrixPlugins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {Main.class})
@TestPropertySource(properties = {"spring.config.location=classpath:fiat-test.yml"})
public class MainSpec {

  @Before
  public void setUp() throws Exception {
    HystrixPlugins.reset();
  }

  @Test
  public void startupTest(){

  }
}