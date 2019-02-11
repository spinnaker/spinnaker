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
import com.netflix.kayenta.canary.CanaryScopeFactory;
import org.springframework.stereotype.Component;

@Component
public class WavefrontCanaryScopeFactory implements CanaryScopeFactory {

    public static long SECOND = 1;
    public static long MINUTE = 60 * SECOND;
    public static long HOUR = 60 * MINUTE;
    public static long DAY = 24 * HOUR;

    @Override
    public boolean handles(String serviceType) {
        return "wavefront".equals(serviceType);
    }

    @Override
    public CanaryScope buildCanaryScope(CanaryScope scope) {
        WavefrontCanaryScope wavefrontCanaryScope = new WavefrontCanaryScope();
        wavefrontCanaryScope.setScope(scope.getScope());
        wavefrontCanaryScope.setLocation(scope.getLocation());
        wavefrontCanaryScope.setStart(scope.getStart());
        wavefrontCanaryScope.setEnd(scope.getEnd());
        wavefrontCanaryScope.setStep(scope.getStep());
        wavefrontCanaryScope.setGranularity(generateGranularity(scope.getStep()));
        wavefrontCanaryScope.setExtendedScopeParams(scope.getExtendedScopeParams());
        return wavefrontCanaryScope;
    }

    private String generateGranularity(Long step) {
        if (step % DAY == 0) {
            return "d";
        }
        if (step % HOUR == 0) {
            return "h";
        }
        if (step % MINUTE == 0) {
            return "m";
        }
        return "s";
    }
}
