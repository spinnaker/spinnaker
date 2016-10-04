import { module } from 'angular';

import { BuildInfo } from '../../domain';

const MODULE_NAME = 'spinnaker.core.delivery.buildDisplayName.filter';

export function buildDisplayName() {
  return function(input: BuildInfo): string {
    if (!input) {
      return '';
    }
    let formattedInput = '';
    if (input.fullDisplayName !== undefined) {
      formattedInput = input.fullDisplayName.split('#' + input.number).pop();
    }
    return formattedInput;
  };
}

module(MODULE_NAME, [])
  .filter('buildDisplayName', buildDisplayName);

export default MODULE_NAME;
