'use strict';

angular.module('deckApp')
  .controller('LoadBalancerCtrl', function($scope, application, loadBalancer, _) {
    $scope.application = application;

    application.getLoadBalancers().then(function(loadBalancers) {
      $scope.loadBalancer = loadBalancers.filter(function(test) {
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.account;
      })[0];
      addSearchFields($scope.loadBalancer);
      updateLoadBalancerGroups();
    });

    $scope.sortFilter = {
      filter: '',
      showAllInstances: true
    };

    $scope.updateSorting = function() {
      var sortFilter = $scope.sortFilter;
      if (sortFilter.sortPrimary === sortFilter.sortSecondary) {
        sortFilter.sortSecondary = $scope.sortOptions(sortFilter.sortPrimary)[0].key;
      }
      $scope.updateLoadBalancerGroups();
    };

    function addSearchFields(loadBalancer) {
      loadBalancer.serverGroups.forEach(function(serverGroup) {
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
    }

    $scope.updateLoadBalancerGroups = _.debounce(updateLoadBalancerGroups, 200);
  });
