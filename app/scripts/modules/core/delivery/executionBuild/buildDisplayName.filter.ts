import { module } from 'angular';

import { BuildInfo } from '../../domain';

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

export const BUILD_DISPLAY_NAME_FILTER = 'spinnaker.core.delivery.buildDisplayName.filter';
module(BUILD_DISPLAY_NAME_FILTER, [])
  .filter('buildDisplayName', buildDisplayName);
