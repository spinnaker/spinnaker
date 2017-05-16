import { module } from 'angular';

import { IBuildInfo } from 'core/domain';

export function buildDisplayName() {
  return function(input: IBuildInfo): string {
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
