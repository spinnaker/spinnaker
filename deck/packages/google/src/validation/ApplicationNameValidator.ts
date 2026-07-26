import type { IApplicationNameValidator, IValidationResult } from '@spinnaker/core';
import { ApplicationNameValidator, FirewallLabels } from '@spinnaker/core';

export class GoogleApplicationNameValidator implements IApplicationNameValidator {
  private validateSpecialCharacters(name: string, errors: string[]): void {
    const pattern = /^([a-zA-Z][a-zA-Z0-9]*)?$/;
    if (!pattern.test(name)) {
      errors.push(
        'The application name must begin with a letter and must contain only letters or digits. No ' +
          'special characters are allowed.',
      );
    }
  }

  private validateLength(name: string, warnings: string[], errors: string[]): void {
    const maxResourceNameLength = 63;
    const maxLengthForLoadBalancers = maxResourceNameLength - 17;
    const maxLengthForServerGroups = maxResourceNameLength - 10;

    if (name.length > maxResourceNameLength) {
      errors.push('The maximum length for an application in Google is 63 characters.');
      return;
    }

    if (name.length > maxLengthForLoadBalancers - 12) {
      if (name.length > maxLengthForLoadBalancers) {
        warnings.push(`You will not be able to create a Google load balancer for this application if the
            application's name is longer than ${maxLengthForLoadBalancers} characters (currently: ${name.length}
          characters).`);
      } else if (name.length >= maxLengthForLoadBalancers - 2) {
        warnings.push(
          'With separators ("-"), you will not be able to include a stack and detail field for ' +
            'Google load balancers.',
        );
      } else {
        const remaining = maxLengthForLoadBalancers - 2 - name.length;
        warnings.push(`If you plan to include a stack or detail field for Google load balancers, you will only
            have ~${remaining} characters to do so.`);
      }
    }

    if (name.length > maxLengthForServerGroups - 12) {
      if (name.length > maxLengthForServerGroups) {
        warnings.push(`You will not be able to create a Google server group for this application if the
            application's name is longer than ${maxLengthForServerGroups} characters (currently: ${name.length}
            characters).`);
      } else if (name.length >= maxLengthForServerGroups - 2) {
        warnings.push(
          'With separators ("-"), you will not be able to include a stack and detail field for ' +
            'Google server groups.',
        );
      } else {
        const remaining = maxLengthForServerGroups - 2 - name.length;
        warnings.push(`If you plan to include a stack or detail field for Google server groups, you will only
            have ~${remaining} characters to do so.`);
      }
    }

    if (name.length > maxResourceNameLength - 12) {
      if (name.length >= maxResourceNameLength - 2) {
        warnings.push(
          `With separators ("-"), you will not be able to include a stack and detail field for Google ${FirewallLabels.get(
            'firewalls',
          )}.`,
        );
      } else {
        const remaining = maxResourceNameLength - 2 - name.length;
        warnings.push(`If you plan to include a stack or detail field for Google ${FirewallLabels.get(
          'firewalls',
        )}, you will only
            have ~${remaining} characters to do so.`);
      }
    }
  }

  public validate(name = ''): IValidationResult {
    const warnings: string[] = [];
    const errors: string[] = [];

    if (name.length) {
      this.validateSpecialCharacters(name, errors);
      this.validateLength(name, warnings, errors);
    }

    return { warnings, errors };
  }
}

ApplicationNameValidator.registerValidator('gce', new GoogleApplicationNameValidator());
