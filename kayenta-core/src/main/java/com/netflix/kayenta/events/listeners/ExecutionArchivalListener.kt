package com.netflix.kayenta.events.listeners

import com.netflix.kayenta.canary.CanaryExecutionStatusResponse
import com.netflix.kayenta.events.CanaryExecutionCompletedEvent
import com.netflix.kayenta.security.AccountCredentials
import com.netflix.kayenta.security.AccountCredentialsRepository
import com.netflix.kayenta.security.CredentialsHelper
import com.netflix.kayenta.storage.ObjectType
import com.netflix.kayenta.storage.StorageServiceRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
class ExecutionArchivalListener @Autowired
constructor(private val accountCredentialsRepository: AccountCredentialsRepository,
            private val storageServiceRepository: StorageServiceRepository)
    : ApplicationListener<CanaryExecutionCompletedEvent> {

    init {
        log.info("Loaded ExecutionArchivalListener")
    }

    override fun onApplicationEvent(event: CanaryExecutionCompletedEvent) {
        val response = event.canaryExecutionStatusResponse
        val storageAccountName = response.storageAccountName
        if (storageAccountName != null) {
            val resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                    AccountCredentials.Type.OBJECT_STORE,
                    accountCredentialsRepository)

            val storageService = storageServiceRepository
                    .getOne(resolvedStorageAccountName)
                    .orElseThrow { IllegalArgumentException("No storage service was configured; unable to archive results.") }

            storageService.storeObject<CanaryExecutionStatusResponse>(resolvedStorageAccountName, ObjectType.CANARY_RESULT_ARCHIVE, response.pipelineId, response)
        }
    }

    companion object {
        private val log: Logger = getLogger(ExecutionArchivalListener::class.java)
    }
}
