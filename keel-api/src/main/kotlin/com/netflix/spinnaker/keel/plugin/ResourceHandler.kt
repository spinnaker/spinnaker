package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.events.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @param S the spec type.
 * @param R the resolved model type.
 *
 * If those two are the same, use [SimpleResourceHandler] instead.
 */
abstract class ResourceHandler<S : ResourceSpec, R : Any>(
  private val resolvers: List<Resolver<*>>
) : KeelPlugin {

  protected val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Maps the kind to the implementation type.
   */
  abstract val supportedKind: SupportedKind<S>

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
   * Resolve and convert the resource spec into the type that represents the diff-able desired
   * state.
   *
   * The value returned by this method is used as the basis of the diff (with the result of
   * [current] in order to decide whether to call [create]/[update]/[upsert].
   *
   * @param resource the resource as persisted in the Keel database.
   */
  suspend fun desired(resource: Resource<S>): R = toResolvedType(applyResolvers(resource))

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

  /**
   * Return the current _actual_ representation of what [resource] looks like in the cloud.
   * The entire desired state is passed so that implementations can use whatever identifying
   * information they need to look up the resource.
   *
   * The value returned by this method is used as the basis of the diff (with the result of
   * [desired] in order to decide whether to call [create]/[update]/[upsert].
   *
   * Implementations of this method should not actuate any changes.
   */
  abstract suspend fun current(resource: Resource<S>): R?

  /**
   * Create a resource so that it matches the desired state represented by [resource].
   *
   * By default this just delegates to [upsert].
   *
   * Implement this method and [update] if you need to handle create and update in different ways.
   * Otherwise just implement [upsert].
   *
   * @return a list of tasks launched to actuate the resource.
   */
  open suspend fun create(
    resource: Resource<S>,
    resourceDiff: ResourceDiff<R>
  ): List<Task> =
    upsert(resource, resourceDiff)

  /**
   * Update a resource so that it matches the desired state represented by [resource].
   *
   * By default this just delegates to [upsert].
   *
   * Implement this method and [create] if you need to handle create and update in different ways.
   * Otherwise just implement [upsert].
   *
   * @return a list of tasks launched to actuate the resource.
   */
  open suspend fun update(
    resource: Resource<S>,
    resourceDiff: ResourceDiff<R>
  ): List<Task> =
    upsert(resource, resourceDiff)

  /**
   * Create or update a resource so that it matches the desired state represented by [resource].
   *
   * You don't need to implement this method if you are implementing [create] and [update]
   * individually.
   *
   * @return a list of tasks launched to actuate the resource.
   */
  open suspend fun upsert(
    resource: Resource<S>,
    resourceDiff: ResourceDiff<R>
  ): List<Task> {
    TODO("Not implemented")
  }

  /**
   * Delete a resource as the desired state is that it should no longer exist.
   */
  open suspend fun delete(resource: Resource<S>): List<Task> = TODO("Not implemented")

  /**
   * Generate a spec from currently existing resources.
   */
  open suspend fun export(exportable: Exportable): S {
    TODO("Not implemented")
  }

  /**
   * @return `true` if this plugin is still busy running a previous actuation for [resource],
   * `false` otherwise.
   */
  open suspend fun actuationInProgress(resource: Resource<S>): Boolean = false
}

data class SupportedKind<SPEC : ResourceSpec>(
  val apiVersion: String,
  val kind: String,
  val specClass: Class<SPEC>
) {
  val typeId = "$apiVersion/$kind"
}

/**
 * Searches a list of `ResourceHandler`s and returns the first that supports [apiVersion] and
 * [kind].
 *
 * @throws UnsupportedKind if no appropriate handlers are found in the list.
 */
fun Collection<ResourceHandler<*, *>>.supporting(
  apiVersion: String,
  kind: String
): ResourceHandler<*, *> =
  find {
    it.supportedKind.apiVersion == apiVersion && it.supportedKind.kind == kind
  }
    ?: throw UnsupportedKind(apiVersion, kind)

class UnsupportedKind(apiVersion: String, kind: String) :
  IllegalStateException("No resource handler supporting \"$kind\" in \"$apiVersion\" is available")
