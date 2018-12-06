/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.cats.cluster

import com.netflix.spinnaker.cats.cluster.DefaultNodeIdentity
import com.netflix.spinnaker.cats.redis.test.NetworkUnavailableCheck
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({NetworkUnavailableCheck.networkUnavailable()})
class DefaultNodeIdentitySpec extends Specification {

    def 'should resolve to valid network interface'() {
        when:
        def serverSocket = new ServerSocket(0)
        def id = new DefaultNodeIdentity('127.0.0.1', serverSocket.getLocalPort())

        then:
        id.nodeIdentity != null
        !id.nodeIdentity.contains(DefaultNodeIdentity.UNKNOWN_HOST)

        cleanup:
        serverSocket.close()
    }

    def 'should refresh when unknown host'() {
        setup:
        def serverSocket = new ServerSocket(0)
        def localPort = serverSocket.getLocalPort()
        serverSocket.close()

        when:
        def id = new DefaultNodeIdentity('localhost', localPort, 10)

        then:
        id.nodeIdentity != null
        id.nodeIdentity.contains(DefaultNodeIdentity.UNKNOWN_HOST)

        when:
        serverSocket = new ServerSocket(localPort)

        then:
        Thread.currentThread().sleep(100)
        id.nodeIdentity != null
        !id.nodeIdentity.contains(DefaultNodeIdentity.UNKNOWN_HOST)

        cleanup:
        serverSocket.close()
    }
}
