import { IController, IComponentOptions, module } from 'angular';

class LoadBalancerSelectorController implements IController {
  public command: any;
}

export const applicationLoadBalancerSelectorComponent: IComponentOptions = {
  bindings: {
    command: '=',
  },
  controller: LoadBalancerSelectorController,
  templateUrl: require('./networkingSelector.component.html'),
};

export const ECS_NETWORKING_SECTION = 'spinnaker.ecs.serverGroup.configure.wizard.loadBalancers.selector.component';
module(ECS_NETWORKING_SECTION, []).component(
  'ecsServerGroupLoadBalancerSelector',
  applicationLoadBalancerSelectorComponent,
);
