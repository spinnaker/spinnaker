import { module } from 'angular';
import 'jquery';
import { KAYENTA_MODULE } from 'kayenta/canary.module';

import { AMAZON_MODULE } from '@spinnaker/amazon';
import { ApplicationDataSourceRegistry, CORE_MODULE } from '@spinnaker/core';

module('netflix.spinnaker', [AMAZON_MODULE, CORE_MODULE, KAYENTA_MODULE]).run(() => {
  'ngInject';
  ApplicationDataSourceRegistry.setDataSourceOrder([
    'executions',
    'serverGroups',
    'tasks',
    'loadBalancers',
    'securityGroups',
    'canaryConfigs',
    'config',
  ]);
});
