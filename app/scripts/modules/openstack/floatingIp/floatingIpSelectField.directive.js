'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.floatingIp.floatingIpSelectField.directive', [
  require('../../core/utils/lodash'),
  require('../common/selectField.directive.js')
])
  .directive('floatingIpSelectField', function (_) {
    return {
      restrict: 'E',
      templateUrl: require('./floatingIpSelectField.directive.html'),
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
          label: 'Floating IP',
          labelColumnSize: 3,
          floatingIps: []
        });

        function updateOptions() {
          //TODO (jcwest): replace with reader when available
          var floatingIps = [
            {provider: 'openstack', id: 'd2cbbf3e-0eeb-4040-9c4e-21dc97010022', floatingIpAddress: '172.24.4.3'},
            {provider: 'openstack', id: 'invalid', floatingIpAddress: 'invalid'}
          ];
          scope.floatingIps = _(floatingIps)
            .filter(_.assign({provider: 'openstack'}, scope.filter || {}))
            .map(function(a) { return {label: a.floatingIpAddress, value: a.id}; })
            .valueOf();
        }

        scope.$watch('filter', updateOptions);
      }
    };
});
