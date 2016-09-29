'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netfilx.widgits.canaryConfigSelector.directive', [
    require('./canary.read.service'),
  ])
  .directive('canaryConfigSelector', () => {
    return {
      scope: {},
      bindToController: {
        model: '=' ,
        applicationName: '='
      },
      templateUrl: require('./canaryConfigSelector.directive.html'),
      controllerAs: 'vm',
      controller: function($scope, canaryReadService) {
        let vm = this;

        vm.selectorChanged = (model) => {
          if (model) {
            vm.model = model;
            vm.model.successfulScore = model.canarySuccessCriteria.canaryResultScore;
            vm.model.unhealthyScore = model.canaryHealthCheckHandler.minimumCanaryResultScore;
            vm.model.resultStrategy = model.combinedCanaryResultStrategy;
          }
        };

        vm.isNameInConfigList = (selectedName) => {
          return getSelected(vm.canaryConfigs, selectedName).length ? true : false;
        };

        let setOptions = (results) => {
          vm.canaryConfigs = results;
          return vm.canaryConfigs;
        };

        let getSelected = _.curry((canaryConfigs = [], canaryName) => {
          return canaryConfigs.filter((config) => config.name === canaryName);
        });

        let findConfigAndSet = _.flowRight(vm.selectorChanged, _.head, getSelected);

        $scope.$watch('vm.model.name', function(newVal) {
          if(newVal !== undefined) {
            findConfigAndSet(vm.canaryConfigs, newVal);
          }
        });

        canaryReadService.getCanaryConfigsByApplication(vm.applicationName)
          .then(setOptions);

      }
    };
  });
