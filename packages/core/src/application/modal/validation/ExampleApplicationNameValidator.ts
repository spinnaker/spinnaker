import { ApplicationNameValidator, IApplicationNameValidator, IValidationResult } from './ApplicationNameValidator';
import { CloudProviderRegistry } from '../../../cloudProvider';
import { SETTINGS } from '../../../config/settings';

export class ExampleApplicationNameValidator implements IApplicationNameValidator {
  public get WARNING_MESSAGE() {
    return 'WARNING!!!!';
  }
  public get WARNING_CONDITION() {
    return 'application.warning';
  }
  public get ERROR_MESSAGE() {
    return 'ERRORRRRRR!!!';
  }
  public get ERROR_CONDITION() {
    return 'application.error';
  }
  public get COMMON_WARNING_CONDITION() {
    return 'common.warning';
  }
  public get COMMON_WARNING_MESSAGE() {
    return '2COMMON WARNING';
  }
  public get COMMON_ERROR_CONDITION() {
    return 'common.error';
  }
  public get COMMON_ERROR_MESSAGE() {
    return 'COMMON ERROR!';
  }
  public get provider() {
    return 'example';
  }

  public validate(name = ''): IValidationResult {
    const warnings: string[] = [];
    const errors: string[] = [];
    name = name || '';
    if (name === this.WARNING_CONDITION) {
      warnings.push(this.WARNING_MESSAGE);
    }
    if (name === this.ERROR_CONDITION) {
      errors.push(this.ERROR_MESSAGE);
    }
    if (name === this.COMMON_WARNING_CONDITION) {
      warnings.push(this.COMMON_WARNING_MESSAGE);
    }
    if (name === this.COMMON_ERROR_CONDITION) {
      errors.push(this.COMMON_ERROR_MESSAGE);
    }

    return {
      warnings,
      errors,
    };
  }
}

export class ExampleApplicationNameValidator2 implements IApplicationNameValidator {
  public get WARNING_MESSAGE() {
    return '2WARNING!!!!';
  }
  public get WARNING_CONDITION() {
    return 'application.warning2';
  }
  public get ERROR_MESSAGE() {
    return '2ERRORRRRRR!!!';
  }
  public get ERROR_CONDITION() {
    return 'application.error™';
  }
  public get COMMON_WARNING_CONDITION() {
    return 'common.warning';
  }
  public get COMMON_WARNING_MESSAGE() {
    return '2COMMON WARNING';
  }
  public get COMMON_ERROR_CONDITION() {
    return 'common.error';
  }
  public get COMMON_ERROR_MESSAGE() {
    return '2COMMON ERROR!';
  }
  public get provider() {
    return 'example2';
  }

  public validate(name = ''): IValidationResult {
    const warnings: string[] = [];
    const errors: string[] = [];
    name = name || '';
    if (name === this.WARNING_CONDITION) {
      warnings.push(this.WARNING_MESSAGE);
    }
    if (name === this.ERROR_CONDITION) {
      errors.push(this.ERROR_MESSAGE);
    }
    if (name === this.COMMON_WARNING_CONDITION) {
      warnings.push(this.COMMON_WARNING_MESSAGE);
    }
    if (name === this.COMMON_ERROR_CONDITION) {
      errors.push(this.COMMON_ERROR_MESSAGE);
    }

    return {
      warnings,
      errors,
    };
  }
}

ApplicationNameValidator.registerValidator('example', new ExampleApplicationNameValidator());
ApplicationNameValidator.registerValidator('example2', new ExampleApplicationNameValidator2());

CloudProviderRegistry.registerProvider('example', { name: 'example' });
CloudProviderRegistry.registerProvider('example2', { name: 'example2' });

SETTINGS.providers.example = { defaults: { account: 'test' }, resetToOriginal: () => {} };
SETTINGS.providers.example2 = { defaults: { account: 'test' }, resetToOriginal: () => {} };
