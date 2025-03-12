package com.netflix.spinnaker.fiat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {Main.class})
@TestPropertySource(properties = {"spring.config.location=classpath:fiat-test.yml"})
public class MainSpec {

  @Test
  public void startupTest() {}
}
