import { module } from 'angular';

import { IBuildInfo } from '../../domain';

export function buildDisplayName(input: IBuildInfo): string {
  if (!input) {
    return '';
  }
  let formattedInput = '';
  if (input.fullDisplayName !== undefined) {
    formattedInput = input.fullDisplayName.split('#' + input.number).pop();
  }
  return formattedInput;
}

export function buildDisplayNameFilter() {
  return buildDisplayName;
}

export const BUILD_DISPLAY_NAME_FILTER = 'spinnaker.core.pipeline.buildDisplayName.filter';
module(BUILD_DISPLAY_NAME_FILTER, []).filter('buildDisplayName', buildDisplayNameFilter);
