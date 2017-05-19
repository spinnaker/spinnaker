import { module } from 'angular';

export const HEALTH_PERCENT_SELECTOR = 'spinnaker.amazon.serverGroup.configure.wizard.capacity.targetHealthyPercentageSelector';
module(HEALTH_PERCENT_SELECTOR, [])
  .component('awsTargetHealthyPercentageSelector', {
    bindings: {
      command: '='
    },
    templateUrl: require('./targetHealthyPercentageSelector.component.html'),
    controller: () => {}
  });
