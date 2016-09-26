'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.canary.canaryAnalysisNameSelector.directive', [
    require('../../core/api/api.service')
  ])
  .directive('canaryAnalysisNameSelector', () => {
    return {
      restrict: 'E',
      replace: true,
      scope: {},
      bindToController: {
        model: '=',
        className: '@',
      },
      controllerAs: 'ctrl',
      templateUrl: require('./canaryAnalysisNameSelector.directive.html'),
      controller: function(API) {
        let vm = this;
        vm.nameList = [];

        API.one('canaryConfig').all('names').getList()
          .then(
            (results) => vm.nameList = results.sort(),
            () => vm.nameList = []
          );

      },
    };
  });

