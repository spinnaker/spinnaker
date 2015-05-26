'use strict';

angular.module('spinnaker.pipelines.stage.bake', [
  'spinnaker.pipelines.stage',
  'spinnaker.pipelines.stage.core',
  'spinnaker.pipelines.stage.bake.executionDetails.controller',
  'spinnaker.providerSelection.directive',
  'spinnaker.account.service',
  'spinnaker.utils.lodash',
]);
