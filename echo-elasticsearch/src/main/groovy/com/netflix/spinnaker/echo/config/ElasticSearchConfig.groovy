package com.netflix.spinnaker.echo.config

import static org.elasticsearch.node.NodeBuilder.nodeBuilder

import io.searchbox.client.JestClient
import io.searchbox.client.JestClientFactory
import io.searchbox.client.config.HttpClientConfig
import org.elasticsearch.client.Client
import org.elasticsearch.node.Node
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Elastic Search configuration for Embedded Jest Client
 */
@Configuration
class ElasticSearchConfig {

    @Bean
    @ConditionalOnMissingBean(JestClient)
    JestClient manufacture() {
        Node node = nodeBuilder().local(true).node()
        Client client = node.client()
        JestClientFactory factory = new JestClientFactory()

        factory.setHttpClientConfig(
            new HttpClientConfig.Builder("http://localhost:9200").multiThreaded(true).build())
        factory.object
    }

}
