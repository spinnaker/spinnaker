/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.gate.config;

import com.netflix.spinnaker.gate.graphql.RootMutationResolver;
import com.netflix.spinnaker.gate.graphql.RootQueryResolver;
import com.netflix.spinnaker.gate.graphql.resolvers.EmptyNodeResolver;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:graphql.properties")
public class GraphQLConfig {

  @Bean
  public EmptyNodeResolver emptyNodeResolver() {
    return new EmptyNodeResolver();
  }

  @Bean
  public GraphQLQueryResolver queryResolver() {
    return new RootQueryResolver();
  }

  @Bean
  public GraphQLMutationResolver mutationResolver() {
    return new RootMutationResolver();
  }
}
