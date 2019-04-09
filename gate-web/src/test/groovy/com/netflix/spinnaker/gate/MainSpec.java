package com.netflix.spinnaker.gate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {Main.class})
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
public class MainSpec {
  @Test
  public void startupTest(){

  }
}
