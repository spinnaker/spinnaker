package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.AssetName
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.NOT_FOUND)
class AssetNotFound(val name: AssetName) : RuntimeException()
