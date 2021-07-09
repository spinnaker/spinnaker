import { IComponentController, IComponentOptions, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import { Application, ConfirmationModalService, IServerGroup, ITaskMonitorConfig } from '@spinnaker/core';

import { ScalingPolicyWriter } from '../ScalingPolicyWriter';
import { ITargetTrackingConfiguration, ITargetTrackingPolicy } from '../../../../domain/ITargetTrackingPolicy';
import { UpsertTargetTrackingController } from './upsertTargetTracking.controller';

class TargetTrackingSummaryController implements IComponentController {
  public policy: ITargetTrackingPolicy;
  public serverGroup: IServerGroup;
  public application: Application;
  public config: ITargetTrackingConfiguration;
  public popoverTemplate = require('./targetTrackingPopover.html');

  public static $inject = ['$uibModal'];
  constructor(private $uibModal: IModalService) {}

  public $onInit() {
    this.config = this.policy.targetTrackingConfiguration;
  }

  public editPolicy(): void {
    this.$uibModal.open({
      templateUrl: require('./upsertTargetTracking.modal.html'),
      controller: UpsertTargetTrackingController,
      controllerAs: '$ctrl',
      size: 'lg',
      resolve: {
        policy: () => this.policy,
        serverGroup: () => this.serverGroup,
        application: () => this.application,
      },
    });
  }

  public deletePolicy(): void {
    const taskMonitor: ITaskMonitorConfig = {
      application: this.application,
      title: 'Deleting scaling policy ' + this.policy.policyName,
    };

    ConfirmationModalService.confirm({
      header: `Really delete ${this.policy.policyName}?`,
      buttonText: 'Delete scaling policy',
      account: this.serverGroup.account,
      taskMonitorConfig: taskMonitor,
      submitMethod: () => ScalingPolicyWriter.deleteScalingPolicy(this.application, this.serverGroup, this.policy),
    });
  }
}

const component: IComponentOptions = {
  bindings: {
    policy: '<',
    serverGroup: '<',
    application: '<',
  },
  controller: TargetTrackingSummaryController,
  template: `
    <div uib-popover-template="$ctrl.popoverTemplate"
       popover-placement="left"
       popover-title="{{$ctrl.policy.policyName}}"
       popover-trigger="'mouseenter'">
      <p>
        <span class="label label-default">{{$ctrl.policy.policyType | robotToHuman | uppercase }}</span>
        <div>
          <strong>Target</strong>
          {{$ctrl.config.predefinedMetricSpecification.predefinedMetricType}}
          {{$ctrl.config.customizedMetricSpecification.metricName}}
          <span ng-if="$ctrl.config.customizedMetricSpecification">({{$ctrl.config.customizedMetricSpecification.statistic}})</span>
          @ {{$ctrl.config.targetValue}}
        </div>
      </p>
      <div class="actions text-right">
        <button class="btn btn-xs btn-link" ng-click="$ctrl.editPolicy()">
          <span class="glyphicon glyphicon-cog" uib-tooltip="Edit policy"></span>
          <span class="sr-only">Edit policy</span>
        </button>
        <button class="btn btn-xs btn-link" ng-click="$ctrl.deletePolicy()">
          <span class="glyphicon glyphicon-trash" uib-tooltip="Delete policy"></span>
          <span class="sr-only">Delete policy</span>
        </button>
      </div>
    </div>
  `,
};

export const TARGET_TRACKING_SUMMARY_COMPONENT = 'spinnaker.amazon.scalingPolicy.targetTracking.summary.component';
module(TARGET_TRACKING_SUMMARY_COMPONENT, []).component('targetTrackingSummary', component);
