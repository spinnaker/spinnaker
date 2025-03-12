package com.netflix.spinnaker.config

import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

/**
 * Used to auto-configure Fiat in the open-source implementation.
 */
@Configuration
@ConditionalOnProperty(name = ["keel.security.custom"], havingValue = "false", matchIfMissing = true)
@EnableFiatAutoConfig
class SecurityConfiguration
