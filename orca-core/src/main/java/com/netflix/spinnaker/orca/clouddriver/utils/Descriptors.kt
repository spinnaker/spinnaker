package com.netflix.spinnaker.orca.clouddriver.utils

data class ClusterDescriptor(val app: String, val account: String, val name: String, val cloudProvider: String)
data class ServerGroupDescriptor(val account: String, val name: String, val region: String)
