package com.netflix.spinnaker.keel.registry

internal object InMemoryPluginRepositoryTests
  : PluginRepositoryTests<InMemoryPluginRepository>(::InMemoryPluginRepository)
