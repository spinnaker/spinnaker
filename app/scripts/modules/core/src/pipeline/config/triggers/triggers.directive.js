'use strict';

import { ArtifactReferenceService } from 'core/artifact/ArtifactReferenceService';
import { ExpectedArtifactService } from 'core/artifact/expectedArtifact.service';
import { Registry } from 'core/registry';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.trigger.triggersDirective', [])
  .directive('triggers', function() {
    return {
      restrict: 'E',
      scope: {
        pipeline: '=',
        application: '=',
        fieldUpdated: '<',
      },
      controller: 'triggersCtrl',
      controllerAs: 'triggersCtrl',
      templateUrl: require('./triggers.html'),
    };
  })
  .controller('triggersCtrl', function($scope) {
    this.addTrigger = function() {
      var triggerTypes = Registry.pipeline.getTriggerTypes(),
        newTrigger = { enabled: true };
      if (!$scope.pipeline.triggers) {
        $scope.pipeline.triggers = [];
      }

      if (triggerTypes.length === 1) {
        newTrigger.type = triggerTypes[0].key;
      }
      $scope.pipeline.triggers.push(newTrigger);
    };

    this.removeExpectedArtifact = (pipeline, expectedArtifact) => {
      if (!pipeline.expectedArtifacts) {
        return;
      }

      pipeline.expectedArtifacts = pipeline.expectedArtifacts.filter(a => a.id !== expectedArtifact.id);

      if (!pipeline.triggers) {
        return;
      }

      pipeline.triggers.forEach(t => {
        if (t.expectedArtifactIds) {
          t.expectedArtifactIds = t.expectedArtifactIds.filter(eid => expectedArtifact.id !== eid);
        }
      });

      ArtifactReferenceService.removeReferenceFromStages(expectedArtifact.id, pipeline.stages);
    };

    this.addArtifact = () => {
      ExpectedArtifactService.addNewArtifactTo($scope.pipeline);
    };
  });
