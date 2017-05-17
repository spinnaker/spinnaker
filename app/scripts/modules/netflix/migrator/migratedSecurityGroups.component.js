'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.migration.securityGroups.component', [])
  .component('migratedSecurityGroups', {
    bindings: {
      vm: '=',
    },
    template: `
            <div ng-if="$ctrl.vm.preview.securityGroups.length">
              <h4>
                <span class="glyphicon glyphicon-transfer small"></span> Security Groups
              </h4>
              <div class="indent">
                <div ng-repeat="securityGroup in $ctrl.vm.preview.securityGroups | orderBy: 'targetName'">
                  <account-tag ng-if="securityGroup.accountName && $ctrl.vm.preview.multipleAccounts" account="securityGroup.accountName"></account-tag>
                  <span class="strong">{{securityGroup.targetName}}</span>
                  <span class="location" ng-if="$ctrl.vm.preview.multipleRegions">in {{securityGroup.region}}</span>
                </div>
              </div>
            </div>
      `
  });
