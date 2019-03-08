/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.scheduler.actions.pipeline

import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

/**
 * This ugly solution is needed to wire up the jobs. The scheduled-actions library does not know anything
 * about spring injection so if the {@code Action} classes in {@code com.netflix.spinnaker.scheduler.actions} package
 * need to use any of the beans, then those classes need to query this bean
 * factory and get the required beans. There are few ideas to improve this and this issue being tracked
 * here: https://github.com/spinnaker/scheduled-actions/issues/2
  */
@Component
@ConditionalOnExpression('${scheduler.enabled:false}')
class SchedulerBeanDependencies implements BeanFactoryAware {

    private static BeanFactory springBeanFactory

    @Override
    void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        springBeanFactory = beanFactory
    }

    static Object getBean(Class type) {
        return springBeanFactory.getBean(type)
    }
}
