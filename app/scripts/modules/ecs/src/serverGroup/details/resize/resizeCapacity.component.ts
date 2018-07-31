import { IComponentOptions, module } from 'angular';

const resizeCapacityComponent: IComponentOptions = {
  bindings: {
    command: '=',
    currentSize: '=',
  },
  templateUrl: require('./resizeCapacity.component.html'),
  controller: () => {},
};

export const ECS_RESIZE_CAPACITY_COMPONENT = 'spinnaker.ecs.serverGroup.resize';
module(ECS_RESIZE_CAPACITY_COMPONENT, []).component('ecsResizeCapacity', resizeCapacityComponent);
