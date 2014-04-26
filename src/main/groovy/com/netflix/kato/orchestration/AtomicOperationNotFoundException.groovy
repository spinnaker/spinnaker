package com.netflix.kato.orchestration

import groovy.transform.InheritConstructors
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.BAD_REQUEST)
@InheritConstructors
class AtomicOperationNotFoundException extends RuntimeException {}
