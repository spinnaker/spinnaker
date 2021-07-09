import { IController, module } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { cloneDeep } from 'lodash';

import { Application, TaskMonitor } from '@spinnaker/core';
import { GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE } from '../../../../autoscalingPolicy/autoscalingPolicy.write.service';
import { IGceAutoHealingPolicy, IGceServerGroup } from '../../../../domain/index';
import { GCE_HEALTH_CHECK_READER, GceHealthCheckReader } from '../../../../healthCheck/healthCheck.read.service';
import { getHealthCheckOptions, IGceHealthCheckOption } from '../../../../healthCheck/healthCheckUtils';

import './upsertAutoHealingPolicy.modal.less';

class GceUpsertAutoHealingPolicyModalCtrl implements IController {
  public autoHealingPolicy: IGceAutoHealingPolicy;
  public taskMonitor: TaskMonitor;
  public healthChecks: IGceHealthCheckOption[];
  public action: 'Edit' | 'New';
  public isNew: boolean;
  public submitButtonLabel: string;

  public static $inject = [
    '$uibModalInstance',
    'application',
    'serverGroup',
    'gceHealthCheckReader',
    'gceAutoscalingPolicyWriter',
  ];
  constructor(
    private $uibModalInstance: IModalServiceInstance,
    private application: Application,
    public serverGroup: IGceServerGroup,
    private gceHealthCheckReader: GceHealthCheckReader,
    private gceAutoscalingPolicyWriter: any,
  ) {
    this.initialize();
  }

  public submit(): void {
    const submitMethod = () => {
      return this.gceAutoscalingPolicyWriter.upsertAutoHealingPolicy(
        this.application,
        this.serverGroup,
        this.autoHealingPolicy,
      );
    };
    this.taskMonitor.submit(submitMethod);
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public setAutoHealingPolicy(autoHealingPolicy: IGceAutoHealingPolicy): void {
    this.autoHealingPolicy = autoHealingPolicy;
  }

  public onHealthCheckRefresh(): void {
    this.gceHealthCheckReader.listHealthChecks().then((healthChecks) => {
      const matchingHealthChecks = healthChecks.filter((hc) => hc.account === this.serverGroup.account);
      this.healthChecks = getHealthCheckOptions(matchingHealthChecks);
    });
  }

  private initialize(): void {
    this.onHealthCheckRefresh();
    this.action = this.serverGroup.autoHealingPolicy ? 'Edit' : 'New';
    this.isNew = !this.serverGroup.autoHealingPolicy;
    this.submitButtonLabel = this.isNew ? 'Create' : 'Update';
    if (!this.isNew) {
      this.autoHealingPolicy = cloneDeep(this.serverGroup.autoHealingPolicy);
    }
    this.taskMonitor = new TaskMonitor({
      application: this.application,
      title: `${this.action} autohealing policy for ${this.serverGroup.name}`,
      modalInstance: this.$uibModalInstance,
    });
  }
}

export const GCE_UPSERT_AUTOHEALING_POLICY_MODAL_CTRL = 'spinnaker.gce.upsertAutoHealingPolicy.modal.controller';
module(GCE_UPSERT_AUTOHEALING_POLICY_MODAL_CTRL, [
  GCE_HEALTH_CHECK_READER,
  GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE,
]).controller('gceUpsertAutoHealingPolicyModalCtrl', GceUpsertAutoHealingPolicyModalCtrl);
