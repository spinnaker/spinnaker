package com.netflix.front50

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

/**
 * Created by aglover on 4/23/14.
 */
@Component
class AWSConnectionFactory {

    @Bean
    public AmazonSimpleDB manufacture(AWSCredentialsProvider provider) {
        return new AmazonSimpleDBClient(provider);
    }
}