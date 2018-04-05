import { module } from 'angular';

import { CORE_MODULE, ApplicationDataSourceRegistry } from '@spinnaker/core';
import { AMAZON_MODULE } from '@spinnaker/amazon';
import { GOOGLE_MODULE } from '@spinnaker/google';
import { KUBERNETES_V1_MODULE, KUBERNETES_V2_MODULE } from '@spinnaker/kubernetes';
import { KAYENTA_MODULE } from 'kayenta/canary.module';

module('netflix.spinnaker', [
  CORE_MODULE,
  AMAZON_MODULE,
  GOOGLE_MODULE,
  KAYENTA_MODULE,
  KUBERNETES_V1_MODULE,
  KUBERNETES_V2_MODULE,
]).run((applicationDataSourceRegistry: ApplicationDataSourceRegistry) => {
  'ngInject';
  applicationDataSourceRegistry.setDataSourceOrder([
    'executions', 'serverGroups', 'tasks', 'loadBalancers', 'securityGroups', 'canaryConfigs', 'config'
  ]);
});
