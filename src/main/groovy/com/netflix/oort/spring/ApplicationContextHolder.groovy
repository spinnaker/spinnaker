package com.netflix.oort.spring

import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class ApplicationContextHolder implements ApplicationContextAware {
  static ApplicationContext applicationContext

  @Override
  void setApplicationContext(ApplicationContext ctx) throws BeansException {
    applicationContext = ctx
  }
}
