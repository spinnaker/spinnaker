'use strict';

import _ from 'lodash';

import {LOAD_BALANCER_READ_SERVICE} from 'core/loadBalancer/loadBalancer.read.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.loadBalancer.loadBalancerSelectField.directive', [
  LOAD_BALANCER_READ_SERVICE,
  require('../common/selectField.component.js')
])
  .directive('osLoadBalancerSelectField', function (loadBalancerReader) {
    return {
      restrict: 'E',
      templateUrl: require('../common/cacheBackedSelectField.template.html'),
      scope: {
        readOnly: '=',
        labelColumnSize: '@',
        label: '@',
        helpKey: '@',
        valueColumnSize: '@',
        model: '=',
        filter: '=',
        onChange: '&',
        allowNoSelection: '=',
        noOptionsMessage: '@',
        noSelectionMessage: '@'
      },
      link: function(scope) {
        _.defaults(scope, {
          label: 'Load Balancer',
          labelColumnSize: 3,
          valueColumnSize: 7,
          options: [],
          filter: {},
          backingCache: 'loadBalancers',

          updateOptions: function() {
            return loadBalancerReader.listLoadBalancers('openstack').then(function(loadBalancers) {
              scope.options = _.chain(loadBalancers)
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
              scope.onChange({loadBalancer: newValue});
            }
          }
        });

        scope.$watch('filter', function() { scope.$broadcast('updateOptions'); }, true);
      }
    };
});
