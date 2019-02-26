'use strict';

const angular = require('angular');
import _ from 'lodash';

import { INSTANCE_TYPE_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.instance.instanceTypeSelectField', [
    INSTANCE_TYPE_SERVICE,
    require('../common/selectField.component').name,
  ])
  .directive('osInstanceTypeSelectField', [
    'instanceTypeService',
    function(instanceTypeService) {
      return {
        restrict: 'E',
        templateUrl: require('../common/cacheBackedSelectField.template.html'),
        scope: {
          account: '<',
          region: '<',
          readOnly: '=',
          labelColumnSize: '@',
          label: '@',
          helpKey: '@',
          valueColumnSize: '@',
          model: '=',
          onChange: '&',
          allowNoSelection: '=',
          noOptionsMessage: '@',
          noSelectionMessage: '@',
        },
        link: function(scope) {
          _.defaults(scope, {
            label: 'Instance Type',
            labelColumnSize: 3,
            valueColumnSize: 7,
            options: [{ label: scope.model, value: scope.model }],
            backingCache: 'instanceTypes',

            updateOptions: function() {
              return instanceTypeService.getAllTypesByRegion('openstack').then(function(result) {
                scope.options = _.chain(result[scope.region] || [])
                  .filter(t => t.account === scope.account)
                  .map(function(t) {
                    return { label: t.name, value: t.name };
                  })
                  .sortBy(function(o) {
                    return o.label;
                  })
                  .value();
              });
            },

            onValueChanged: function(newValue) {
              scope.model = newValue;
              if (scope.onChange) {
                scope.onChange({ instanceType: newValue });
              }
            },
          });

          scope.$watch('region', function() {
            scope.$broadcast('updateOptions');
          });
          scope.$watch('account', function() {
            scope.$broadcast('updateOptions');
          });
        },
      };
    },
  ]);
