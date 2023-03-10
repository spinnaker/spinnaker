import type { StateService } from '@uirouter/angularjs';
import type { IController } from 'angular';
import { module } from 'angular';
import type { IModalServiceInstance } from 'angular-ui-bootstrap';
import { cloneDeep } from 'lodash';

import type { Application } from '@spinnaker/core';
import { LoadBalancerWriter, TaskMonitor } from '@spinnaker/core';

import type { CloudrunLoadBalancerTransformer, ICloudrunTrafficSplitDescription } from '../../loadBalancerTransformer';
import { CloudrunLoadBalancerUpsertDescription } from '../../loadBalancerTransformer';

import './wizard.less';

class CloudrunLoadBalancerWizardController implements IController {
  public state = { loading: true };
  public loadBalancer: CloudrunLoadBalancerUpsertDescription;
  public heading: string;
  public submitButtonLabel: string;
  public taskMonitor: TaskMonitor;

  public static $inject = [
    '$scope',
    '$state',
    '$uibModalInstance',
    'application',
    'loadBalancer',
    'isNew',
    'forPipelineConfig',
    'cloudrunLoadBalancerTransformer',
    'wizardSubFormValidation',
  ];
  constructor(
    public $scope: ng.IScope,
    private $state: StateService,
    private $uibModalInstance: IModalServiceInstance,
    private application: Application,
    loadBalancer: CloudrunLoadBalancerUpsertDescription,
    public isNew: boolean,
    private forPipelineConfig: boolean,
    private cloudrunLoadBalancerTransformer: CloudrunLoadBalancerTransformer,
    private wizardSubFormValidation: any,
  ) {
    this.submitButtonLabel = this.forPipelineConfig ? 'Done' : 'Update';

    if (this.isNew) {
      this.heading = 'Create New Load Balancer';
    } else {
      this.heading = `Edit ${[
        loadBalancer.name,
        loadBalancer.region,
        loadBalancer.account || loadBalancer.credentials,
      ].join(':')}`;
      this.cloudrunLoadBalancerTransformer
        .convertLoadBalancerForEditing(loadBalancer, application)
        .then((convertedLoadBalancer) => {
          this.loadBalancer = this.cloudrunLoadBalancerTransformer.convertLoadBalancerToUpsertDescription(
            convertedLoadBalancer,
          );
          if (loadBalancer.split && !this.loadBalancer.splitDescription) {
            this.loadBalancer.splitDescription = CloudrunLoadBalancerUpsertDescription.convertTrafficSplitToTrafficSplitDescription(
              loadBalancer.split,
            );
          } else {
            this.loadBalancer.splitDescription = loadBalancer.splitDescription;
          }
          this.loadBalancer.mapAllocationsToPercentages();
          this.setTaskMonitor();
          this.initializeFormValidation();
          this.state.loading = false;
        });
    }
  }

  public submit(): any {
    const description = cloneDeep(this.loadBalancer);
    description.mapAllocationsToPercentages();
    delete description.serverGroups;

    if (this.forPipelineConfig) {
      return this.$uibModalInstance.close(description);
    } else {
      return this.taskMonitor.submit(() => {
        return LoadBalancerWriter.upsertLoadBalancer(description, this.application, 'Update');
      });
    }
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public showSubmitButton(): boolean {
    return this.wizardSubFormValidation.subFormsAreValid();
  }

  private setTaskMonitor(): void {
    this.taskMonitor = new TaskMonitor({
      application: this.application,
      title: 'Updating your load balancer',
      modalInstance: this.$uibModalInstance,
      onTaskComplete: () => this.onTaskComplete(),
    });
  }

  private initializeFormValidation(): void {
    this.wizardSubFormValidation.config({ form: 'form', scope: this.$scope }).register({
      page: 'basic-settings',
      subForm: 'basicSettingsForm',
      validators: [
        {
          watchString: 'ctrl.loadBalancer.splitDescription',
          validator: (splitDescription: ICloudrunTrafficSplitDescription): boolean => {
            return (
              splitDescription.allocationDescriptions.reduce((sum, description) => sum + description.percent, 0) === 100
            );
          },
          watchDeep: true,
        },
      ],
    });
  }

  private onTaskComplete(): void {
    this.application.getDataSource('loadBalancers').refresh();
    this.application.getDataSource('loadBalancers').onNextRefresh(this.$scope, () => this.onApplicationRefresh());
  }

  private onApplicationRefresh(): void {
    // If the user has already closed the modal, do not navigate to the new details view
    if ((this.$scope as any).$$destroyed) {
      // $$destroyed is not in the ng.IScope interface
      return;
    }

    this.$uibModalInstance.dismiss();
    const newStateParams = {
      name: this.loadBalancer.name,
      accountId: this.loadBalancer.credentials,
      region: this.loadBalancer.region,
      provider: 'cloudrun',
    };

    if (!this.$state.includes('**.loadBalancerDetails')) {
      this.$state.go('.loadBalancerDetails', newStateParams);
    } else {
      this.$state.go('^.loadBalancerDetails', newStateParams);
    }
  }
}

export const CLOUDRUN_LOAD_BALANCER_WIZARD_CTRL = 'spinnaker.cloudrun.loadBalancer.wizard.controller';

module(CLOUDRUN_LOAD_BALANCER_WIZARD_CTRL, []).controller(
  'cloudrunLoadBalancerWizardCtrl',
  CloudrunLoadBalancerWizardController,
);
