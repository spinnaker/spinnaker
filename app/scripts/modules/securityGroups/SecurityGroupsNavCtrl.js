'use strict';


angular.module('deckApp.securityGroup.navigation.controller', [])
  .controller('SecurityGroupsNavCtrl', function ($scope, application, _) {

    $scope.application = application;

    $scope.sortField = 'accountName';

    $scope.sortOptions = [
      { label: 'Account', key: 'accountName' },
      { label: 'Name', key: 'name' },
      { label: 'Region', key: 'region' }
    ];

    this.getHeadings = function getHeadings() {
      var allValues = _.collect(application.securityGroups, $scope.sortField);
      return _.compact(_.unique(allValues)).sort();
    };

    this.getSecurityGroupsFor = function getSecurityGroupsFor(value) {
      return application.securityGroups.filter(function (securityGroup) {
        return securityGroup[$scope.sortField] === value;
      });
    };

    this.getSecurityGroupLabel = function getSecurityGroupLabel(securityGroup) {
      if ($scope.sortField === 'name') {
        return securityGroup.accountName;
      }
      return securityGroup.name;
    };

    this.getSecurityGroupSublabel = function getSecurityGroupSublabel(securityGroup) {
      var labelFields = $scope.sortOptions.filter(function(sortOption) {
        if ($scope.sortField === 'name') {
          return sortOption.key === 'region';
        }
        return sortOption.key !== $scope.sortField && sortOption.key !== 'name';
      });
      return securityGroup[labelFields[0].key];
    };
  }
);
