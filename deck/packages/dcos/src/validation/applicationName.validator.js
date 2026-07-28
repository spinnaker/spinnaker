import { ApplicationNameValidator } from '@spinnaker/core';

function validateSpecialCharacters(name, errors) {
  const pattern = /^[a-z0-9]+$/;
  if (!pattern.test(name)) {
    errors.push(
      'The application name can only contain lowercase letters and digits. No other ' +
        'special characters are allowed.',
    );
  }
}

function validateLength(name, warnings, errors) {
  const maxResourceNameLength = 127;

  if (name.length > maxResourceNameLength) {
    errors.push('The maximum length for an application in DCOS is 127 characters.');
  }
}

export const dcosApplicationNameValidator = {
  validate(name) {
    const warnings = [];
    const errors = [];

    if (name && name.length) {
      validateSpecialCharacters(name, errors);
      validateLength(name, warnings, errors);
    }

    return { warnings, errors };
  },
};

ApplicationNameValidator.registerValidator('dcos', dcosApplicationNameValidator);
