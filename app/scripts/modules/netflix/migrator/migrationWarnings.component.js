'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.migration.warnings.component', [])
  .component('migrationWarnings', {
    bindings: {
      vm: '=',
    },
    template: `
      <div ng-if="$ctrl.vm.preview.warnings.length" class="skipped">
            <h4><span class="small glyphicon glyphicon-ban-circle"></span> Skipped Security Groups</h4>
            <p>The following ingress rule<span ng-if="$ctrl.vm.preview.warnings.length > 1">s</span> cannot be created because
              Spinnaker does not manage the account owning the security group.</p>
            <div ng-repeat="warning in $ctrl.vm.preview.warnings">
              <dl class="dl-horizontal">
                <dt>Source Security Group</dt>
                <dd>{{warning.parentGroup}}</dd>
                <dt>Account ID</dt>
                <dd>{{warning.accountId}}</dd>
                <dt>Ingress Security Group</dt>
                <dd>{{warning.groupName}} ({{warning.groupId}})</dd>
              </dl>
            </div>
            <p ng-if="$ctrl.vm.state === 'preview'">After the migration completes, you will need to make sure
              the ingress security group<span ng-if="$ctrl.vm.preview.warnings.length > 1">s</span>
              exist<span ng-if="$ctrl.vm.preview.warnings.length === 1">s</span> in VPC0, then create an ingress rule via
              the AWS console.</p>
            <p ng-if="$ctrl.vm.state === 'complete'">Please make sure
              the ingress security group<span ng-if="$ctrl.vm.preview.warnings.length > 1">s</span>
              exist<span ng-if="$ctrl.vm.preview.warnings.length === 1">s</span> in VPC0, then create an ingress rule via
              the AWS console.</p>
          </div>
`
  });
