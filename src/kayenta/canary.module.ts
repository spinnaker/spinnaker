import { module } from 'angular';

import {
  APPLICATION_DATA_SOURCE_REGISTRY,
  ApplicationDataSourceRegistry,
} from '@spinnaker/core';

import { CANARY_DATA_SOURCE } from 'kayenta/canary.dataSource';
import { CANARY_STAGES } from 'kayenta/stages/stages.module';
import { CANARY_STATES } from 'kayenta/canary.states';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function (key) {
  templates(key);
});

export const KAYENTA_MODULE = 'spinnaker.kayenta';
module(KAYENTA_MODULE, [
  APPLICATION_DATA_SOURCE_REGISTRY,
  CANARY_DATA_SOURCE,
  CANARY_STAGES,
  CANARY_STATES,
]).run((applicationDataSourceRegistry: ApplicationDataSourceRegistry) => {
  // Should be dropped when deck-kayenta is a library (not running as its own app).
  applicationDataSourceRegistry.setDataSourceOrder([
    'executions', 'serverGroups', 'tasks', 'canary', 'loadBalancers', 'securityGroups', 'config'
  ]);
});
