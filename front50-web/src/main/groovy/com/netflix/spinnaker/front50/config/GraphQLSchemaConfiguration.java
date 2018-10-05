/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.front50.config;

import com.netflix.spinnaker.front50.graphql.JsonScalarType;
import com.netflix.spinnaker.front50.graphql.datafetcher.PipelinesDataFetcher;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@Configuration
public class GraphQLSchemaConfiguration {

  @Bean
  RuntimeWiring runtimeWiring(PipelinesDataFetcher pipelinesDataFetcher) {
    return RuntimeWiring.newRuntimeWiring()
      .scalar(JsonScalarType.INSTANCE)
      .type("Query", builder -> builder
        .dataFetcher("version", new StaticDataFetcher("0.1"))
        .dataFetcher("pipelines", pipelinesDataFetcher)
        .defaultDataFetcher(new StaticDataFetcher("TODO"))
      )
      .type(newTypeWiring("Trigger").typeResolver(env -> (GraphQLObjectType) env.getSchema().getType("CronTrigger")))
      .build();
  }

  @Bean
  GraphQLSchema graphQLSchema(RuntimeWiring runtimeWiring) {
    File pipelineConfigSchema = new File(getClass().getResource("/graphql/pipelineConfig.graphqls").getFile());

    SchemaParser schemaParser = new SchemaParser();
    TypeDefinitionRegistry registry = schemaParser.parse(pipelineConfigSchema);

    SchemaGenerator schemaGenerator = new SchemaGenerator();
    GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(registry, runtimeWiring);

    return graphQLSchema;
  }
}
