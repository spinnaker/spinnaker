package com.netflix.spinnaker.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Temporary mitigation for RCE in Spring Core
 * (https://bugalert.org/content/notices/2022-03-30-spring.html) Code from
 * https://www.praetorian.com/blog/spring-core-jdk9-rce/
 */
@ControllerAdvice
@Order(10000)
public class BinderControllerAdvice {

  @InitBinder
  public void setAllowedFields(WebDataBinder dataBinder) {
    Set<String> disallowedFields =
        Optional.ofNullable(dataBinder.getDisallowedFields())
            .map(existingFields -> new HashSet<>(Arrays.asList(existingFields)))
            .orElse(new HashSet<>());
    disallowedFields.addAll(Arrays.asList("class.*", "Class.*", "*.class.*", "*.Class.*"));
    dataBinder.setDisallowedFields(disallowedFields.toArray(new String[0]));
  }
}
