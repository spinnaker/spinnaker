'use strict';


angular.module('spinnaker.securityGroup.all.controller', [
  'ui.bootstrap',
  'spinnaker.utils.lodash',
  'spinnaker.providerSelection.service',
])
  .controller('AllSecurityGroupsCtrl', function($scope, $modal, _, providerSelectionService, application) {
    $scope.application = application;

    $scope.sortFilter = {
      sortPrimary: 'accountName',
      filter: '',
      showServerGroups: true,
      showLoadBalancers: true
    };

    $scope.sortOptions = [
      { label: 'Account', key: 'accountName' },
      { label: 'Region', key: 'region' }
    ];

    function addSearchField(securityGroups) {
      securityGroups.forEach(function(securityGroup) {
        if (!securityGroup.searchField) {
          securityGroup.searchField = [
            securityGroup.name,
            securityGroup.id,
            securityGroup.accountName,
            securityGroup.region,
            _.pluck(securityGroup.usages.serverGroups, 'name').join(' '),
            _.pluck(securityGroup.usages.loadBalancers, 'name').join(' ')
          ].join(' ');
        }
      });
    }

    function matchesFilter(filter, securityGroup) {
      return filter.every(function (testWord) {
        return securityGroup.searchField.indexOf(testWord) !== -1;
      });
    }

    function filterSecurityGroupsForDisplay(securityGroups, filter) {
      return securityGroups.filter(function (securityGroup) {
        if (!filter.length) {
          return true;
        }
        return matchesFilter(filter, securityGroup);
      });
    }

    this.createSecurityGroup = function createSecurityGroup() {
      providerSelectionService.selectProvider().then(function(provider) {
        $modal.open({
          templateUrl: 'scripts/modules/securityGroups/configure/' + provider + '/createSecurityGroup.html',
          controller: 'CreateSecurityGroupCtrl as ctrl',
          resolve: {
            securityGroup: function () {
              return {
                credentials: 'test',
                subnet: 'none',
                regions: [],
                vpcId: null,
                securityGroupIngress: []
              };
            },
            application: function () {
              return application;
            }
          }
        });
      });
    };

    function updateSecurityGroups() {
      $scope.$evalAsync(function() {
        var securityGroups = application.securityGroups,
          groups = [],
          filter = $scope.sortFilter.filter ? $scope.sortFilter.filter.toLowerCase().split(' ') : [],
          primarySort = $scope.sortFilter.sortPrimary,
          secondarySort = $scope.sortOptions.filter(function(option) { return option.key !== primarySort; })[0].key;

        addSearchField(securityGroups);

        var filtered = filterSecurityGroupsForDisplay(securityGroups, filter);
        var grouped = _.groupBy(filtered, primarySort);

        _.forOwn(grouped, function(group, key) {
          var subGroupings = _.groupBy(group, secondarySort),
            subGroups = [];

          _.forOwn(subGroupings, function(subGroup, subKey) {
            subGroups.push( { heading: subKey, securityGroups: _.sortBy(subGroup, 'name') } );
          });

          groups.push( { heading: key, subgroups: _.sortBy(subGroups, 'heading') } );
        });

        $scope.groups = _.sortBy(groups, 'heading');

        $scope.displayOptions = {
          showServerGroups: $scope.sortFilter.showServerGroups,
          showLoadBalancers: $scope.sortFilter.showLoadBalancers
        };
      });
    }

    this.updateSecurityGroups = _.debounce(updateSecurityGroups, 200);

    application.registerAutoRefreshHandler(updateSecurityGroups, $scope);

    updateSecurityGroups();

  }
);
