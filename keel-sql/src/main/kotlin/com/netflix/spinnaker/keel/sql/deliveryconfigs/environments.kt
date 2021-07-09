package com.netflix.spinnaker.keel.sql.deliveryconfigs

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_VERSION
import com.netflix.spinnaker.keel.sql.SqlStorageContext
import com.netflix.spinnaker.keel.sql.fetchSingleInto
import java.time.Instant

/**
 * @return the number of versions of an environment that have been created since [time].
 */
fun SqlStorageContext.versionsCreatedSince(
  deliveryConfig: DeliveryConfig,
  environmentName: String,
  time: Instant
): Int =
  jooq
    .selectCount()
    .from(ENVIRONMENT_VERSION)
    .join(ENVIRONMENT).on(ENVIRONMENT.UID.eq(ENVIRONMENT_VERSION.ENVIRONMENT_UID))
    .join(DELIVERY_CONFIG).on(DELIVERY_CONFIG.UID.eq(ENVIRONMENT.DELIVERY_CONFIG_UID))
    .where(DELIVERY_CONFIG.NAME.eq(deliveryConfig.name))
    .and(ENVIRONMENT.NAME.eq(environmentName))
    .and(ENVIRONMENT_VERSION.CREATED_AT.ge(time))
    .fetchSingleInto<Int>()
