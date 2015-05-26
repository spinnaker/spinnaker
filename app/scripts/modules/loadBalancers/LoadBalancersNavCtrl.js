'use strict';


angular.module('spinnaker.loadBalancer.nav.controller', [
 'spinnaker.utils.lodash'
])
  .controller('LoadBalancersNavCtrl', function ($scope, application, _) {

    $scope.application = application;
    $scope.loadBalancers = application.loadBalancers;

    $scope.sortField = 'account';

    $scope.sortOptions = [
      { label: 'Account', key: 'account' },
      { label: 'Name', key: 'name' },
      { label: 'Region', key: 'region' }
    ];

    this.getHeadings = function getHeadings() {
      var allValues = _.collect(application.loadBalancers, $scope.sortField);
      return _.compact(_.unique(allValues)).sort();
    };

    this.getLoadBalancersFor = function getLoadBalancersFor(value) {
      return application.loadBalancers.filter(function (loadBalancer) {
        return loadBalancer[$scope.sortField] === value;
      });
    };

    this.getLoadBalancerLabel = function getLoadBalancerLabel(loadBalancer) {
      if ($scope.sortField === 'name') {
        return loadBalancer.account;
      }
      return loadBalancer.name;
    };

    this.getLoadBalancerSublabel = function getLoadBalancerSublabel(loadBalancer) {
      var labelFields = $scope.sortOptions.filter(function(sortOption) {
        if ($scope.sortField === 'name') {
          return sortOption.key === 'region';
        }
        return sortOption.key !== $scope.sortField && sortOption.key !== 'name';
      });
      return loadBalancer[labelFields[0].key];
    };
  }
);
