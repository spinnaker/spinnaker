require('jquery'); // ensures jQuery is loaded before Angular so Angular does not use jqlite
import { module } from 'angular';

import { CORE_MODULE } from '@spinnaker/core';
import { DOCKER_MODULE } from '@spinnaker/docker';
import { AMAZON_MODULE } from '@spinnaker/amazon';
import { APPENGINE_MODULE } from '@spinnaker/appengine';
import { GOOGLE_MODULE } from '@spinnaker/google';
import { OPENSTACK_MODULE } from '@spinnaker/openstack';
import { CANARY_MODULE } from './modules/canary/canary.module';
import { KUBERNETES_V1_MODULE, KUBERNETES_V2_MODULE } from '@spinnaker/kubernetes';
import { ORACLE_MODULE } from '@spinnaker/oracle';
import { KAYENTA_MODULE } from '@spinnaker/kayenta';
import { TITUS_MODULE } from '@spinnaker/titus';
import { ECS_MODULE } from '@spinnaker/ecs';
import { CLOUD_FOUNDRY_MODULE } from '@spinnaker/cloudfoundry';

module('netflix.spinnaker', [
  CORE_MODULE,
  AMAZON_MODULE,
  GOOGLE_MODULE,
  ECS_MODULE,
  CLOUD_FOUNDRY_MODULE,
  require('./modules/azure/azure.module.js').name,
  KUBERNETES_V1_MODULE,
  OPENSTACK_MODULE,
  DOCKER_MODULE,
  ORACLE_MODULE,
  require('./modules/dcos/dcos.module.js').name,
  APPENGINE_MODULE,
  CANARY_MODULE,
  KUBERNETES_V2_MODULE,
  KAYENTA_MODULE,
  TITUS_MODULE,
]);
