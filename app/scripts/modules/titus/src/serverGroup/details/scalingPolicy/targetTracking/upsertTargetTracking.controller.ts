import { IComponentController } from 'angular';

import { cloneDeep } from 'lodash';
import { Subject } from 'rxjs';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { Application, IServerGroup, TaskMonitorBuilder, TaskMonitor } from '@spinnaker/core';

import { ITargetTrackingConfiguration, ITargetTrackingPolicy, IUpsertScalingPolicyCommand, ScalingPolicyWriter } from '@spinnaker/amazon';
import { IAlarmRenderingServerGroup } from './targetTrackingSummary.component';

export interface ITargetTrackingState {
  unit: string;
  scaleInChanged: boolean;
}

export interface ITargetTrackingPolicyCommand extends IUpsertScalingPolicyCommand {
  jobId: string;
  targetTrackingConfiguration: ITargetTrackingConfiguration;
}

export interface ITitusServerGroup extends IServerGroup {
  id: string;
}

export interface ITitusTargetTrackingPolicy extends ITargetTrackingPolicy {
  id?: string;
}

export class UpsertTargetTrackingController implements IComponentController {

  public statistics = ['Average', 'Maximum', 'Minimum', 'SampleCount', 'Sum'];
  public alarmUpdated = new Subject();

  public taskMonitor: TaskMonitor;
  public state: ITargetTrackingState;
  public command: ITargetTrackingPolicyCommand;

  constructor(private $uibModalInstance: IModalServiceInstance,
              private scalingPolicyWriter: ScalingPolicyWriter,
              private taskMonitorBuilder: TaskMonitorBuilder,
              public policy: ITitusTargetTrackingPolicy,
              public serverGroup: ITitusServerGroup,
              public alarmServerGroup: IAlarmRenderingServerGroup,
              public application: Application) {
    'ngInject';
  }

  public $onInit() {
    this.command = this.buildCommand();
    this.state = {
      unit: null,
      scaleInChanged: false,
    };
  }

  public scaleInChanged(): void {
    this.state.scaleInChanged = true;
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public save(): void {
    const action = this.policy.id ? 'Update' : 'Create';
    const command = cloneDeep(this.command);
    this.taskMonitor = this.taskMonitorBuilder.buildTaskMonitor({
      application: this.application,
      title: `${action} scaling policy for ${this.serverGroup.name}`,
      modalInstance: this.$uibModalInstance,
      submitMethod: () => this.scalingPolicyWriter.upsertScalingPolicy(this.application, command)
    });

    this.taskMonitor.submit();
  }

  private buildCommand(): ITargetTrackingPolicyCommand {
    return {
      scalingPolicyID: this.policy.id,
      type: 'upsertScalingPolicy',
      cloudProvider: 'titus',
      jobId: this.serverGroup.id,
      credentials: this.serverGroup.account,
      region: this.serverGroup.region,
      serverGroupName: this.serverGroup.name,
      adjustmentType: null,
      name: this.policy.id,
      targetTrackingConfiguration: Object.assign({}, this.policy.targetTrackingConfiguration),
    };
  }

}
