import {module} from 'angular';
import {IStateService} from 'angular-ui-router';
import {reduce} from 'lodash';

import {Application} from 'core/application/application.model';
import {IAppengineLoadBalancer} from 'appengine/domain/index';
import {AppengineLoadBalancerTransformer} from 'appengine/loadBalancer/transformer';

import './wizard.less';

class AppengineLoadBalancerWizardController {
  public loadBalancer: IAppengineLoadBalancer;
  public heading: string;
  public taskMonitor: any;

  static get $inject() { return [
    '$scope',
    '$state',
    '$uibModalInstance',
    'application',
    'loadBalancer',
    'isNew',
    'forPipelineConfig',
    'appengineLoadBalancerTransformer',
    'taskMonitorService',
    'loadBalancerWriter',
    'wizardSubFormValidation']; }

  constructor(public $scope: ng.IScope,
              private $state: IStateService,
              private $uibModalInstance: any,
              private application: Application,
              loadBalancer: IAppengineLoadBalancer,
              public isNew: boolean,
              private forPipelineConfig: boolean,
              private transformer: AppengineLoadBalancerTransformer,
              taskMonitorService: any,
              private loadBalancerWriter: any,
              private wizardSubFormValidation: any) {
    if (this.isNew) {
      this.heading = 'Create New Load Balancer';
    } else {
      this.heading = `Edit ${[loadBalancer.name, loadBalancer.region, loadBalancer.account].join(':')}`;
      this.loadBalancer = this.transformer.convertLoadBalancerForEditing(loadBalancer);

      this.taskMonitor = taskMonitorService.buildTaskMonitor({
        application: this.application,
        title: 'Updating your load balancer',
        modalInstance: this.$uibModalInstance,
        onTaskComplete: () => this.onTaskComplete(),
      });

      this.wizardSubFormValidation.config({form: 'form', scope: this.$scope})
        .register({
          page: 'basic-settings',
          subForm: 'basicSettingsForm',
          validators: [
            {
              watchString: 'ctrl.loadBalancer.split.allocations',
              validator: (allocations: {[serverGroup: string]: number}): boolean => {
                return reduce(allocations, (sum: number, allocation: number) => sum + allocation, 0) === 100;
              },
              watchDeep: true
            }
          ]
        })
        .register({page: 'advanced-settings', subForm: 'advancedSettingsForm'});
    }
  }

  public submit(): void {
    let description = this.transformer.convertLoadBalancerToUpsertDescription(this.loadBalancer);
    this.taskMonitor.submit(() => {
      return this.loadBalancerWriter
        .upsertLoadBalancer(description, this.application, 'Update', {cloudProvider: 'appengine'});
    });
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public showSubmitButton(): boolean {
    return this.wizardSubFormValidation.subFormsAreValid();
  }

  private onTaskComplete(): void {
    this.application.getDataSource('loadBalancers').refresh();
    this.application.getDataSource('loadBalancers').onNextRefresh(this.$scope, () => this.onApplicationRefresh());
  }

  private onApplicationRefresh(): void {
    // If the user has already closed the modal, do not navigate to the new details view
    if ((this.$scope as any).$$destroyed) { // $$destroyed is not in the ng.IScope interface
      return;
    }

    this.$uibModalInstance.dismiss();
    let newStateParams = {
      name: this.loadBalancer.name,
      accountId: this.loadBalancer.account,
      region: this.loadBalancer.region,
      provider: 'appengine',
    };

    if (!this.$state.includes('**.loadBalancerDetails')) {
      this.$state.go('.loadBalancerDetails', newStateParams);
    } else {
      this.$state.go('^.loadBalancerDetails', newStateParams);
    }
  }
}

export const APPENGINE_LOAD_BALANCER_WIZARD_CTRL = 'spinnaker.appengine.loadBalancer.wizard.controller';

module(APPENGINE_LOAD_BALANCER_WIZARD_CTRL, [
  require('core/task/monitor/taskMonitorService.js'),
  require('core/loadBalancer/loadBalancer.write.service.js'),
  require('core/modal/wizard/wizardSubFormValidation.service.js'),
]).controller('appengineLoadBalancerWizardCtrl', AppengineLoadBalancerWizardController);

