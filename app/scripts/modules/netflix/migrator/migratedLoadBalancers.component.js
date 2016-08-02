'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.migration.loadBalancers.component', [])
  .component('migratedLoadBalancers', {
    bindings: {
      vm: '=',
    },
    template: `
      <div ng-if="$ctrl.vm.preview.loadBalancers.length">
        <h4><span class="small icon icon-elb"></span> Load Balancers</h4>
        <div class="indent">
          <div ng-repeat="loadBalancer in $ctrl.vm.preview.loadBalancers | orderBy: 'targetName'">
            <account-tag ng-if="loadBalancer.accountName && $ctrl.vm.preview.multipleAccounts" account="loadBalancer.accountName"></account-tag>
            <span class="strong">{{loadBalancer.targetName}}</span>
            <span class="location" ng-if="$ctrl.vm.preview.multipleRegions">in {{loadBalancer.region}}</span>
          </div>
          <p class="note">
            Note: if Route53 or sticky session policies are configured for the current
            load balancer<span ng-if="$ctrl.vm.preview.loadBalancers.length> 1">s</span>,
            you will need to set that up in the AWS console.
          </p>
        </div>
      </div>
      `
  });
