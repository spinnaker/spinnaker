'use strict';

import {EXECUTION_GROUP_COMPONENT} from './executionGroup.component';

let angular = require('angular');

require('./executionGroups.less');

module.exports = angular
  .module('spinnaker.core.delivery.main.executionGroups.directive', [
    require('../filter/executionFilter.model.js'),
    EXECUTION_GROUP_COMPONENT,
  ])
  .directive('executionGroups', function () {
    return {
      restrict: 'E',
      templateUrl: require('./executionGroups.directive.html'),
      scope: {},
      bindToController: {
        application: '=',
      },
      controller: 'ExecutionGroupsCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ExecutionGroupsCtrl', function (ExecutionFilterModel, $state) {
    this.groups = ExecutionFilterModel.groups;
    this.sortFilter = ExecutionFilterModel.sortFilter;
    this.showingDetails = () => $state.includes('**.execution');
  });
