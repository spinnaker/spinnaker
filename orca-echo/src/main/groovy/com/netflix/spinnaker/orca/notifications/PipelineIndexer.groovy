package com.netflix.spinnaker.orca.notifications

import com.google.common.collect.ImmutableMap

/**
 * Implementations provide different strategies for indexing pipelines.
 */
interface PipelineIndexer {
  ImmutableMap<Serializable, Collection<Map>> getPipelines()
}