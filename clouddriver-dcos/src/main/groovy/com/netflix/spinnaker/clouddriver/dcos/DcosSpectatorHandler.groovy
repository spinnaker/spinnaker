/*
 * Copyright 2018 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Registry
import mesosphere.dcos.client.DCOS
import mesosphere.dcos.client.DCOSException

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

class DcosSpectatorHandler implements InvocationHandler {
    private final DCOS dcosClient
    private final Registry registry
    private final Clock clock
    private final String accountName
    private final String regionName

    DcosSpectatorHandler(DCOS dcosClient, String accountName, String regionName, Registry registry) {
        this.dcosClient = dcosClient
        this.accountName = accountName
        this.regionName = regionName
        this.clock = registry.clock()
        this.registry = registry
    }

    @Override
    Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result = null
        Throwable failure = null
        long startTime = clock.monotonicTime()

        try {
            result = method.invoke(dcosClient, args)
        } catch (InvocationTargetException e) {
            failure = e.getCause()
        } catch (Exception e) {
            failure = e
        } finally {
            Map<String, String> tags = new HashMap<>()

            tags.put("method", method.name)
            tags.put("account", accountName)
            tags.put("region", regionName)

            if (failure == null) {
                tags.put("success", "true")
            } else {
                tags.put("success", "false")
                tags.put("reason", failure.getClass().getSimpleName())
            }

            registry.timer(registry.createId("dcos.api", tags))
                    .record(clock.monotonicTime() - startTime, TimeUnit.NANOSECONDS)
        }

        if (failure != null) {
            throw failure
        } else {
            return result
        }
    }
}
