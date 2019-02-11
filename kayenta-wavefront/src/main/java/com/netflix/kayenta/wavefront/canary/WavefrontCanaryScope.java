/*
 * Copyright 2019 Intuit, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.kayenta.wavefront.canary;

import com.netflix.kayenta.canary.CanaryScope;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WavefrontCanaryScope extends CanaryScope {

    @NotNull
    private String granularity;

    public void setStepFromGranularity(String granularity) {
        if (granularity.equals("s")) {
            this.step = WavefrontCanaryScopeFactory.SECOND;
        }
        if (granularity.equals("m")) {
            this.step = WavefrontCanaryScopeFactory.MINUTE;
        }
        if (granularity.equals("h")) {
            this.step = WavefrontCanaryScopeFactory.HOUR;
        }
        if (granularity.equals("d")) {
            this.step = WavefrontCanaryScopeFactory.DAY;
        }
    }

}
