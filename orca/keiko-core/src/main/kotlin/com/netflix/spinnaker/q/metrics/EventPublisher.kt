package com.netflix.spinnaker.q.metrics

interface EventPublisher {
  fun publishEvent(event: QueueEvent)
}
