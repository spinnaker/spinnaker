'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.network.networkSelectField.directive', [
  require('../../core/utils/lodash'),
  require('../common/selectField.directive.js')
])
  .directive('networkSelectField', function (_) {
    return {
      restrict: 'E',
      templateUrl: require('./networkSelectField.directive.html'),
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
          label: 'Network',
          labelColumnSize: 3,
          floatingIps: []
        });

        function updateOptions() {
          //TODO (jcwest): replace with reader when available
          var floatingIps = [
            {provider: 'openstack', id: 'd2cbbf3e-0eeb-4040-9c4e-21dc97010022', name: 'defaultNetwork'},
            {provider: 'openstack', id: 'invalid', name: 'invalid'}
          ];
          scope.floatingIps = _(floatingIps)
            .filter(_.assign({provider: 'openstack'}, scope.filter || {}))
            .map(function(a) { return {label: a.name, value: a.id}; })
            .valueOf();
        }

        scope.$watch('filter', updateOptions);
      }
    };
});
