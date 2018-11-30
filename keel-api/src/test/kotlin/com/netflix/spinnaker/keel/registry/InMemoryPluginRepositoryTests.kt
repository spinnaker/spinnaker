package com.netflix.spinnaker.keel.registry

internal object InMemoryPluginRepositoryTests : PluginRepositoryTests<InMemoryPluginRepository>() {
  override fun factory() = InMemoryPluginRepository()

  override fun clear(subject: InMemoryPluginRepository) {
    subject.clear()
  }
}
