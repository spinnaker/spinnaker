package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.id
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A base class for native resource handlers which leverage [Resolver]s to apply defaults/opinions to
 * [Resource] specs when determining desired state.
 */
abstract class ResolvableResourceHandler<S : ResourceSpec, R : Any>(
  private val resolvers: List<Resolver<*>>
) : ResourceHandler<S, R> {
  protected val log: Logger = LoggerFactory.getLogger(javaClass)

  /**
   * Applies any defaults / opinions to the resource as it is resolved into its [desired] state.
   *
   * @return [resource] or a copy of [resource] that may have been changed in order to set default
   * values or apply opinions.
   */
  private fun applyResolvers(resource: Resource<S>): Resource<S> =
    resolvers
      .supporting(resource)
      .fold(resource) { r, resolver ->
        log.debug("Applying ${resolver.javaClass.simpleName} to ${r.id}")
        resolver(r)
      }

  /**
   * Convert the resource spec into the type that represents the diff-able desired state. This may
   * involve looking up referenced resources, splitting a multi-region resource into discrete
   * objects for each region, etc.
   *
   * Implementations of this method should not actuate any changes.
   *
   * @param resource a fully-resolved version of the persisted resource spec. You can assume that
   * [applyResolvers] has already been called on this object.
   */
  protected abstract suspend fun toResolvedType(resource: Resource<S>): R

  override suspend fun desired(resource: Resource<S>): R = toResolvedType(applyResolvers(resource))
}
