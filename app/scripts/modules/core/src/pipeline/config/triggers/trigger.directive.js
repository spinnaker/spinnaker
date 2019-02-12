'use strict';

import * as React from 'react';
import * as ReactDOM from 'react-dom';

import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.trigger.triggerDirective', [])
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
  .controller('TriggerCtrl', function($scope, $element, $compile, $controller, $templateCache) {
    let reactComponentMounted;
    var triggerTypes = Registry.pipeline.getTriggerTypes();
    $scope.options = triggerTypes;
    this.disableAutoTriggering = SETTINGS.disableAutoTriggering || [];

    this.removeTrigger = function(trigger) {
      var triggerIndex = $scope.pipeline.triggers.indexOf(trigger);
      $scope.pipeline.triggers.splice(triggerIndex, 1);
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
            const props = { fieldUpdated: $scope.fieldUpdated, trigger: $scope.trigger };

            ReactDOM.render(React.createElement(TriggerConfig, props), triggerBodyNode);
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
  });
