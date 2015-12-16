'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.scope.appSelector.directive', [])
  .directive('scopeAppSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        model: '=',
        applications: '=',
        onAppSelected: '&',
        onAppRemove: '&',
        onRefreshList: '&'
      },
      controllerAs: 'fp',
      controller: function controller() {
        var vm = this;

        vm.appSelected = (item) => {
          vm.onAppSelected(item);
        };

        vm.removeApp = (item) => {
          vm.onAppRemove(item);
        };

        vm.refreshList = (query) => {
          vm.onRefreshList(query);
        };

      },
      templateUrl: require('./scopeAppSelector.directive.html')
    };
  });
