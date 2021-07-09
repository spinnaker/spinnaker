import UIROUTER_ANGULARJS, { StateService } from '@uirouter/angularjs';
import { IScope, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';

import { Application, TaskMonitor } from '@spinnaker/core';

import { GCE_CACHE_REFRESH } from '../../../cache/cacheRefresh.component';
import { GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULESBUTTON_COMPONENT } from '../../details/hostAndPathRules/hostAndPathRulesButton.component';
import { IGceHttpLoadBalancer } from '../../../domain';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BACKENDSERVICE_BACKENDSERVICE_COMPONENT } from '../http/backendService/backendService.component';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BASICSETTINGS_BASICSETTINGS_COMPONENT } from '../http/basicSettings/basicSettings.component';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_COMMANDBUILDER_SERVICE } from '../http/commandBuilder.service';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HEALTHCHECK_HEALTHCHECK_COMPONENT } from '../http/healthCheck/healthCheck.component';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HOSTRULE_HOSTRULE_COMPONENT } from '../http/hostRule/hostRule.component';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HTTPLOADBALANCER_WRITE_SERVICE } from '../http/httpLoadBalancer.write.service';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_LISTENERS_LISTENER_COMPONENT } from '../http/listeners/listener.component';
import { BackendServiceTemplate, HealthCheckTemplate, HostRuleTemplate, ListenerTemplate } from '../http/templates';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_TRANSFORMER_SERVICE } from '../http/transformer.service';

import '../http/httpLoadBalancerWizard.component.less';

export const GOOGLE_LOADBALANCER_CONFIGURE_INTERNAL_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER =
  'spinnaker.deck.gce.loadBalancer.createInternalHttp.controller';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_INTERNAL_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER; // for backwards compatibility

class CreateInternalHttpLoadBalancerController implements ng.IComponentController {
  public taskMonitor: any;
  public command: any;
  public modalDescriptor: string;

  public pages = {
    location: require('../http/basicSettings/basicSettings.html'),
    listeners: require('../http/listeners/listeners.html'),
    defaultService: require('../http/defaultService/defaultService.html'),
    backendServices: require('../http/backendService/backendServices.html'),
    healthChecks: require('../http/healthCheck/healthChecks.html'),
    hostRules: require('../http/hostRule/hostRules.html'),
  };

  private keyToTemplateMap: { [key: string]: any } = {
    backendServices: BackendServiceTemplate,
    healthChecks: HealthCheckTemplate,
    hostRules: HostRuleTemplate,
    listeners: ListenerTemplate,
  };

  public static $inject = [
    '$scope',
    'application',
    '$uibModalInstance',
    'loadBalancer',
    'gceHttpLoadBalancerCommandBuilder',
    'isNew',
    'wizardSubFormValidation',
    'gceHttpLoadBalancerTransformer',
    'gceHttpLoadBalancerWriter',
    '$state',
  ];

  constructor(
    public $scope: IScope,
    public application: Application,
    public $uibModalInstance: IModalInstanceService,
    private loadBalancer: IGceHttpLoadBalancer,
    private gceHttpLoadBalancerCommandBuilder: any,
    private isNew: boolean,
    private wizardSubFormValidation: any,
    private gceHttpLoadBalancerTransformer: any,
    private gceHttpLoadBalancerWriter: any,
    private $state: StateService,
  ) {
    this.modalDescriptor = this.isNew
      ? 'Create Internal HTTP(S) load balancer'
      : `Edit ${this.loadBalancer.name}:${this.loadBalancer.region}:${this.loadBalancer.account}`;

    const onTaskComplete = () => {
      application.loadBalancers.refresh();
      application.loadBalancers.onNextRefresh($scope, this.onApplicationRefresh.bind(this));
    };

    $scope.taskMonitor = this.taskMonitor = new TaskMonitor({
      application: this.application,
      title: (this.isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: this.$uibModalInstance,
      onTaskComplete: onTaskComplete,
    });
  }

  public $onInit(): void {
    this.gceHttpLoadBalancerCommandBuilder
      .buildCommand({ isNew: this.isNew, originalLoadBalancer: this.loadBalancer, isInternal: true })
      .then((command: any) => {
        this.command = command;
        this.wizardSubFormValidation
          .config({ scope: this.$scope, form: 'form' })
          .register({ page: 'location', subForm: 'location' })
          .register({
            page: 'listeners',
            subForm: 'listeners',
            validators: [
              {
                watchString: 'ctrl.command.loadBalancer.listeners',
                validator: (listeners: any[]) => listeners.length > 0,
                collection: true,
              },
            ],
          })
          .register({ page: 'default-service', subForm: 'defaultService' })
          .register({ page: 'health-checks', subForm: 'healthChecks' })
          .register({ page: 'backend-services', subForm: 'backendServices' })
          .register({ page: 'host-rules', subForm: 'hostRules' });
      });
  }

  public add(key: string): void {
    this.command.loadBalancer[key].push(new this.keyToTemplateMap[key]());
  }

  public remove(key: string, index: number): void {
    this.command.loadBalancer[key].splice(index, 1);
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public submit(): void {
    const serializedCommands = this.gceHttpLoadBalancerTransformer.serialize(this.command, this.loadBalancer);
    const descriptor = this.isNew ? 'Create' : 'Update';
    this.taskMonitor.submit(() =>
      this.gceHttpLoadBalancerWriter.upsertLoadBalancers(serializedCommands, this.application, descriptor),
    );
  }

  private onApplicationRefresh(): void {
    // If the user has already closed the modal, do not navigate to the new details view
    if (this.$scope.$$destroyed) {
      return;
    }
    this.$uibModalInstance.close();
    const lb = this.command.loadBalancer;
    const newStateParams = {
      name: lb.urlMapName,
      accountId: lb.credentials,
      region: lb.region,
      provider: 'gce',
    };

    if (!this.$state.includes('**.loadBalancerDetails')) {
      this.$state.go('.loadBalancerDetails', newStateParams);
    } else {
      this.$state.go('^.loadBalancerDetails', newStateParams);
    }
  }
}

module(GOOGLE_LOADBALANCER_CONFIGURE_INTERNAL_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER, [
  'ui.bootstrap',
  UIROUTER_ANGULARJS,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BACKENDSERVICE_BACKENDSERVICE_COMPONENT,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BASICSETTINGS_BASICSETTINGS_COMPONENT,
  GCE_CACHE_REFRESH,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_COMMANDBUILDER_SERVICE,
  GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULESBUTTON_COMPONENT,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HEALTHCHECK_HEALTHCHECK_COMPONENT,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HOSTRULE_HOSTRULE_COMPONENT,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HTTPLOADBALANCER_WRITE_SERVICE,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_LISTENERS_LISTENER_COMPONENT,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_TRANSFORMER_SERVICE,
]).controller('gceCreateInternalHttpLoadBalancerCtrl', CreateInternalHttpLoadBalancerController);
