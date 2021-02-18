import { IComponentController, IComponentOptions, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import { ITargetTrackingConfiguration, ITargetTrackingPolicy } from '@spinnaker/amazon';
import {
  AccountService,
  Application,
  ConfirmationModalService,
  IServerGroup,
  ITaskMonitorConfig,
  TaskExecutor,
} from '@spinnaker/core';

import { UpsertTargetTrackingController } from './upsertTargetTracking.controller';

export interface IAlarmRenderingServerGroup {
  type: string;
  name: string;
  account: string;
  region: string;
}

interface ITitusPolicy extends ITargetTrackingPolicy {
  id: string;
}

class TargetTrackingSummaryController implements IComponentController {
  public policy: ITitusPolicy;
  public serverGroup: IServerGroup;
  public alarmServerGroup: IAlarmRenderingServerGroup;
  public application: Application;
  public config: ITargetTrackingConfiguration;
  public popoverTemplate = require('./targetTrackingPopover.html');

  public static $inject = ['$uibModal'];
  constructor(private $uibModal: IModalService) {}

  public $onInit() {
    this.config = this.policy.targetTrackingConfiguration;
    AccountService.getAccountDetails(this.serverGroup.account).then((details) => {
      // alarmServerGroup is used to trick the chart rendering into using AWS metrics
      this.alarmServerGroup = {
        type: 'aws',
        name: this.serverGroup.name,
        account: details.awsAccount,
        region: this.serverGroup.region,
      };
    });
  }

  public editPolicy(): void {
    this.$uibModal
      .open({
        templateUrl: require('./upsertTargetTracking.modal.html'),
        controller: UpsertTargetTrackingController,
        controllerAs: '$ctrl',
        size: 'lg',
        resolve: {
          policy: () => this.policy,
          alarmServerGroup: () => this.alarmServerGroup,
          serverGroup: () => this.serverGroup,
          application: () => this.application,
        },
      })
      .result.catch(() => {});
  }

  public deletePolicy(): void {
    const { application, serverGroup, policy } = this;
    const taskMonitor: ITaskMonitorConfig = {
      application: this.application,
      title: 'Deleting scaling policy ' + this.policy.id,
    };

    ConfirmationModalService.confirm({
      header: `Really delete ${policy.id}?`,
      buttonText: 'Delete scaling policy',
      account: this.serverGroup.account,
      taskMonitorConfig: taskMonitor,
      submitMethod: () =>
        TaskExecutor.executeTask({
          application,
          description: 'Delete scaling policy ' + policy.id,
          job: [
            {
              type: 'deleteScalingPolicy',
              cloudProvider: 'titus',
              credentials: serverGroup.account,
              region: serverGroup.region,
              scalingPolicyID: policy.id,
              serverGroupName: serverGroup.name,
            },
          ],
        }),
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
       popover-title="Policy {{$ctrl.policy.id}}"
       popover-trigger="'mouseenter'">
      <p>
        <span class="label label-default">TARGET TRACKING</span>
        <span class="label small" ng-if="$ctrl.policy.status.state !== 'Applied'">({{$ctrl.policy.status.state}})</span>
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

export const TARGET_TRACKING_SUMMARY_COMPONENT = 'spinnaker.titus.scalingPolicy.targetTracking.summary.component';
module(TARGET_TRACKING_SUMMARY_COMPONENT, []).component('titusTargetTrackingSummary', component);
