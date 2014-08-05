'use strict';

angular.module('deckApp')
  .controller('LoadBalancerCtrl', function($scope, application, loadBalancer, _) {
    $scope.application = application;

    application.getLoadBalancers().then(function(loadBalancers) {
      $scope.loadBalancer = loadBalancers.filter(function(test) {
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.account;
      })[0];
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

    function addServerGroupsAndSearchFields(loadBalancer, clusters) {
      if (!loadBalancer.serverGroups) {
        loadBalancer.serverGroups = [];
        var clusterMatches = clusters.filter(function (cluster) {
          return cluster.account === loadBalancer.account;
        });
        clusterMatches.forEach(function (matchedCluster) {
          matchedCluster.serverGroups.forEach(function (serverGroup) {
            if (serverGroup.region === loadBalancer.region && loadBalancer.serverGroupNames.indexOf(serverGroup.name) !== -1) {
              loadBalancer.serverGroups.push(serverGroup);
            }
          });
        });
      }
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
      application.getClusters().then(function(clusters) {
        var loadBalancer = $scope.loadBalancer,
          filter = $scope.sortFilter.filter.toLowerCase();

        addServerGroupsAndSearchFields(loadBalancer, clusters);

        $scope.filteredServerGroups = matchesFilter(loadBalancer, filter);
      });
    }

    $scope.updateLoadBalancerGroups = _.debounce(updateLoadBalancerGroups, 200);

    updateLoadBalancerGroups();
  });
