import { ApplicationNameValidator, FirewallLabels, IApplicationNameValidator } from '@spinnaker/core';

class TitusApplicationNameValidator implements IApplicationNameValidator {
  private validateSpecialCharacters(name: string, errors: string[]): void {
    const pattern = /^[a-zA-Z_0-9.]*$/g;
    if (!pattern.test(name)) {
      errors.push('Only dot(.) and underscore(_) special characters are allowed.');
    }
  }

  private validateLength(name: string, warnings: string[], errors: string[]): void {
    if (name.length > 250) {
      errors.push('The maximum length for an application in Titus is 250 characters.');
      return;
    }
    if (name.length > 240) {
      if (name.length >= 248) {
        warnings.push(
          `You will not be able to include a stack or detail field for clusters or ${FirewallLabels.get('firewalls')}.`,
        );
      } else {
        const remaining = 248 - name.length;
        warnings.push(`If you plan to include a stack or detail field for clusters, you will only
            have ~${remaining} characters to do so.`);
      }
    }
  }

  public validate(name = '') {
    const warnings: string[] = [];
    const errors: string[] = [];

    if (name && name.length) {
      this.validateSpecialCharacters(name, errors);
      this.validateLength(name, warnings, errors);
    }

    return { warnings, errors };
  }
}

ApplicationNameValidator.registerValidator('titus', new TitusApplicationNameValidator());
