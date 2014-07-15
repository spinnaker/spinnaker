/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.astyanax

import com.netflix.astyanax.Keyspace
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class AstyanaxComponentsSpec extends Specification {

    void setupSpec() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")
    }

    def "should run embedded cassandra #description"() {
        given:
        properties.each { k, v -> System.setProperty(k, v) }

        and:
        def ctx = createContext()

        expect:
        ctx.getBean(AstyanaxComponents.EmbeddedCassandraRunner)

        cleanup:
        ctx.close()

        and:
        properties.each { k, v -> System.clearProperty(k) }

        where:
        description             | properties
        "by default"            | [:]
        "if explicitly enabled" | ["cassandra.embedded": "true"]
    }

    def "should not run embedded cassandra #description"() {
        given:
        properties.each { k, v -> System.setProperty(k, v) }

        and:
        def ctx = createContext(context)

        when:
        ctx.getBean(AstyanaxComponents.EmbeddedCassandraRunner)

        then:
        thrown NoSuchBeanDefinitionException

        cleanup:
        ctx.close()

        and:
        properties.each { k, v -> System.clearProperty(k) }

        where:
        description                                                 | properties
        "if explicitly disabled"                                    | ["cassandra.embedded": "false"]
        "if cassandra host is non-local"                            | ["cassandra.host": "54.243.116.211"]
        "if cassandra host is non-local even if explicitly enabled" | ["cassandra.embedded": "true", "cassandra.host": "54.243.116.211"]
        "if keyspace is not enabled"                                | [:]

        context = (description == "if keyspace is not enabled" ? CassandraContextNoKeyspace : CassandraContext)
    }

    private AnnotationConfigApplicationContext createContext(Class testContext = CassandraContext) {
        def configs = []
        configs << testContext
        configs << AstyanaxComponents
        new AnnotationConfigApplicationContext(configs as Class[])
    }

    @Configuration
    static class CassandraContext {
        @Bean
        static PropertySourcesPlaceholderConfigurer ppc() {
            new PropertySourcesPlaceholderConfigurer()
        }

        @Bean
        Keyspace keyspace(AstyanaxKeyspaceFactory factory) {
            factory.getKeyspace("test", "test")
        }
    }

    @Configuration
    static class CassandraContextNoKeyspace {
        @Bean
        static PropertySourcesPlaceholderConfigurer ppc() {
            new PropertySourcesPlaceholderConfigurer()
        }
    }
}
