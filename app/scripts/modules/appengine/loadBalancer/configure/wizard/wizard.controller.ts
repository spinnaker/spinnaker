import {Application} from 'core/application/application.model';
import {LoadBalancer} from 'core/domain/index';

class AppengineLoadBalancerWizardController {
  static get $inject() { return ['$uibModalInstance', 'application', 'loadBalancer', 'isNew', 'forPipelineConfig']; }

  constructor(private $uibModalInstance: any,
              private application: Application,
              private loadBalancer: LoadBalancer,
              public isNew: boolean,
              private forPipelineConfig: boolean) { }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }
}

export const APPENGINE_LOAD_BALANCER_WIZARD_CTRL = 'spinnaker.appengine.loadBalancer.wizard.controller';

angular.module(APPENGINE_LOAD_BALANCER_WIZARD_CTRL, [])
  .controller('appengineLoadBalancerWizardCtrl', AppengineLoadBalancerWizardController);
