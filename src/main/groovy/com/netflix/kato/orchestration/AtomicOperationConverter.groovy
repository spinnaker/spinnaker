package com.netflix.kato.orchestration

interface AtomicOperationConverter {
  AtomicOperation convertOperation(Map input)
  Object convertDescription(Map input)
}