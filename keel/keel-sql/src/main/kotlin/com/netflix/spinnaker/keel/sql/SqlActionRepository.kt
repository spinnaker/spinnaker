package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ActionStateUpdateContext
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.action.Action
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.action.ActionStateFull
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.action.ActionType.POST_DEPLOY
import com.netflix.spinnaker.keel.api.action.ActionType.VERIFICATION
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.persistence.metamodel.Tables
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ACTION_STATE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ACTIVE_ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_LAST_VERIFIED
import com.netflix.spinnaker.keel.resources.ResourceFactory
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import com.netflix.spinnaker.keel.sql.deliveryconfigs.deliveryConfigByName
import org.jooq.*
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.function
import org.jooq.impl.DSL.inline
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.select
import org.jooq.impl.DSL.value
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import org.springframework.core.env.Environment as SpringEnvironment

class SqlActionRepository(
  jooq: DSLContext,
  clock: Clock,
  objectMapper: ObjectMapper,
  resourceFactory: ResourceFactory,
  sqlRetry: SqlRetry,
  artifactSuppliers: List<ArtifactSupplier<*, *>> = emptyList(),
  private val environment: SpringEnvironment
) : SqlStorageContext(
  jooq,
  clock,
  sqlRetry,
  objectMapper,
  resourceFactory,
  artifactSuppliers
), ActionRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
  private val useLockingRead : Boolean
    get() = environment.getProperty("keel.verifications.db.lock.reads.enabled", Boolean::class.java, true)

  override fun nextEnvironmentsForVerification(
    minTimeSinceLastCheck: Duration,
    limit: Int
  ): Collection<ArtifactInEnvironmentContext> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck)
    return sqlRetry.withRetry(WRITE) {
      // TODO: only consider environments that have verifications
      jooq.inTransaction {
        nextEnvironmentQuery(cutoff, limit)
          .lockInShareMode(useLockingRead)
          .fetch()
          .onEach { (_, _, environmentUid, _, artifactUid, _, artifactVersion) ->
            insertInto(ENVIRONMENT_LAST_VERIFIED)
              .set(ENVIRONMENT_LAST_VERIFIED.ENVIRONMENT_UID, environmentUid)
              .set(ENVIRONMENT_LAST_VERIFIED.ARTIFACT_UID, artifactUid)
              .set(ENVIRONMENT_LAST_VERIFIED.ARTIFACT_VERSION, artifactVersion)
              .set(ENVIRONMENT_LAST_VERIFIED.AT, now)
              .onDuplicateKeyUpdate()
              .set(ENVIRONMENT_LAST_VERIFIED.ARTIFACT_VERSION, artifactVersion)
              .set(ENVIRONMENT_LAST_VERIFIED.AT, now)
              .execute()
          }
      }
    }
      .map { (_, deliveryConfigName, _, environmentName, _, artifactReference, artifactVersion) ->
        ArtifactInEnvironmentContext(
          deliveryConfigByName(deliveryConfigName),
          environmentName,
          artifactReference,
          artifactVersion
        )
      }
  }

  private fun getState(
    context: ArtifactInEnvironmentContext,
    id: String,
    type: ActionType
  ): ActionState? =
    with(context) {
      jooq
        .select(
          ACTION_STATE.STATUS,
          ACTION_STATE.STARTED_AT,
          ACTION_STATE.ENDED_AT,
          ACTION_STATE.METADATA,
          ACTION_STATE.LINK
        )
        .from(ACTION_STATE)
        .where(ACTION_STATE.ENVIRONMENT_UID.eq(environmentUid))
        .and(ACTION_STATE.ARTIFACT_UID.eq(artifactUid))
        .and(ACTION_STATE.ARTIFACT_VERSION.eq(version))
        .and(ACTION_STATE.ACTION_ID.eq(id))
        .and(ACTION_STATE.TYPE.eq(type))
        .fetchOneInto<ActionState>()
    }

  override fun getState(context: ArtifactInEnvironmentContext, action: Action): ActionState? {
    return getState(context, action.id, action.actionType)
  }

  override fun getStates(context: ArtifactInEnvironmentContext, type: ActionType): Map<String, ActionState> =
    with(context) {
      when {
        type == VERIFICATION && verifications.isEmpty() -> emptyMap() // Optimization: don't hit the db if we know there are no entries
        type == POST_DEPLOY && postDeployActions.isEmpty() -> emptyMap() // Optimization: don't hit the db if we know there are no entries
        else -> jooq.select(
          ACTION_STATE.ACTION_ID,
          ACTION_STATE.STATUS,
          ACTION_STATE.STARTED_AT,
          ACTION_STATE.ENDED_AT,
          ACTION_STATE.METADATA,
          ACTION_STATE.LINK
        )
          .from(ACTION_STATE)
          .where(ACTION_STATE.ENVIRONMENT_UID.eq(environmentUid))
          .and(ACTION_STATE.ARTIFACT_UID.eq(artifactUid))
          .and(ACTION_STATE.ARTIFACT_VERSION.eq(version))
          .and(ACTION_STATE.TYPE.eq(type))
          .fetch()
          .associate { (id, status, started_at, ended_at, metadata, link) ->
            id to ActionState(status, started_at, ended_at, metadata, link)
          }
      }
    }

  override fun allPassed(context: ArtifactInEnvironmentContext, type: ActionType): Boolean {
    val actions = if (type == VERIFICATION) {
      context.verifications
    } else {
      context.postDeployActions
    }
    return when {
      actions.isEmpty() -> true
      else -> {
        val states = getStates(context, type)
        // must have an entry for all defined actions, and all those entries must be passing
        actions
          .map { it.id }
          .all { id ->
            when (states[id]?.status) {
              ConstraintStatus.PASS, ConstraintStatus.OVERRIDE_PASS -> true.also {
                log.info("${type.name} ($id) passed against version ${context.version} for app ${context.deliveryConfig.application}")
              }
              ConstraintStatus.FAIL, ConstraintStatus.OVERRIDE_FAIL -> false.also {
                log.info("${type.name} ($id) failed against version ${context.version} for app ${context.deliveryConfig.application}")
              }
              ConstraintStatus.NOT_EVALUATED, ConstraintStatus.PENDING -> false.also {
                log.info("${type.name} ($id) still running against version ${context.version} for app ${context.deliveryConfig.application}")
              }
              null -> false.also {
                log.info("no database entry for ${type.name} ($id) against version ${context.version} for app ${context.deliveryConfig.application}")
              }
            }
          }
      }
    }
  }

  override fun allStarted(context: ArtifactInEnvironmentContext, type: ActionType): Boolean {
    val actions = if (type == VERIFICATION) {
      context.verifications
    } else {
      context.postDeployActions
    }
    return when {
      actions.isEmpty() -> true
      else -> {
        val states = getStates(context, type)
        // must have an entry for all defined actions, since that indicates they've started.
        log.info("${type.name} found entries for actions ${states.keys} against version ${context.version} for app ${context.deliveryConfig.application}")
        return actions.map { it.id }.containsAll(states.keys)
      }
    }
  }

  override fun getVerificationContextsWithStatus(deliveryConfig: DeliveryConfig, environment: Environment, status: ConstraintStatus): Collection<ArtifactInEnvironmentContext> =
    jooq.select(
      DELIVERY_ARTIFACT.REFERENCE,
      ACTION_STATE.ARTIFACT_VERSION
    )
      .from(ACTION_STATE)
      .join(DELIVERY_ARTIFACT)
      .on(DELIVERY_ARTIFACT.UID.eq(ACTION_STATE.ARTIFACT_UID))
      .join(ACTIVE_ENVIRONMENT)
      .on(ACTIVE_ENVIRONMENT.UID.eq(ACTION_STATE.ENVIRONMENT_UID))
      .join(DELIVERY_CONFIG)
      .on(DELIVERY_CONFIG.UID.eq(ACTIVE_ENVIRONMENT.DELIVERY_CONFIG_UID))
      .where(DELIVERY_CONFIG.NAME.eq(deliveryConfig.name))
      .and(ACTIVE_ENVIRONMENT.NAME.eq(environment.name))
      .and(ACTION_STATE.TYPE.eq(VERIFICATION))
      .and(ACTION_STATE.STATUS.eq(status))
      .fetch()
      .map { (artifactReference, version) ->
          ArtifactInEnvironmentContext(
            deliveryConfig = deliveryConfig,
            environmentName = environment.name,
            artifactReference = artifactReference,
            version = version)
      }
      .toList()

  /**
   * Query the repository for the states of multiple contexts.
   *
   * This call is semantically equivalent to
   *    contexts.map { context -> this.getStates(context) }
   *
   * However, it's implemented as a single query for efficiency.
   *
   * @param contexts a list of verification contexts to query for state
   * @return a list of maps of verification ids to states, in the same order as the contexts. If there are no
   *         verification states associated with a context, the resulting map will be empty.
   */
  //todo: remove once we switch to graphql api, because the new api loads verifications and actions in one call
  override fun getStatesBatch(
    contexts: List<ArtifactInEnvironmentContext>,
    type: ActionType
  ): List<Map<String, ActionState>> {
    /**
     * In-memory database table representation of the set of contexts we want to query for
     *
     * Columns:
     *   ind - index that encodes the original list order, to ensure results are in same order
     *   environment_name
     *   artifact_reference
     *   artifact_version
     */
    val contextTable = ContextTable(contexts, jooq)

    /**
     * This function guarantees that the number of output elements match the number of input elements.
     * So we use left joins on the givenVersions table
     */
    return contextTable.table?.let { ctxTable ->
      jooq.select(
        contextTable.IND,
        ACTION_STATE.ACTION_ID,
        ACTION_STATE.STATUS,
        ACTION_STATE.STARTED_AT,
        ACTION_STATE.ENDED_AT,
        ACTION_STATE.METADATA,
        ACTION_STATE.LINK
      )
        .from(ctxTable)
        .leftJoin(ACTIVE_ENVIRONMENT)
        .on(ACTIVE_ENVIRONMENT.NAME.eq(contextTable.ENVIRONMENT_NAME))
        .leftJoin(DELIVERY_CONFIG)
        .on(DELIVERY_CONFIG.UID.eq(ACTIVE_ENVIRONMENT.DELIVERY_CONFIG_UID))
        .leftJoin(DELIVERY_ARTIFACT)
        .on(DELIVERY_ARTIFACT.REFERENCE.eq(contextTable.ARTIFACT_REFERENCE))
        .leftJoin(ACTION_STATE)
        .on(ACTION_STATE.ARTIFACT_UID.eq(DELIVERY_ARTIFACT.UID))
        .and(ACTION_STATE.ARTIFACT_VERSION.eq(contextTable.ARTIFACT_VERSION))
        .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(DELIVERY_CONFIG.NAME))
        .and(ACTION_STATE.ENVIRONMENT_UID.eq(ACTIVE_ENVIRONMENT.UID))
        .and(ACTION_STATE.TYPE.eq(type))
        // execute the query
        .fetch()

        // sort the results by the "ind" (index) column, so that outputs are same order as inputs
        .groupBy { (index, _, _, _, _, _, _) -> index as Long }
        .toSortedMap()
        .values

        // convert List<Record> to Map<String, ActionState>, where the string is the verification id
        .map { records ->
          records
            // since we do a left join, there may be rows where there is no corresponding records in the
            // ACTION_STATE database, so we filter them out, which will result in an empty map
            .filter { (_, _, status, _, _, _) -> status != null }
            .associate { (_, action_id, status, started_at, ended_at, metadata, link) ->
              action_id to ActionState(status, started_at, ended_at, metadata, link)
            }
        }
        .toList()
    } ?: emptyList()
  }

  /**
   * Query the repository for the states of multiple contexts.
   *
   * This call is semantically equivalent to
   *    contexts.map { context -> this.getStates(context) }
   *
   * However, it's implemented as a single query for efficiency.
   *
   * @param contexts a list of artifact in environment contexts to query for state
   * @return a list of list of ActionStateFull, in the same order as the contexts.
   *         Each inner list corresponds to the context. If there are no
   *         action states associated with a context, the resulting list will be empty.
   */
  override fun getAllStatesBatch(contexts: List<ArtifactInEnvironmentContext>): List<List<ActionStateFull>> {
    /**
     * In-memory database table representation of the set of contexts we want to query for
     *
     * Columns:
     *   ind - index that encodes the original list order, to ensure results are in same order
     *   environment_name
     *   artifact_reference
     *   artifact_version
     */
    val contextTable = ContextTable(contexts, jooq)

    /**
     * This function guarantees that the number of output elements match the number of input elements.
     * So we use left joins on the givenVersions table
     */
    return contextTable.table?.let { ctxTable ->
      jooq.select(
        contextTable.IND,
        ACTION_STATE.ACTION_ID,
        ACTION_STATE.STATUS,
        ACTION_STATE.STARTED_AT,
        ACTION_STATE.ENDED_AT,
        ACTION_STATE.METADATA,
        ACTION_STATE.LINK,
        ACTION_STATE.TYPE
      )
        .from(ctxTable)
        .leftJoin(ACTIVE_ENVIRONMENT)
        .on(ACTIVE_ENVIRONMENT.NAME.eq(contextTable.ENVIRONMENT_NAME))
        .leftJoin(DELIVERY_CONFIG)
        .on(DELIVERY_CONFIG.UID.eq(ACTIVE_ENVIRONMENT.DELIVERY_CONFIG_UID))
        .leftJoin(DELIVERY_ARTIFACT)
        .on(DELIVERY_ARTIFACT.REFERENCE.eq(contextTable.ARTIFACT_REFERENCE))
        .leftJoin(ACTION_STATE)
        .on(ACTION_STATE.ARTIFACT_UID.eq(DELIVERY_ARTIFACT.UID))
        .and(ACTION_STATE.ARTIFACT_VERSION.eq(contextTable.ARTIFACT_VERSION))
        .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(DELIVERY_CONFIG.NAME))
        .and(ACTION_STATE.ENVIRONMENT_UID.eq(ACTIVE_ENVIRONMENT.UID))
        // execute the query
        .fetch()

        // sort the results by the "ind" (index) column, so that outputs are same order as inputs
        .groupBy { (index, _, _, _, _, _, _, _) -> index as Long }
        .toSortedMap()
        .values

        // convert List<Record> to List<ActionStateFull>
        .map { records ->
          records
            // since we do a left join, there may be rows where there is no corresponding records in the
            // ACTION_STATE database, so we filter them out, which will result in an empty map
            .filter { (_, _, status, _, _, _, _) -> status != null }
            .map { (_, action_id, status, started_at, ended_at, metadata, link, type) ->
              ActionStateFull(
                state = ActionState(status, started_at, ended_at, metadata, link),
                type = type,
                id = action_id
              )
            }
        }
        .toList()
    } ?: emptyList()
  }

  override fun updateState(
    context: ActionStateUpdateContext,
    status: ConstraintStatus
  )  {
    sqlRetry.withRetry(WRITE) {
      with(context) {
        jooq
          .update(ACTION_STATE)
          .set(ACTION_STATE.STATUS, status)
          .where(ACTION_STATE.ENVIRONMENT_UID.eq(environmentUid))
          .and(ACTION_STATE.TYPE.eq(actionType))
          .and(ACTION_STATE.ACTION_ID.eq(id))
          .and(ACTION_STATE.STATUS.eq(ConstraintStatus.PENDING))
          .execute()
      }
    }
  }

  private fun updateState(
    context: ArtifactInEnvironmentContext,
    id: String,
    status: ConstraintStatus,
    metadata: Map<String, Any?>,
    link: String?,
    type: ActionType
  ) {
    log.debug("Updating action state for ${context.shortName()}: $status")
    with(context) {
      jooq
        .insertInto(ACTION_STATE)
        .set(ACTION_STATE.STATUS, status)
        .set(ACTION_STATE.METADATA, metadata)
        .set(ACTION_STATE.LINK, link)
        .set(ACTION_STATE.STARTED_AT, currentTimestamp())
        .set(ACTION_STATE.TYPE, type)
        .run {
          if (status.complete) {
            set(ACTION_STATE.ENDED_AT, currentTimestamp())
          } else {
            setNull(ACTION_STATE.ENDED_AT)
          }
        }
        .set(ACTION_STATE.ENVIRONMENT_UID, environmentUid)
        .set(ACTION_STATE.ARTIFACT_UID, artifactUid)
        .set(ACTION_STATE.ARTIFACT_VERSION, version)
        .set(ACTION_STATE.ACTION_ID, id)
        .onDuplicateKeyUpdate()
        .set(ACTION_STATE.STATUS, status)
        .set(ACTION_STATE.LINK, link)
        .run {
          if (status.complete) {
            set(ACTION_STATE.ENDED_AT, currentTimestamp())
          } else {
            setNull(ACTION_STATE.ENDED_AT)
          }
        }
        .run {
          if (metadata.isNotEmpty()) {
            set(ACTION_STATE.METADATA, jsonMerge(ACTION_STATE.METADATA, metadata))
          } else {
            this
          }
        }
        .execute()
    }
  }

  override fun updateState(
    context: ArtifactInEnvironmentContext,
    action: Action,
    status: ConstraintStatus,
    metadata: Map<String, Any?>,
    link: String?
  ) = updateState(
    context,
    action.id,
    status,
    metadata,
    link,
    action.actionType
  )

  override fun resetState(context: ArtifactInEnvironmentContext, action: Action, user: String): ConstraintStatus {
    sqlRetry.withRetry(WRITE) {
      with(context) {
        jooq
          .update(ACTION_STATE)
          .set(ACTION_STATE.STATUS, ConstraintStatus.NOT_EVALUATED)
          .setNull(ACTION_STATE.LINK)
          .set(ACTION_STATE.STARTED_AT, currentTimestamp())
          .set(ACTION_STATE.METADATA, mapOf("retryRequestedBy" to user))
          .setNull(ACTION_STATE.ENDED_AT)
          .where(ACTION_STATE.ENVIRONMENT_UID.eq(environmentUid))
          .and(ACTION_STATE.ARTIFACT_UID.eq(artifactUid))
          .and(ACTION_STATE.ARTIFACT_VERSION.eq(version))
          .and(ACTION_STATE.ACTION_ID.eq(action.id))
          .execute()
      }
    }
    return ConstraintStatus.NOT_EVALUATED
  }

  override fun nextEnvironmentsForPostDeployAction(
    minTimeSinceLastCheck: Duration,
    limit: Int
  ): Collection<ArtifactInEnvironmentContext> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck)
    return sqlRetry.withRetry(WRITE) {
      jooq.inTransaction {
        nextEnvironmentQuery(cutoff, limit)
          .lockInShareMode(useLockingRead)
          .fetch()
          .onEach { (_, _, environmentUid, _, artifactUid, _, artifactVersion) ->
            insertInto(Tables.ENVIRONMENT_LAST_POST_DEPLOY)
              .set(Tables.ENVIRONMENT_LAST_POST_DEPLOY.ENVIRONMENT_UID, environmentUid)
              .set(Tables.ENVIRONMENT_LAST_POST_DEPLOY.ARTIFACT_UID, artifactUid)
              .set(Tables.ENVIRONMENT_LAST_POST_DEPLOY.ARTIFACT_VERSION, artifactVersion)
              .set(Tables.ENVIRONMENT_LAST_POST_DEPLOY.AT, now)
              .onDuplicateKeyUpdate()
              .set(Tables.ENVIRONMENT_LAST_POST_DEPLOY.ARTIFACT_VERSION, artifactVersion)
              .set(Tables.ENVIRONMENT_LAST_POST_DEPLOY.AT, now)
              .execute()
          }
      }
        .map { (_, deliveryConfigName, _, environmentName, _, artifactReference, artifactVersion) ->
          ArtifactInEnvironmentContext(
            deliveryConfigByName(deliveryConfigName),
            environmentName,
            artifactReference,
            artifactVersion
          )
        }
    }
  }

  /**
   * JOOQ-ified access to MySQL's `json_merge` function.
   *
   * Updates [field] with [values] retaining any existing entries that are not present in [values].
   * Any array properties are appended to existing arrays.
   *
   * @see https://dev.mysql.com/doc/refman/8.0/en/json-modification-functions.html#function_json-merge
   */
  @Suppress("UNCHECKED_CAST")
  private fun jsonMerge(field: Field<Map<String, Any?>>, values: Map<String, Any?>) =
    function<Map<String, Any?>>(
      "json_merge",
      field,
      value(objectMapper.writeValueAsString(values))
    )

  private inline fun <reified T> function(name: String, vararg arguments: Field<*>) =
    function(name, T::class.java, *arguments)

  private fun currentTimestamp() = clock.instant()

  private fun selectEnvironmentUid(deliveryConfig: DeliveryConfig, environment: Environment) =
    select(ACTIVE_ENVIRONMENT.UID)
      .from(DELIVERY_CONFIG, ACTIVE_ENVIRONMENT)
      .where(DELIVERY_CONFIG.NAME.eq(deliveryConfig.name))
      .and(ACTIVE_ENVIRONMENT.NAME.eq(environment.name))
      .and(ACTIVE_ENVIRONMENT.DELIVERY_CONFIG_UID.eq(DELIVERY_CONFIG.UID))

  private val ArtifactInEnvironmentContext.environmentUid: Select<Record1<String>>
    get() = selectEnvironmentUid(deliveryConfig, environment)

  private val ActionStateUpdateContext.environmentUid: Select<Record1<String>>
    get() = selectEnvironmentUid(deliveryConfig, environment)

  private val ArtifactInEnvironmentContext.artifactUid: Select<Record1<String>>
    get() = select(DELIVERY_ARTIFACT.UID)
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfig.name))
      .and(DELIVERY_ARTIFACT.REFERENCE.eq(artifactReference))

  /**
   * Helper class for [getVerificationStatesBatch]
   *
   * This class enables the caller to construct a [Table] object from a list of [contexts].
   * The table is accessed via the [table] property.
   *
   * This table object does not correspond to an actual table in keel's database. Instead, it constructs
   * a query from the [contexts] that can be used as a subselect. The query would be used like this
   *
   * ```
   * SELECT ...
   * FROM
   * (
   * -- empty dummy record added to provide column names
   * SELECT NULL ind, NULL environment_name, NULL artifact_reference, NULL artifact_version FROM dual WHERE 1 = 0 UNION ALL
   * -- the actual values
   * SELECT 0, "staging", "myapp", "myapp-h123-v23.4" FROM dual UNION ALL
   * SELECT 1, "staging", "myapp", "myapp-h124-v23.5" FROM dual UNION ALL
   * SELECT 2, "staging", "myapp", "myapp-h124-v23.6"
   * ) action_contexts
   * ...
   * ```
   *
   * Note that `dual` is a dummy table, c.f.: https://en.wikipedia.org/wiki/DUAL_table
   *
   * The query essentially emulates the VALUES() table constructor, which we can't use because it's not supported in MySQL 5.7:
   * https://www.jooq.org/doc/3.0/manual/sql-building/table-expressions/values/
   */
  @Suppress("PropertyName")
  private class ContextTable(
    val contexts: List<ArtifactInEnvironmentContext>,
    val jooq: DSLContext
  ) {
    val alias = "action_contexts"

    private val ind = "ind"
    private val environmentName = "environment_name"
    private val artifactReference = "artifact_reference"
    private val artifactVersion = "artifact_version"

    fun <T> typedField(s : String, t: Class<T>) : Field<T> = field(name(alias, s), t)

    // These behave like regular jOOQ table field names when building SQL queries

    val IND  = typedField(ind, Long::class.java)
    val ENVIRONMENT_NAME = typedField(environmentName, String::class.java)
    val ARTIFACT_REFERENCE = typedField(artifactReference, String::class.java)
    val ARTIFACT_VERSION = typedField(artifactVersion, String::class.java)

    /**
     * return a jOOQ table that contains the [contexts] data represented as a table that can be selected against
     *
     * null if there are no contexts
     */
    val table : Table<Record4<Int, String, String, String>>?
      get() =
        contexts // List<ArtifactInEnvironmentContext>
          // Creates a SELECT statement from each element of [contexts], where every column is a constant. e.g.:
          // SELECT 0, "staging", "myapp", "myapp-h123-v23.4" FROM dual
          .mapIndexed { idx, context -> jooq.select(inline(idx).`as`(ind),
                                              inline(context.environmentName).`as`(environmentName),
                                              inline(context.artifactReference).`as`(artifactReference),
                                              inline(context.version).`as`(artifactVersion)) as SelectOrderByStep<Record4<Int, String, String, String>> }

          // Apply UNION ALL to the list of SELECT statements so they form a single query
          .reduceOrNull { s1, s2 -> s1.unionAll(s2) } // SelectOrderByStep<Record4<Int, String, String, String>>?
          // Convert the result to a [Table] object
          ?.asTable(alias, ind, environmentName, artifactReference, artifactVersion)
  }
}
