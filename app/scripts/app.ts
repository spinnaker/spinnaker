import { module } from 'angular';

import { CORE_MODULE } from '@spinnaker/core';
import { DOCKER_MODULE } from '@spinnaker/docker';
import { APPENGINE_MODULE } from './modules/appengine/appengine.module';
import { NETFLIX_MODULE } from './modules/netflix/netflix.module';

module('netflix.spinnaker', [
  NETFLIX_MODULE,
  CORE_MODULE,
  require('./modules/amazon/aws.module.js'),
  require('./modules/google/gce.module.js'),
  require('./modules/cloudfoundry/cf.module.js'),
  require('./modules/titus/titus.module.js'),
  require('./modules/azure/azure.module.js'),
  require('./modules/kubernetes/kubernetes.module.js'),
  require('./modules/openstack/openstack.module.js'),
  DOCKER_MODULE,
  require('./modules/oracle/oraclebmcs.module.js'),
  APPENGINE_MODULE,
]);
