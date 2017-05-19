import { module } from 'angular';

export const AZ_REBALANCE_SELECTOR = 'spinnaker.amazon.serverGroup.configure.wizard.capacity.azRebalance.selector';

module(AZ_REBALANCE_SELECTOR, []).component('azRebalanceSelector',
  {
    bindings: {
      command: '=',
    },
    templateUrl: require('./azRebalanceSelector.component.html'),
    controller: () => {},
  });
