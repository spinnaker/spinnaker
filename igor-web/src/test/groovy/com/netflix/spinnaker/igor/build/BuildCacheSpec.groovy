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

package com.netflix.spinnaker.igor.build

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.config.IgorConfig
import com.netflix.spinnaker.igor.config.JedisConfig
import com.netflix.spinnaker.igor.config.JenkinsConfig
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.igor.jenkins.network.Network.isReachable

@WebAppConfiguration
@ContextConfiguration(classes = [TestConfiguration])
@SuppressWarnings(['DuplicateNumberLiteral', 'UnnecessaryBooleanExpression', 'DuplicateListLiteral'])
@Slf4j
@IgnoreIf( {!isReachable('redis://localhost:6379')} )
class BuildCacheSpec extends Specification {

    @Autowired
    BuildCache cache

    @Autowired
    JedisPool jedisPool

    final master = 'master'
    final test = 'test'

    void setupSpec() {
        System.setProperty('netflix.environment', 'test')
        System.setProperty('services.echo.baseUrl', 'none')
    }

    void cleanup() {
        jedisPool.resource.withCloseable { Jedis resource ->
            resource.flushDB()
        }
    }

    void 'new build numbers get overridden'() {
        when:
        cache.setLastBuild(master, 'job1', 78, true)

        then:
        cache.getLastBuild(master, 'job1').lastBuildLabel == 78

        when:
        cache.setLastBuild(master, 'job1', 80, false)

        then:
        cache.getLastBuild(master, 'job1').lastBuildLabel == 80
    }

    void 'statuses get overridden'() {
        when:
        cache.setLastBuild(master, 'job1', 78, true)

        then:
        cache.getLastBuild(master, 'job1').lastBuildBuilding == true

        when:
        cache.setLastBuild(master, 'job1', 78, false)

        then:
        cache.getLastBuild(master, 'job1').lastBuildBuilding == false

    }

    void 'when value is not found, an empty collection is returned'() {
        expect:
        cache.getLastBuild('notthere', 'job1') == [:]
    }

    void 'can set builds for multiple masters'() {
        when:
        cache.setLastBuild(master, 'job1', 78, true)
        cache.setLastBuild('example2', 'job1', 88, true)

        then:
        cache.getLastBuild(master, 'job1').lastBuildLabel == 78
        cache.getLastBuild('example2', 'job1').lastBuildLabel == 88
    }

    void 'correctly retrieves all jobsNames for a master'() {
        when:
        cache.setLastBuild(master, 'job1', 78, true)
        cache.setLastBuild(master, 'job2', 11, false)
        cache.setLastBuild(master, 'blurb', 1, false)

        then:
        cache.getJobNames(master) == ['blurb', 'job1', 'job2']
    }

    void 'can remove details for a build'() {
        when:
        cache.setLastBuild(master, 'job1', 78, true)
        cache.remove(master, 'job1')

        then:
        cache.getLastBuild(master, 'job1') == [:]
    }

    @Unroll
    void 'retrieves all matching jobs for typeahead #query'() {
        when:
        cache.setLastBuild(master, 'job1', 1, true)
        cache.setLastBuild(test, 'job1', 1, false)
        cache.setLastBuild(master, 'job2', 1, false)
        cache.setLastBuild(test, 'job3', 1, false)

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
        BuildCache secondInstance = new BuildCache(jedisPool: jedisPool)
        def altCfg = new IgorConfigurationProperties()
        altCfg.spinnaker.jedis.prefix = 'newPrefix'
        secondInstance.igorConfigurationProperties = altCfg
        secondInstance.setLastBuild(master, 'job1', 1, false)

        then:
        secondInstance.getJobNames(master) == ['job1']
        cache.getJobNames(master) == []

        when:
        cache.remove(master, 'job1')

        then:
        secondInstance.getJobNames(master) == ['job1']
    }

    @Configuration
    @EnableAutoConfiguration(exclude = [GroovyTemplateAutoConfiguration])
    @EnableConfigurationProperties(IgorConfigurationProperties)
    @Import([BuildCache, JenkinsConfig, IgorConfig, JedisConfig])
    static class TestConfiguration {

    }

}
