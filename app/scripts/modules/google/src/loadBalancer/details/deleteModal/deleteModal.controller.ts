import { IController, module } from 'angular';
import ANGULAR_UI_BOOTSTRAP, { IModalInstanceService } from 'angular-ui-bootstrap';

import { Application, ILoadBalancerDeleteCommand, LoadBalancerWriter, TaskMonitor } from '@spinnaker/core';

import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HTTPLOADBALANCER_WRITE_SERVICE } from '../../configure/http/httpLoadBalancer.write.service';
import { GCE_HTTP_LOAD_BALANCER_UTILS, GceHttpLoadBalancerUtils } from '../../httpLoadBalancerUtils.service';

class Verification {
  public verified = false;
}

class Params {
  public deleteHealthChecks = false;
}

interface IGoogleLoadBalancerDeleteOperation extends ILoadBalancerDeleteCommand {
  region: string;
  accountName: string;
  deleteHealthChecks: boolean;
  loadBalancerType: string;
}

class DeleteLoadBalancerModalController implements IController {
  public verification: Verification = new Verification();
  public params: Params = new Params();
  public taskMonitor: any;

  public static $inject = [
    'application',
    'gceHttpLoadBalancerUtils',
    'gceHttpLoadBalancerWriter',
    'loadBalancer',
    '$uibModalInstance',
  ];
  constructor(
    private application: Application,
    private gceHttpLoadBalancerUtils: GceHttpLoadBalancerUtils,
    private gceHttpLoadBalancerWriter: any,
    private loadBalancer: any,
    private $uibModalInstance: IModalInstanceService,
  ) {}

  public $onInit(): void {
    const taskMonitorConfig = {
      application: this.application,
      title: 'Deleting ' + this.loadBalancer.name,
      modalInstance: this.$uibModalInstance,
    };

    this.taskMonitor = new TaskMonitor(taskMonitorConfig);
  }

  public isValid(): boolean {
    return this.verification.verified;
  }

  public submit(): void {
    this.taskMonitor.submit(this.getSubmitMethod());
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public hasHealthChecks(): boolean {
    if (this.gceHttpLoadBalancerUtils.isHttpLoadBalancer(this.loadBalancer)) {
      return true;
    } else {
      return !!this.loadBalancer.healthCheck;
    }
  }

  private getSubmitMethod(): () => PromiseLike<any> {
    if (this.gceHttpLoadBalancerUtils.isHttpLoadBalancer(this.loadBalancer)) {
      return () => {
        return this.gceHttpLoadBalancerWriter.deleteLoadBalancers(this.loadBalancer, this.application, this.params);
      };
    } else {
      return () => {
        const command: IGoogleLoadBalancerDeleteOperation = {
          cloudProvider: 'gce',
          loadBalancerName: this.loadBalancer.name,
          accountName: this.loadBalancer.account,
          credentials: this.loadBalancer.account,
          region: this.loadBalancer.region,
          loadBalancerType: this.loadBalancer.loadBalancerType || 'NETWORK',
          deleteHealthChecks: this.params.deleteHealthChecks,
        };
        return LoadBalancerWriter.deleteLoadBalancer(command, this.application);
      };
    }
  }
}

export const DELETE_MODAL_CONTROLLER = 'spinnaker.gce.loadBalancer.deleteModal.controller';
module(DELETE_MODAL_CONTROLLER, [
  ANGULAR_UI_BOOTSTRAP as any,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HTTPLOADBALANCER_WRITE_SERVICE,
  GCE_HTTP_LOAD_BALANCER_UTILS,
]).controller('gceLoadBalancerDeleteModalCtrl', DeleteLoadBalancerModalController);
