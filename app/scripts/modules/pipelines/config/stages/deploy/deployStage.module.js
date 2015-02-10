'use strict';

angular.module('deckApp.pipelines.stage.deploy', [
  'deckApp.pipelines.stage.deploy.details.controller',
  'deckApp.pipelines.stage',
  'deckApp.pipelines.stage.core',
  'deckApp.deploymentStrategy',
  'deckApp.utils.lodash',
  'deckApp.serverGroup.read.service',
  'deckApp.aws.serverGroupCommandBuilder.service',
]);
