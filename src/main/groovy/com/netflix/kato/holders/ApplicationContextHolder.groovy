package com.netflix.kato.holders

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class ApplicationContextHolder implements ApplicationContextAware {
  static ApplicationContext applicationContext

  @Override
  void setApplicationContext(ApplicationContext ctx) {
    applicationContext = ctx
  }
}
