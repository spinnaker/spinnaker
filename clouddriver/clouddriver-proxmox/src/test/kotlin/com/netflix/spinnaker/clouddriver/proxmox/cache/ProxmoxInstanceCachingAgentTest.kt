package com.netflix.spinnaker.clouddriver.proxmox.cache

import com.netflix.spinnaker.clouddriver.kork.core.RetrySupport
import com.netflix.spinnaker.clouddriver.proxmox.proxmox.ProxmoxClient
import com.netflix.spinnaker.clouddriver.proxmox.proxmox.model.ProxmoxInstance
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration
import java.util.*
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotEmpty

@SpringBootTest(classes = [ProxmoxInstanceCachingAgent::class])
class ProxmoxInstanceCachingAgentTest {

    @MockitoBean
    private lateinit var proxmoxClient: ProxmoxClient

    @Autowired
    private lateinit var cachingAgent: ProxmoxInstanceCachingAgent

    @BeforeEach
    fun setUp() {
        clearInvocations(proxmoxClient)
    }

    @Test
    `should load instances into cache`() {
        val mockInstances = listOf(
            ProxmoxInstance(
                id = 101,
                name = "test-vm-101",
                nodeName = "proxmox-node-1",
                description = "Test VM",
                cpu = 2,
                memory = 4294967296L,
                diskSize = 21474836480L,
                running = true,
                startedAt = 1234567890000,
                stoppedAt = null,
                primaryIpAddress = "10.0.0.1",
                agentVersion = null,
                lastCacheTime = null,
                upgradedAt = null,
                userTags = ""
            )
        )

        `when`(proxmoxClient.listInstances()).thenReturn(mockInstances)

        val cache = cachingAgent.extractions.get()

        assertThat(cache.groupedValues).isNotEmpty()
        val instances = cache.groupedValues.values.flatten()
        assertThat(instances).isNotEmpty()
        assertThat(instances.size).isEqualTo(1)
    }

    @Test
    `should handle empty instance list`() {
        `when`(proxmoxClient.listInstances()).thenReturn(emptyList())

        val cache = cachingAgent.extractions.get()

        assertThat(cache.groupedValues).isEmpty()
    }

    @Test
    `should handle client exception`() {
        `when`(proxmoxClient.listInstances()).thenThrow(RuntimeException("API error"))

        val cache = cachingAgent.extractions.get()

        assertThat(cache.groupedValues).isEmpty()
    }
}