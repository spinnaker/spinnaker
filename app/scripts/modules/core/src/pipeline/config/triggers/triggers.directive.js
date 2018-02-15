'use strict';

import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';
import { UUIDGenerator } from 'core/utils/uuid.service';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.trigger.triggersDirective', [
  PIPELINE_CONFIG_PROVIDER,
])
  .directive('triggers', function() {
    return {
      restrict: 'E',
      scope: {
        pipeline: '=',
        application: '='
      },
      controller: 'triggersCtrl',
      controllerAs: 'triggersCtrl',
      templateUrl: require('./triggers.html')
    };
  })
  .controller('triggersCtrl', function($scope, pipelineConfig) {
    this.addTrigger = function() {
      var triggerTypes = pipelineConfig.getTriggerTypes(),
          newTrigger = {enabled: true};
      if (!$scope.pipeline.triggers) {
        $scope.pipeline.triggers = [];
      }

      if (triggerTypes.length === 1) {
        newTrigger.type = triggerTypes[0].key;
      }
      $scope.pipeline.triggers.push(newTrigger);
    };

    this.defaultArtifact = () => ({
      kind: 'custom'
    });

    this.removeExpectedArtifact = (pipeline, expectedArtifact) => {
      if (!pipeline.expectedArtifacts) {
        return;
      }

      pipeline.expectedArtifacts = pipeline.expectedArtifacts
        .filter(a => a.id !== expectedArtifact.id);

      if (!pipeline.triggers) {
        return;
      }

      pipeline.triggers
        .forEach(t => t.expectedArtifactIds = t.expectedArtifactIds
          .filter(eid => expectedArtifact.id !== eid));
    };

    this.addArtifact = () => {
      const newArtifact = {
        matchArtifact: this.defaultArtifact(),
        usePriorExecution: false,
        useDefaultArtifact: false,
        defaultArtifact: this.defaultArtifact(),
        id: UUIDGenerator.generateUuid()
      };

      if (!$scope.pipeline.expectedArtifacts) {
        $scope.pipeline.expectedArtifacts = [];
      }
      $scope.pipeline.expectedArtifacts.push(newArtifact);
    };

  });
