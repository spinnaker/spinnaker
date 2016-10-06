import {module} from 'angular';
import {Application} from '../../../../core/application/application.model';

class Verification {
  verified: boolean = false;
}

class Params {
  deleteHealthChecks: boolean = false;
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
               private loadBalancerWriter: any,
               private taskMonitorService: any,
               private $uibModalInstance: any) {}

  public $onInit (): void {
    // The core load balancer writer expects these fields on the load balancer.
    this.loadBalancer.accountId = this.loadBalancer.account;
    this.loadBalancer.providerType = this.loadBalancer.provider;
  }

  public isValid (): boolean {
    return this.verification.verified;
  }

  public submit (): void {
    let taskMonitorConfig = {
      modalInstance: this.$uibModalInstance,
      application: this.application,
      title: 'Deleting ' + this.loadBalancer.name,
    };

    this.taskMonitor = this.taskMonitorService.buildTaskMonitor(taskMonitorConfig);
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
        return this.loadBalancerWriter.deleteLoadBalancer(this.loadBalancer, this.application, {
          loadBalancerName: this.loadBalancer.name,
          region: this.loadBalancer.region,
          loadBalancerType: this.loadBalancer.loadBalancerType || 'NETWORK',
          deleteHealthChecks: this.params.deleteHealthChecks,
        });
      };
    }
  }
}

const moduleName = 'spinnaker.gce.loadBalancer.deleteModal.controller';

module(moduleName, [
    require('angular-ui-bootstrap'),
    require('../../../../core/task/monitor/taskMonitorService.js'),
    require('../../../../core/loadBalancer/loadBalancer.write.service.js'),
    require('../../configure/http/httpLoadBalancer.write.service.js'),
  ])
  .controller('gceLoadBalancerDeleteModalCtrl', DeleteLoadBalancerModalController);

export default moduleName;
