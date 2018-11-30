package com.netflix.spinnaker.keel.plugin

import java.io.Reader

/**
 * Each plugin provides a bean implementing this interface for every CRD that needs to be registered
 * with Kubernetes.
 */
interface CustomResourceDefinitionLocator {

  fun locate(): Reader

}
