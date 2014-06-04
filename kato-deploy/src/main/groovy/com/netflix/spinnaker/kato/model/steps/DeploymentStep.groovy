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
package com.netflix.spinnaker.kato.model.steps

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes([
        @Type(name = "CreateAsg", value = CreateAsgStep),
        @Type(name = "DeleteAsg", value = DeleteAsgStep),
        @Type(name = "DisableAsg", value = DisableAsgStep),
        @Type(name = "EnableAsg", value = EnableAsgStep),
        @Type(name = "Judgment", value = JudgmentStep),
        @Type(name = "Resize", value = ResizeStep),
        @Type(name = "Wait", value = WaitStep)
])
interface DeploymentStep {
}
