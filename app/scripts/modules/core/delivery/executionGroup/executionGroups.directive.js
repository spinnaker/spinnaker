'use strict';

let angular = require('angular');

require('./executionGroups.less');

module.exports = angular
  .module('spinnaker.core.delivery.main.executionGroups.directive', [
    require('../filter/executionFilter.model.js'),
    require('./executionGroup.directive.js'),
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
  .controller('ExecutionGroupsCtrl', function (ExecutionFilterModel) {
    this.groups = ExecutionFilterModel.groups;
    this.sortFilter = ExecutionFilterModel.sortFilter;
  });
