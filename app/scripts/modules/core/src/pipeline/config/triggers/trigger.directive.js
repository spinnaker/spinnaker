'use strict';

import * as React from 'react';
import * as ReactDOM from 'react-dom';

import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';
import { TRIGGER_ARTIFACT_CONSTRAINT_SELECTOR_REACT } from './artifacts';
import { ArtifactReferenceService } from '../../../artifact';

const angular = require('angular');

const removeUnusedExpectedArtifacts = function(pipeline) {
  // remove unused expected artifacts from the pipeline
  const artifacts = pipeline.expectedArtifacts;
  artifacts.forEach(artifact => {
    if (!pipeline.triggers.find(t => t.expectedArtifactIds && t.expectedArtifactIds.includes(artifact.id))) {
      pipeline.expectedArtifacts.splice(pipeline.expectedArtifacts.indexOf(artifact), 1);
      if (pipeline.expectedArtifacts.length === 0) {
        delete pipeline.expectedArtifacts;
      }
    }
    ArtifactReferenceService.removeReferenceFromStages(artifact.id, pipeline.stages);
  });
};

module.exports = angular
  .module('spinnaker.core.pipeline.config.trigger.triggerDirective', [TRIGGER_ARTIFACT_CONSTRAINT_SELECTOR_REACT])
  .directive('trigger', function() {
    return {
      restrict: 'E',
      require: '^pipelineConfigurer',
      scope: {
        trigger: '=',
        pipeline: '=',
        application: '=',
        fieldUpdated: '<',
      },
      controller: 'TriggerCtrl as triggerCtrl',
      templateUrl: require('./trigger.html'),
      link: function(scope, elem, attrs, pipelineConfigurerCtrl) {
        scope.pipelineConfigurerCtrl = pipelineConfigurerCtrl;
      },
    };
  })
  .controller('TriggerCtrl', [
    '$scope',
    '$element',
    '$compile',
    '$controller',
    '$templateCache',
    function($scope, $element, $compile, $controller, $templateCache) {
      let reactComponentMounted;
      var triggerTypes = Registry.pipeline.getTriggerTypes();
      $scope.options = triggerTypes;
      this.disableAutoTriggering = SETTINGS.disableAutoTriggering || [];

      this.removeTrigger = function(trigger) {
        var triggerIndex = $scope.pipeline.triggers.indexOf(trigger);
        $scope.pipeline.triggers.splice(triggerIndex, 1);
        if (this.checkFeatureFlag('artifactsRewrite')) {
          removeUnusedExpectedArtifacts($scope.pipeline);
        }
      };

      this.checkFeatureFlag = function(flag) {
        return !!SETTINGS.feature[flag];
      };

      this.changeExpectedArtifacts = function(expectedArtifacts, trigger) {
        $scope.$applyAsync(() => {
          trigger.expectedArtifactIds = expectedArtifacts;
          removeUnusedExpectedArtifacts($scope.pipeline);
        });
      };

      this.defineExpectedArtifact = function(expectedArtifact) {
        $scope.$applyAsync(() => {
          const expectedArtifacts = $scope.pipeline.expectedArtifacts;
          if (expectedArtifacts) {
            let editingArtifact = expectedArtifacts.findIndex(artifact => artifact.id === expectedArtifact.id);
            if (editingArtifact >= 0) {
              $scope.pipeline.expectedArtifacts[editingArtifact] = expectedArtifact;
            } else {
              expectedArtifacts.push(expectedArtifact);
            }
          } else {
            $scope.pipeline.expectedArtifacts = [expectedArtifact];
          }

          const expectedArtifactIds = $scope.trigger.expectedArtifactIds;
          if (expectedArtifactIds && !expectedArtifactIds.includes(expectedArtifact.id)) {
            expectedArtifactIds.push(expectedArtifact.id);
          } else {
            $scope.trigger.expectedArtifactIds = [expectedArtifact.id];
          }
        });
      };

      this.loadTrigger = () => {
        const triggerBodyNode = $element.find('.trigger-body').get(0);

        // clear existing contents
        if (reactComponentMounted) {
          ReactDOM.unmountComponentAtNode(triggerBodyNode);
          reactComponentMounted = false;
        }

        var type = $scope.trigger.type,
          triggerScope = $scope.$new();
        if (type) {
          if (this.disableAutoTriggering.includes(type)) {
            $scope.trigger.enabled = false;
          }
          var triggerConfig = triggerTypes.filter(function(config) {
            return config.key === type;
          });
          if (triggerConfig.length) {
            const config = triggerConfig[0];
            if (config.component) {
              // react
              const TriggerConfig = config.component;
              const renderTrigger = props =>
                ReactDOM.render(React.createElement(TriggerConfig, props), triggerBodyNode);

              const props = {
                application: $scope.application,
                pipelineId: $scope.pipeline.id,
                trigger: $scope.trigger,
                triggerUpdated: trigger => {
                  const triggerIndex = $scope.pipeline.triggers.indexOf($scope.trigger);
                  trigger = Object.assign(props.trigger, trigger);
                  $scope.pipeline.triggers[triggerIndex] = trigger;
                  $scope.trigger = trigger;
                  $scope.fieldUpdated();
                  renderTrigger(props);
                },
              };

              renderTrigger(props);
            } else if (config.templateUrl) {
              // angular
              const template = $templateCache.get(config.templateUrl);
              if (config.controller) {
                var ctrl = config.controller.split(' as ');
                var controller = $controller(ctrl[0], { $scope: triggerScope, trigger: $scope.trigger });
                if (ctrl.length === 2) {
                  triggerScope[ctrl[1]] = controller;
                }
                if (config.controllerAs) {
                  triggerScope[config.controllerAs] = controller;
                }
                var templateBody = $compile(template)(triggerScope);
                $element.find('.trigger-body').html(templateBody);
              }
            }
            $scope.description = config.description;
            reactComponentMounted = !!config.component;
          }
        }
      };

      this.loadTrigger();
    },
  ]);
