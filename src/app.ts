import { module } from 'angular';

import { AMAZON_MODULE } from '@spinnaker/amazon';
import { CORE_MODULE, ApplicationDataSourceRegistry } from '@spinnaker/core';
import { KAYENTA_MODULE } from 'kayenta/canary.module';

module('netflix.spinnaker', [
  AMAZON_MODULE,
  CORE_MODULE,
  KAYENTA_MODULE,
]).run(() => {
  'ngInject';
  ApplicationDataSourceRegistry.setDataSourceOrder([
    'executions', 'serverGroups', 'tasks', 'loadBalancers', 'securityGroups', 'canaryConfigs', 'config'
  ]);
});
