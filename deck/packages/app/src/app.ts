/* eslint-disable @spinnaker/import-sort */
import 'rxjs-compat';
import { module } from 'angular';
import './strictDi';

import { CORE_MODULE } from '@spinnaker/core';
import '@spinnaker/docker';
import '@spinnaker/amazon';
import '@spinnaker/appengine';
import '@spinnaker/azure';
import { GOOGLE_MODULE } from '@spinnaker/google';
import { CANARY_MODULE } from './canary/canary.module';
import '@spinnaker/proxmox';
import '@spinnaker/kubernetes';
import '@spinnaker/oracle';
import '@spinnaker/kayenta';
import '@spinnaker/titus';
import { ECS_MODULE } from '@spinnaker/ecs';
import '@spinnaker/cloudrun';
import '@spinnaker/cloudfoundry';

module('netflix.spinnaker', [CORE_MODULE, GOOGLE_MODULE, ECS_MODULE, CANARY_MODULE]);
