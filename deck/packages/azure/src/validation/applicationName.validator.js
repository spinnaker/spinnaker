import { ApplicationNameValidator } from '@spinnaker/core';

export const azureApplicationNameValidator = {
  validate(name) {
    function validateSpecialCharacters(value, errors) {
      const pattern = /^([a-zA-Z][a-zA-Z0-9]*)?$/;
      if (!pattern.test(value)) {
        errors.push(
          'The application name must begin with a letter and must contain only letters or digits. No ' +
            'special characters are allowed.',
        );
      }
    }

    const warnings = [];
    const errors = [];

    if (name && name.length) {
      validateSpecialCharacters(name, errors);
    }

    return {
      warnings: warnings,
      errors: errors,
    };
  },
};

ApplicationNameValidator.registerValidator('azure', azureApplicationNameValidator);
