'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('LoadBalancersNavCtrl', function ($scope, application, _) {

    $scope.application = application;
    $scope.loadBalancers = application.loadBalancers;

    $scope.sortField = 'account';

    $scope.sortOptions = [
      { label: 'Account', key: 'account' },
      { label: 'Name', key: 'name' },
      { label: 'Region', key: 'region' }
    ];

    $scope.getHeadings = function() {
      var allValues = _.collect(application.loadBalancers, $scope.sortField);
      return _.unique(allValues).sort();
    };

    $scope.getLoadBalancersFor = function (value) {
      return $scope.loadBalancers.filter(function (loadBalancer) {
        return loadBalancer[$scope.sortField] === value;
      });
    };

    $scope.getLoadBalancerLabel = function(loadBalancer) {
      if ($scope.sortField === 'name') {
        return loadBalancer.account;
      }
      return loadBalancer.name;
    };

    $scope.getLoadBalancerSublabel = function(loadBalancer) {
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
