'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.subnet.subnetSelectField.directive', [
  require('../../core/config/settings'),
  require('../../core/utils/lodash'),
  require('../../core/subnet/subnet.read.service.js'),
  require('../common/selectField.component.js')
])
  .directive('osSubnetSelectField', function (settings, _, subnetReader) {
    return {
      restrict: 'E',
      templateUrl: require('./subnetSelectField.directive.html'),
      scope: {
        label: '@',
        labelColumnSize: '@',
        helpKey: '@',
        model: '=',
        filter: '=',
        onChange: '&',
        readOnly: '=',
        allowNoSelection: '=',
        noOptionsMessage: '@',
        noSelectionMessage: '@'
      },
      link: function(scope) {
        _.defaults(scope, {
          label: 'Subnet',
          labelColumnSize: 3,
          subnets: [],
          filter: {}
        });

        var currentRequestId = 0;

        function updateSubnetOptions() {
          currentRequestId++;
          var requestId = currentRequestId;
          subnetReader.listSubnetsByProvider('openstack').then(function(subnets) {
            if (requestId !== currentRequestId) {
              return;
            }

            scope.subnets = _(subnets)
              .filter(scope.filter || {})
              .map(function(s) { return {label: s.name, value: s.id}; })
              .sortBy(function(o) { return o.label; })
              .valueOf();
          });
        }

        scope.$watch('filter', updateSubnetOptions);
        updateSubnetOptions();
      }
    };
});
