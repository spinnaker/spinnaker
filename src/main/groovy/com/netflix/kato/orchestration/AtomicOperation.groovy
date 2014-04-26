package com.netflix.kato.orchestration

public interface AtomicOperation<R> {
  R operate(List priorOutputs)
}
