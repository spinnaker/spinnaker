'use strict';

const angular = require('angular');

import { InstanceTemplates } from 'core/instance/templates';

import './consoleOutput.modal.less';
import { CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUT_MODAL_CONTROLLER } from './consoleOutput.modal.controller';

export const CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_DIRECTIVE = 'spinnaker.core.instance.details.console.link';
export const name = CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_DIRECTIVE; // for backwards compatibility
angular
  .module(CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_DIRECTIVE, [
    require('angular-ui-bootstrap'),
    CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUT_MODAL_CONTROLLER,
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
