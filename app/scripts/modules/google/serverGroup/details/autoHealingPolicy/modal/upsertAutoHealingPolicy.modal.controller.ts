import {module} from 'angular';
import {chain, cloneDeep, last} from 'lodash';
import {IModalServiceInstance} from 'angular-ui-bootstrap';
import {Application} from 'core/application/application.model';
import {IGceAutoHealingPolicy, IGceServerGroup} from 'google/domain/index';
import {TaskMonitorBuilder, TaskMonitor} from 'core/task/monitor/taskMonitor.builder';

import './upsertAutoHealingPolicy.modal.less';

class GceUpsertAutoHealingPolicyModalCtrl {
  public autoHealingPolicy: IGceAutoHealingPolicy;
  public taskMonitor: TaskMonitor;
  public httpHealthChecks: string[];
  public action: 'Edit' | 'New';
  public isNew: boolean;

  public static get $inject() {
    return ['$uibModalInstance', 'application', 'serverGroup', 'gceHttpHealthCheckReader',
            'taskMonitorBuilder', 'gceAutoscalingPolicyWriter'];
  }

  constructor(private $uibModalInstance: IModalServiceInstance,
              private application: Application,
              public serverGroup: IGceServerGroup,
              private gceHttpHealthCheckReader: any,
              private taskMonitorBuilder: TaskMonitorBuilder,
              private gceAutoscalingPolicyWriter: any) {
    this.initialize();
  }

  public submit(): void {
    let submitMethod = () => {
      return this.gceAutoscalingPolicyWriter
        .upsertAutoHealingPolicy(this.application, this.serverGroup, this.autoHealingPolicy);
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
    this.gceHttpHealthCheckReader.listHttpHealthChecks()
      .then((response: any) => {
        this.httpHealthChecks = chain(response[0].results)
          .filter({provider: 'gce', account: this.serverGroup.account})
          .map('name')
          .value() as string[];
      });
  }

  private initialize(): void {
    this.onHealthCheckRefresh();
    this.action = this.serverGroup.autoHealingPolicy ? 'Edit' : 'New';
    this.isNew = !this.serverGroup.autoHealingPolicy;
    if (!this.isNew) {
      this.autoHealingPolicy = cloneDeep(this.serverGroup.autoHealingPolicy);
      if (this.autoHealingPolicy.healthCheck) {
        this.autoHealingPolicy.healthCheck = last(this.autoHealingPolicy.healthCheck.split('/'));
      }
    }
    this.taskMonitor = this.taskMonitorBuilder.buildTaskMonitor({
      application: this.application,
      title: `${this.action} autohealing policy for ${this.serverGroup.name}`,
      modalInstance: this.$uibModalInstance,
    });
  }
}

export const GCE_UPSERT_AUTOHEALING_POLICY_MODAL_CTRL = 'spinnaker.gce.upsertAutoHealingPolicy.modal.controller';
module(GCE_UPSERT_AUTOHEALING_POLICY_MODAL_CTRL, [
  require('google/httpHealthCheck/httpHealthCheck.reader.js'),
  require('google/autoscalingPolicy/autoscalingPolicy.write.service.js'),
]).controller('gceUpsertAutoHealingPolicyModalCtrl', GceUpsertAutoHealingPolicyModalCtrl);
