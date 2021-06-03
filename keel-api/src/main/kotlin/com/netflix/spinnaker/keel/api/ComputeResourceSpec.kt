package com.netflix.spinnaker.keel.api

/**
 * Common interface for [ResourceSpec]s that represent compute resources.
 */
interface ComputeResourceSpec<T: Locations<*>> :
  ResourceSpec, Monikered, Locatable<T>, VersionedArtifactProvider, ArtifactReferenceProvider
