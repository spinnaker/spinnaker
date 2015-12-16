'use strict';

let angular = require('angular');

function selectScopeDirective() {
  return {
    restrict: 'E',
    controller: 'ScopeSelectController',
    controllerAs: 'ss',
    templateUrl: require('./scopeSelect.directive.html'),
    scope: {
      clusters: '=',
      modal: '=',
      appName: '='
    }
  };
}

module.exports = angular
  .module('spinnaker.netflix.fastProperties.scope.selection.directive', [
    require('./fastPropertyScope.service.js'),
    require('./fastProperty.read.service.js'),
    require('../../core/utils/lodash.js'),
  ])
  .directive('scopeSelect', selectScopeDirective)
  .controller('ScopeSelectController', function ($scope, FastPropertyScopeService, fastPropertyReader, _) {
    var vm = this;

    vm.clusters = $scope.clusters;
    vm.appName = $scope.appName;
    vm.modal = $scope.modal;
    vm.modal.impactCount = $scope.modal.impactCount || 0;
    vm.infiniteScroll = {};
    vm.infiniteScroll.numToAdd = 20;
    vm.infiniteScroll.currentItems = 20;

    vm.scopes = [
      {id: 'appId', name: 'Application'},
      {id: 'region', name: 'Region'},
      {id: 'stack', name: 'Stack'},
      {id: 'cluster', name: 'Cluster'},
      {id: 'asg', name: 'ASG'},
      {id: 'zone', name: 'Zone'},
      {id: 'serverId', name: 'Instance Id'},
    ];

    vm.findScope = function(query) {
      if(vm.scopeResults)  {
        if(vm.scopeResults.length < 100) {
          vm.filteredScopeResults = vm.scopeResults;
        }
        else {
          vm.filteredScopeResults = vm.scopeResults.filter(function (item) {
            return item.primary.indexOf(query)> -1 || _(item.secondary).any(function (scopeItem) {
                return scopeItem.indexOf(query)> -1;
              });
          });
        }
      }
    };

    vm.addMoreItems = function() {
      vm.infiniteScroll.currentItems += vm.infiniteScroll.numToAdd;
    };

    vm.resetInfScroll = function () {
      vm.infiniteScroll.currentItems = vm.infiniteScroll.numToAdd;
    };

    vm.scopeLevelSelected = function (selected) {
      FastPropertyScopeService.getResultsForScope($scope.appName, this.clusters, selected.id).then(function (results) {
        vm.scopeResults = vm.filteredScopeResults = results;
        vm.modal.selectedScope = null;
        vm.scope = null;
        vm.modal.impactCount = 0;
        vm.resetInfScroll();
      });
    };

    vm.scopeSelected = function(selected) {
      selected.scope.env = vm.modal.env;
      vm.modal.selectedScope = selected.scope;

      fastPropertyReader.fetchImpactCountForScope(selected.scope).then(function(result) {
        vm.modal.impactCount = result.count || 0;
      });
    };

    return vm;
  }
);


