package com.netflix.spinnaker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.SimpleApplicationEventMulticaster
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class EventsConfiguration {
  @Bean
  fun applicationEventTaskExecutor() =
    ThreadPoolTaskExecutor().apply {
      threadNamePrefix = "event-pool-"
    }

  @Bean
  fun applicationEventMulticaster(applicationEventTaskExecutor: TaskExecutor) =
    SimpleApplicationEventMulticaster().apply {
      setTaskExecutor(applicationEventTaskExecutor)
    }
}
