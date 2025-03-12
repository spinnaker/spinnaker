import { module } from 'angular';

export function robotToHuman(input: string): string {
  if (!input) {
    return '';
  }
  let formattedInput = input.charAt(0).toUpperCase() + input.substr(1);

  if (/\s/g.test(formattedInput)) {
    return formattedInput;
  }

  // clear camel case.
  formattedInput = formattedInput.replace(/[A-Z]/g, ' $&');

  // clear snake case
  formattedInput = formattedInput.replace(/_[a-z]/g, function (str) {
    return ' ' + str.charAt(1).toUpperCase() + str.substr(2);
  });

  // then clear dash case
  formattedInput = formattedInput.replace(/-[a-z]/g, function (str) {
    return ' ' + str.charAt(1).toUpperCase() + str.substr(2);
  });

  formattedInput = formattedInput.replace(/([A-Z])\s([A-Z])\s/g, '$1$2');

  return formattedInput;
}

export function robotToHumanFilter() {
  return robotToHuman;
}

export const ROBOT_TO_HUMAN_FILTER = 'spinnaker.core.presentation.robotToHuman.filter';
module(ROBOT_TO_HUMAN_FILTER, []).filter('robotToHuman', robotToHumanFilter);
