'use strict';

import * as angular from 'angular';

import './multipleInstanceServerGroup.directive.less';

export const CORE_INSTANCE_DETAILS_MULTIPLEINSTANCESERVERGROUP_DIRECTIVE =
  'spinnaker.core.instance.details.multipleInstanceServerGroup.directive';
export const name = CORE_INSTANCE_DETAILS_MULTIPLEINSTANCESERVERGROUP_DIRECTIVE; // for backwards compatibility
angular
  .module(CORE_INSTANCE_DETAILS_MULTIPLEINSTANCESERVERGROUP_DIRECTIVE, [])
  .directive('multipleInstanceServerGroup', function () {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        instanceGroup: '=',
      },
      controller: angular.noop,
      controllerAs: 'vm',
      templateUrl: require('./multipleInstanceServerGroup.directive.html'),
    };
  });
