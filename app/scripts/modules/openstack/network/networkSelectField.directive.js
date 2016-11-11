'use strict';

import _ from 'lodash';
import {NETWORK_READ_SERVICE} from 'core/network/network.read.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.network.networkSelectField.directive', [
  NETWORK_READ_SERVICE,
  require('../common/selectField.component.js')
])
  .directive('networkSelectField', function (networkReader) {
    return {
      restrict: 'E',
      templateUrl: require('../common/cacheBackedSelectField.template.html'),
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
          valueColumnSize: 7,
          options: [],
          backingCache: 'networks',

          updateOptions: function() {
            return networkReader.listNetworksByProvider('openstack').then(function(networks) {
              scope.options = _.chain(networks)
                .filter(scope.filter || {})
                .map(function(a) { return {label: a.name, value: a.id}; })
                .sortBy(function(o) { return o.label; })
                .value();

              return scope.options;
            });
          },

          onValueChanged: function(newValue) {
            scope.model = newValue;
            if( scope.onChange ) {
              scope.onChange({network: newValue});
            }
          }

        });

        scope.$watch('filter', function() { scope.$broadcast('updateOptions'); });
      }
    };
});
