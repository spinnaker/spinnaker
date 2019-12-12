import { ApplicationNameValidator, IApplicationNameValidator, IValidationResult } from '@spinnaker/core';

// See https://cloud.google.com/appengine/docs/admin-api/reference/rest/v1/apps.services.versions#Version
class AppengineApplicationNameValidator implements IApplicationNameValidator {
  public validate(name = ''): IValidationResult {
    const warnings: string[] = [];
    const errors: string[] = [];
    if (name.length) {
      this.validateSpecialCharacters(name, errors);
      this.validateLength(name, warnings, errors);
    }
    return { warnings, errors };
  }

  private validateSpecialCharacters(name: string, errors: string[]): void {
    const pattern = /^[a-z0-9]*$/g;
    if (!pattern.test(name)) {
      errors.push('Only numbers and lowercase letters are allowed.');
    }
  }

  private validateLength(name: string, warnings: string[], errors: string[]): void {
    if (name.length > 58) {
      errors.push('The maximum length for an App Engine application name is 63 characters.');
      return;
    }
    if (name.length > 48) {
      if (name.length >= 56) {
        warnings.push('You will not be able to include a stack or detail field for clusters.');
      } else {
        const remaining = 56 - name.length;
        warnings.push(`If you plan to include a stack or detail field for clusters, you will only have
                       ${remaining} character${remaining > 1 ? 's' : ''} to do so.`);
      }
    }
  }
}

ApplicationNameValidator.registerValidator('appengine', new AppengineApplicationNameValidator());
