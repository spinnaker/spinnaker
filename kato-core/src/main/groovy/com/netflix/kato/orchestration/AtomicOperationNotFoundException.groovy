package com.netflix.kato.orchestration

import groovy.transform.InheritConstructors
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Could not find a suitable converter for supplied type.")
@InheritConstructors
class AtomicOperationNotFoundException extends RuntimeException {}
