import { module } from 'angular';

import { CORE_MODULE, ApplicationDataSourceRegistry } from '@spinnaker/core';
import { KAYENTA_MODULE } from 'kayenta/canary.module';

module('netflix.spinnaker', [
  CORE_MODULE,
  KAYENTA_MODULE,
]).run((applicationDataSourceRegistry: ApplicationDataSourceRegistry) => {
  'ngInject';
  applicationDataSourceRegistry.setDataSourceOrder([
    'executions', 'serverGroups', 'tasks', 'loadBalancers', 'securityGroups', 'canaryConfigs', 'config'
  ]);
});
