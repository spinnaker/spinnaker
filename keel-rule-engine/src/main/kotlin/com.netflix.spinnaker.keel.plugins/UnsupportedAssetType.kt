package com.netflix.spinnaker.keel.plugins

import com.netflix.spinnaker.keel.api.TypeMetadata

class UnsupportedAssetType(type: TypeMetadata) : RuntimeException("No asset plugin supporting $type registered")
