import { IComponentOptions, module } from 'angular';

const resizeCapacityComponent: IComponentOptions = {
  bindings: {
    command: '=',
    currentSize: '='
  },
  templateUrl: require('./resizeCapacity.component.html'),
  controller: () => {}
};

export const AWS_RESIZE_CAPACITY_COMPONENT = 'spinnaker.amazon.serverGroup.resize';
module(AWS_RESIZE_CAPACITY_COMPONENT, []).component('awsResizeCapacity', resizeCapacityComponent);
