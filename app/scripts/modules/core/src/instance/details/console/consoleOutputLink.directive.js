'use strict';

const angular = require('angular');

import { InstanceTemplates } from 'core/instance/templates';

import './consoleOutput.modal.less';

module.exports = angular
  .module('spinnaker.core.instance.details.console.link', [
    require('angular-ui-bootstrap'),
    require('./consoleOutput.modal.controller').name,
  ])
  .directive('consoleOutputLink', function() {
    return {
      restrict: 'E',
      template: '<a href ng-click="vm.showConsoleOutput()">{{vm.text}}</a>',
      scope: {},
      bindToController: {
        instance: '=',
        text: '=?',
        usesMultiOutput: '=?',
      },
      controllerAs: 'vm',
      controller: [
        '$uibModal',
        function($uibModal) {
          this.text = this.text || 'Console Output (Raw)';
          this.usesMultiOutput = this.usesMultiOutput || false;
          this.showConsoleOutput = function() {
            $uibModal.open({
              templateUrl: InstanceTemplates.consoleOutputModal,
              controller: 'ConsoleOutputCtrl as ctrl',
              size: 'lg modal-fullscreen',
              resolve: {
                instance: () => this.instance,
                usesMultiOutput: () => this.usesMultiOutput,
              },
            });
          };
        },
      ],
    };
  });
