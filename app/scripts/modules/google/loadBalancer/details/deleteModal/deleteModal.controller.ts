import {module} from 'angular';
import {Application} from 'core/application/application.model';
import {
  LOAD_BALANCER_WRITE_SERVICE, LoadBalancerWriter,
  ILoadBalancerDeleteDescription
} from 'core/loadBalancer/loadBalancer.write.service';

class Verification {
  verified: boolean = false;
}

class Params {
  deleteHealthChecks: boolean = false;
}

interface IGoogleLoadBalancerDeleteOperation extends ILoadBalancerDeleteDescription {
  region: string;
  accountName: string;
  deleteHealthChecks: boolean;
  loadBalancerType: string;
}

class DeleteLoadBalancerModalController implements ng.IComponentController {
  public verification: Verification = new Verification();
  public params: Params = new Params();
  public taskMonitor: any;

  static get $inject () {
    return [
      'application',
      'elSevenUtils',
      'gceHttpLoadBalancerWriter',
      'loadBalancer',
      'loadBalancerWriter',
      'taskMonitorService',
      '$uibModalInstance',
    ];
  }

  constructor (private application: Application,
               private elSevenUtils: any,
               private gceHttpLoadBalancerWriter: any,
               private loadBalancer: any,
               private loadBalancerWriter: LoadBalancerWriter,
               private taskMonitorService: any,
               private $uibModalInstance: any) {}

  public $onInit (): void {

    let taskMonitorConfig = {
      modalInstance: this.$uibModalInstance,
      application: this.application,
      title: 'Deleting ' + this.loadBalancer.name,
    };

    this.taskMonitor = this.taskMonitorService.buildTaskMonitor(taskMonitorConfig);
  }

  public isValid (): boolean {
    return this.verification.verified;
  }

  public submit (): void {
    this.taskMonitor.submit(this.getSubmitMethod());
  }

  public cancel (): void {
    this.$uibModalInstance.dismiss();
  }

  public hasHealthChecks (): boolean {
    if (this.elSevenUtils.isElSeven(this.loadBalancer)) {
      return true;
    } else {
      return !!this.loadBalancer.healthCheck;
    }
  }

  private getSubmitMethod (): {(): ng.IPromise<any>} {
    if (this.elSevenUtils.isElSeven(this.loadBalancer)) {
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
        return this.loadBalancerWriter.deleteLoadBalancer(command, this.application);
      };
    }
  }
}

export const DELETE_MODAL_CONTROLLER = 'spinnaker.gce.loadBalancer.deleteModal.controller';
module(DELETE_MODAL_CONTROLLER, [
    require('angular-ui-bootstrap'),
    require('core/task/monitor/taskMonitorService.js'),
    LOAD_BALANCER_WRITE_SERVICE,
    require('../../configure/http/httpLoadBalancer.write.service.js'),
  ])
  .controller('gceLoadBalancerDeleteModalCtrl', DeleteLoadBalancerModalController);
