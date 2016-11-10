'use strict';

import _ from 'lodash';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular.module('spinnaker.openstack.region.regionSelectField.directive', [
  ACCOUNT_SERVICE,
  require('../common/selectField.component.js')
])
  .directive('osRegionSelectField', function (accountService) {
    return {
      restrict: 'E',
      templateUrl: require('./regionSelectField.directive.html'),
      scope: {
        label: '@?',
        labelColumnSize: '@?',
        helpKey: '@?',
        model: '<?',
        account: '<',
        onChange: '&',
        readOnly: '<',
        allowNoSelection: '<',
        noOptionsMessage: '@?',
        noSelectionMessage: '@?'
      },
      link: function(scope) {
        _.defaults(scope, {
          label: 'Region',
          labelColumnSize: 3,
          regions: []
        });

        if( scope.model ) {
          scope.regions.push({label: scope.model, value: scope.model});
        }

        var currentRequestId = 0;

        function updateRegionOptions() {
          currentRequestId++;
          var requestId = currentRequestId;

          accountService.getRegionsForAccount(scope.account).then(function(regions) {
            if (requestId !== currentRequestId) {
              return;
            }

            scope.regions = _.chain(regions)
              .map(function(r) { return {label: r, value: r}; })
              .sortBy(function(o) { return o.label; })
              .value();
          });
        }

        scope.$watch('account', updateRegionOptions);
      }
    };
});
