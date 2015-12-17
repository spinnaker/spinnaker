'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.canary.canaryAnalysisNameSelector.directive', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
  ])
  .directive('canaryAnalysisNameSelector', (Restangular) => {
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
      controller: function(Restangular) {
        let vm = this;
        vm.nameList = [];

        Restangular.one('canaryConfig').all('names').getList()
          .then(
            (results) => vm.nameList = results,
            (errror) => vm.nameList=[]
          );

      },
    };
  });

