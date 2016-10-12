/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis.client.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.google.gson.annotations.SerializedName
import com.netflix.spinnaker.igor.build.model.GenericParameterDefinition
import groovy.transform.CompileStatic
import org.simpleframework.xml.Default

@Default
@CompileStatic
@JsonInclude(JsonInclude.Include.NON_NULL)
class Config {

    @SerializedName("global_env")
    List<Object> globalEnv

    List<String> env = ["SPINNAKER_TRIGGERED=true"]

    Config(Map<String, String> environmentMap) {
        globalEnv = environmentMap.collect { key, value ->
            (Object) "${key}=${value}".toString()
        }
    }

    public List<GenericParameterDefinition> getParameterDefinitionList() {
        (globalEnv ? globalEnv.findAll{it instanceof String}.collect {
            String tmpGlobalEnv = (String) it
            def parts = tmpGlobalEnv.tokenize('=')
            new GenericParameterDefinition(parts[0], parts.drop(1).join('='))
        } : []) as List<GenericParameterDefinition>
    }

    public List<Object> getSecrets() {
        globalEnv? globalEnv.findAll{!(it instanceof String)}.collect{it} : []
    }
}
