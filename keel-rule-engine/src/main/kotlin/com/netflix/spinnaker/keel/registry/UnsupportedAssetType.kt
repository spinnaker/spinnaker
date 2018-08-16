package com.netflix.spinnaker.keel.registry

import com.netflix.spinnaker.keel.api.TypeMetadata

class UnsupportedAssetType(type: TypeMetadata) : RuntimeException("No asset plugin supporting $type registered")
