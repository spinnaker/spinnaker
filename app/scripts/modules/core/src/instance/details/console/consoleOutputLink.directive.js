'use strict';

const angular = require('angular');

import { InstanceTemplates } from 'core/instance/templates';

import './consoleOutput.modal.less';

export const CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_DIRECTIVE = 'spinnaker.core.instance.details.console.link';
export const name = CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_DIRECTIVE; // for backwards compatibility
angular
  .module(CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_DIRECTIVE, [
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
