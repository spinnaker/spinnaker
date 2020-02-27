import { ApplicationNameValidator } from '@spinnaker/core';

class CloudFoundryApplicationNameValidator {
  private static MAX_RESOURCE_NAME_LENGTH = 63;

  private static validateSpecialCharacters(name: string, _warnings: string[], errors: string[]): void {
    const alphanumPattern = /^([a-zA-Z][a-zA-Z0-9]*)?$/;
    if (!alphanumPattern.test(name)) {
      const alphanumWithDashPattern = /^([a-zA-Z][a-zA-Z0-9-]*)?$/;
      if (!alphanumWithDashPattern.test(name)) {
        errors.push(
          'The application name must begin with a letter and must contain only letters, digits or dashes. ' +
            'No special characters are allowed.',
        );
      }
    }
  }

  private static validateLength(name: string, errors: string[]): void {
    if (name.length > CloudFoundryApplicationNameValidator.MAX_RESOURCE_NAME_LENGTH) {
      errors.push(
        `The maximum length for an application in Cloudfoundry is ${CloudFoundryApplicationNameValidator.MAX_RESOURCE_NAME_LENGTH} characters.`,
      );
    }
  }

  public validate(name = '') {
    const warnings: string[] = [];
    const errors: string[] = [];

    if (name && name.length) {
      CloudFoundryApplicationNameValidator.validateSpecialCharacters(name, warnings, errors);
      CloudFoundryApplicationNameValidator.validateLength(name, errors);
    }

    return {
      warnings,
      errors,
    };
  }
}

ApplicationNameValidator.registerValidator('cloudfoundry', new CloudFoundryApplicationNameValidator());
