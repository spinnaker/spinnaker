'use strict';

angular.module('spinnaker.pipelines.stage.deploy', [
  'spinnaker.pipelines.stage.deploy.details.controller',
  'spinnaker.pipelines.stage',
  'spinnaker.pipelines.stage.core',
  'spinnaker.deploymentStrategy',
  'spinnaker.utils.lodash',
  'spinnaker.serverGroup.read.service',
  'spinnaker.aws.serverGroupCommandBuilder.service',
]);
