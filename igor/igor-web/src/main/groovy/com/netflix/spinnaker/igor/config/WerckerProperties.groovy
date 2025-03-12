/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.config

import com.netflix.spinnaker.fiat.model.resources.Permissions
import groovy.transform.CompileStatic
import org.hibernate.validator.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties

import javax.validation.Valid

/**
 * Helper class to map masters in properties file into a validated property map
 */
@CompileStatic
@ConfigurationProperties(prefix = 'wercker')
class WerckerProperties implements BuildServerProperties<WerckerProperties.WerckerHost> {
    @Valid
    List<WerckerHost> masters

    static class WerckerHost implements BuildServerProperties.Host {
        @NotEmpty
        String name

        @NotEmpty
        String address

        String user

        String token

        Integer itemUpperThreshold

        Permissions.Builder permissions = new Permissions.Builder()
    }
}
