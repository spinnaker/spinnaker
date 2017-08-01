import { module } from 'angular';

import { CORE_MODULE } from '@spinnaker/core';
import { DOCKER_MODULE } from '@spinnaker/docker';
import { AMAZON_MODULE } from '@spinnaker/amazon';
import { APPENGINE_MODULE } from './modules/appengine/appengine.module';
import { GOOGLE_MODULE } from '@spinnaker/google';
import { CANARY_MODULE } from './modules/canary/canary.module';

module('netflix.spinnaker', [
  CORE_MODULE,
  AMAZON_MODULE,
  GOOGLE_MODULE,
  require('./modules/cloudfoundry/cf.module.js'),
  require('./modules/azure/azure.module.js'),
  require('./modules/kubernetes/kubernetes.module.js'),
  require('./modules/openstack/openstack.module.js'),
  DOCKER_MODULE,
  require('./modules/oracle/oraclebmcs.module.js'),
  require('./modules/dcos/dcos.module.js'),
  APPENGINE_MODULE,
  CANARY_MODULE,
]);
