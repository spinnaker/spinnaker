'use strict';

import { ArtifactReferenceService } from 'core/artifact/ArtifactReferenceService';
import { ExpectedArtifactService } from 'core/artifact/expectedArtifact.service';
import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';

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
  .controller('triggersCtrl', ['$scope', function($scope) {
    this.showProperties = SETTINGS.feature.quietPeriod || SETTINGS.feature.managedServiceAccounts;
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

    /**
     * PageNavigatorComponent relies on the ordering of items in the pages array of PageNavigationState.
     * PageNavigationState pages are registered in the init of each <page-section>.
     * Using <render-if-feature> / ng-if causes a <page-section> to init out of order with respect to html layout.
     * Alternatively, checkFeatureFlag allows for init to happen and for the <page-section> to check for visibilty.
     * https://github.com/spinnaker/spinnaker/issues/3970
     */

    this.checkFeatureFlag = flag => !!SETTINGS.feature[flag];
  }]);
