'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('LoadBalancerCtrl', function ($scope, application, loadBalancer, _) {
    $scope.application = application;

    $scope.loadBalancer = application.loadBalancers.filter(function (test) {
      return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account.name === loadBalancer.account.name;
    })[0];

    $scope.sortFilter = {
      filter: ''
    };

    $scope.displayOptions = {
      limitInstanceDisplay: false,
      showServerGroups: true,
      showAllInstances: true
    };

    $scope.updateSorting = function () {
      var sortFilter = $scope.sortFilter;
      if (sortFilter.sortPrimary === sortFilter.sortSecondary) {
        sortFilter.sortSecondary = $scope.sortOptions(sortFilter.sortPrimary)[0].key;
      }
      $scope.updateLoadBalancerGroups();
    };

    function addSearchFields(loadBalancer) {
      loadBalancer.serverGroups.forEach(function (serverGroup) {
        serverGroup.searchField = [
          serverGroup.name
        ].join(' ');
      });
    }

    function matchesFilter(loadBalancer, filter) {
      return loadBalancer.serverGroups.filter(function (serverGroup) {
        if (!filter) {
          return true;
        }
        return filter.split(' ').every(function (testWord) {
          return serverGroup.searchField.indexOf(testWord) !== -1;
        });
      });
    }

    function updateLoadBalancerGroups() {
      var loadBalancer = $scope.loadBalancer,
        filter = $scope.sortFilter.filter.toLowerCase();

      $scope.filteredServerGroups = matchesFilter(loadBalancer, filter);
      $scope.$digest(); // debounce
    }

    $scope.updateLoadBalancerGroups = _.debounce(updateLoadBalancerGroups, 200);

    addSearchFields($scope.loadBalancer);
    $scope.updateLoadBalancerGroups();

  }
);
