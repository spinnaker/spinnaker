/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.jenkins

import static com.netflix.spinnaker.igor.jenkins.network.Network.isReachable

import com.netflix.spinnaker.igor.Main
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

@WebAppConfiguration
@ContextConfiguration(classes = [Main])
@SuppressWarnings(['DuplicateNumberLiteral', 'UnnecessaryBooleanExpression', 'DuplicateListLiteral'])
@Slf4j
@IgnoreIf( {!isReachable('redis://localhost:6379')} )
class JenkinsCacheSpec extends Specification {

    @Autowired
    JenkinsCache cache

    @Autowired
    JedisPool jedisPool

    final master = 'master'
    final test = 'test'

    void setupSpec() {
        System.setProperty('netflix.environment', 'test')
        System.setProperty('spinnaker.echo.host', 'none')
    }

    void cleanup() {
        Jedis resource = jedisPool.resource
        resource.flushDB()
        jedisPool.returnResource(resource)
    }

    void 'new build numbers get overridden'() {
        when:
        cache.setLastBuild(master, 'job1', 78, 'running')

        then:
        cache.getLastBuild(master, 'job1').lastBuildLabel == 78

        when:
        cache.setLastBuild(master, 'job1', 80, 'running')

        then:
        cache.getLastBuild(master, 'job1').lastBuildLabel == 80
    }

    void 'statuses get overridden'() {
        when:
        cache.setLastBuild(master, 'job1', 78, 'running')

        then:
        cache.getLastBuild(master, 'job1').lastBuildStatus == 'running'

        when:
        cache.setLastBuild(master, 'job1', 78, 'failed')

        then:
        cache.getLastBuild(master, 'job1').lastBuildStatus == 'failed'

    }

    void 'when value is not found, an empty collection is returned'() {
        expect:
        cache.getLastBuild('notthere', 'job1') == [:]
    }

    void 'can set builds for multiple masters'() {
        when:
        cache.setLastBuild(master, 'job1', 78, 'success')
        cache.setLastBuild('example2', 'job1', 88, 'success')

        then:
        cache.getLastBuild(master, 'job1').lastBuildLabel == 78
        cache.getLastBuild('example2', 'job1').lastBuildLabel == 88
    }

    void 'correctly retrieves all jobsNames for a master'() {
        when:
        cache.setLastBuild(master, 'job1', 78, 'success')
        cache.setLastBuild(master, 'job2', 11, 'fail')
        cache.setLastBuild(master, 'blurb', 1, 'running')

        then:
        cache.getJobNames(master) == ['blurb', 'job1', 'job2']
    }

    void 'can remove details for a build'() {
        when:
        cache.setLastBuild(master, 'job1', 78, 'success')
        cache.remove(master, 'job1')

        then:
        cache.getLastBuild(master, 'job1') == [:]
    }

    @Unroll
    void 'retrieves all matching jobs for typeahead #query'() {
        when:
        cache.setLastBuild(master, 'job1', 1, 'success')
        cache.setLastBuild(test, 'job1', 1, 'fail')
        cache.setLastBuild(master, 'job2', 1, 'fail')
        cache.setLastBuild(test, 'job3', 1, 'fail')

        then:
        cache.getTypeaheadResults(query) == expected

        where:
        query  || expected
        'job'  || ['master:job1', 'master:job2', 'test:job1', 'test:job3']
        'job1' || ['master:job1', 'test:job1']
        'ob1'  || ['master:job1', 'test:job1']
        'B2'   || ['master:job2']
        '3'    || ['test:job3']
        'nope' || []
    }

    void 'a cache with another prefix does not pollute the current cache'() {
        when:
        JenkinsCache secondInstance = new JenkinsCache(jedisPool: jedisPool)
        secondInstance.prefix = 'newPrefix'
        secondInstance.setLastBuild(master, 'job1', 1, 'success')

        then:
        secondInstance.getJobNames(master) == ['job1']
        cache.getJobNames(master) == []

        when:
        cache.remove(master, 'job1')

        then:
        secondInstance.getJobNames(master) == ['job1']
    }

}
