import { module } from 'angular';

export const HEALTH_PERCENT_SELECTOR =
  'spinnaker.core.serverGroup.configure.wizard.capacity.targetHealthyPercentageSelector';
module(HEALTH_PERCENT_SELECTOR, []).component('targetHealthyPercentageSelector', {
  bindings: {
    command: '=',
  },
  templateUrl: require('./targetHealthyPercentageSelector.component.html'),
  controller: () => {},
});
