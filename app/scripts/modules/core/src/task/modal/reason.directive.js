'use strict';

import * as angular from 'angular';

export const CORE_TASK_MODAL_REASON_DIRECTIVE = 'spinnaker.task.reason.directive';
export const name = CORE_TASK_MODAL_REASON_DIRECTIVE; // for backwards compatibility
angular.module(CORE_TASK_MODAL_REASON_DIRECTIVE, []).directive('taskReason', function () {
  return {
    restrict: 'E',
    bindToController: {
      command: '=',
    },
    scope: {},
    controller: angular.noop,
    controllerAs: 'vm',
    templateUrl: require('./reason.directive.html'),
  };
});
