'use strict';

const angular = require('angular');
import _ from 'lodash';

import { SUBNET_READ_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.openstack.subnet.subnetSelectField.directive', [
  SUBNET_READ_SERVICE,
  require('../common/selectField.component.js')
])
  .directive('osSubnetSelectField', function (subnetReader) {
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
          label: 'Subnet',
          labelColumnSize: 3,
          valueColumnSize: 7,
          options: [],
          filter: {},
          backingCache: 'subnets',

          updateOptions: function() {
            return subnetReader.listSubnetsByProvider('openstack').then(function(subnets) {
              scope.options = _.chain(subnets)
                .filter(scope.filter || {})
                .map(function(s) { return {label: s.name, value: s.id}; })
                .sortBy(function(o) { return o.label; })
                .value();

              return scope.options;
            });
          },

          onValueChanged: function(newValue) {
            scope.model = newValue;
            if( scope.onChange ) {
              scope.onChange({subnet: newValue});
            }
          }

        });

        scope.$watch('filter', function() { scope.$broadcast('updateOptions'); }, true);
      }
    };
});
