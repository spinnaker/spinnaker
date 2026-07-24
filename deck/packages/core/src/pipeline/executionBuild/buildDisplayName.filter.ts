import type { IBuildInfo } from '../../domain';

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
