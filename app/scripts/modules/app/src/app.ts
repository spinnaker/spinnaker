/* eslint-disable @spinnaker/import-sort */
import 'jquery'; // ensures jQuery is loaded before Angular so Angular does not use jqlite
import 'rxjs-compat';
import { module } from 'angular';
import './strictDi';

import { CORE_MODULE } from '@spinnaker/core';
import { DOCKER_MODULE } from '@spinnaker/docker';
import { AMAZON_MODULE } from '@spinnaker/amazon';
import { APPENGINE_MODULE } from '@spinnaker/appengine';
import { GOOGLE_MODULE } from '@spinnaker/google';
import { CANARY_MODULE } from './canary/canary.module';
import { KUBERNETES_MODULE } from '@spinnaker/kubernetes';
import { ORACLE_MODULE } from '@spinnaker/oracle';
import { KAYENTA_MODULE } from '@spinnaker/kayenta';
import { TITUS_MODULE } from '@spinnaker/titus';
import { ECS_MODULE } from '@spinnaker/ecs';
import '@spinnaker/cloudfoundry';

module('netflix.spinnaker', [
  CORE_MODULE,
  AMAZON_MODULE,
  GOOGLE_MODULE,
  ECS_MODULE,
  DOCKER_MODULE,
  ORACLE_MODULE,
  APPENGINE_MODULE,
  CANARY_MODULE,
  KUBERNETES_MODULE,
  KAYENTA_MODULE,
  TITUS_MODULE,
]);
