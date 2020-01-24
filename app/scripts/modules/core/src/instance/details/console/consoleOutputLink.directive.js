'use strict';

import { module } from 'angular';

import { InstanceTemplates } from '../../templates';

import './consoleOutput.modal.less';
import { CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUT_MODAL_CONTROLLER } from './consoleOutput.modal.controller';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

export const CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_DIRECTIVE = 'spinnaker.core.instance.details.console.link';
export const name = CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_DIRECTIVE; // for backwards compatibility
module(CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_DIRECTIVE, [
  ANGULAR_UI_BOOTSTRAP,
  CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUT_MODAL_CONTROLLER,
]).directive('consoleOutputLink', function() {
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
