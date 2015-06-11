package com.netflix.spinnaker.kato.orchestration

import groovy.transform.Canonical

@Canonical
class AtomicOperationException extends RuntimeException {
  String error
  List<String> errors
}
