import {module} from 'angular';
import {
  APPLICATION_NAME_VALIDATOR, IApplicationNameValidator,
  ApplicationNameValidator, IValidationResult
} from './applicationName.validator';
import {CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry} from 'core/cloudProvider/cloudProvider.registry';

export class ExampleApplicationNameValidator implements IApplicationNameValidator {

  public get WARNING_MESSAGE() { return 'WARNING!!!!'; }
  public get WARNING_CONDITION() { return 'application.warning'; }
  public get ERROR_MESSAGE() { return 'ERRORRRRRR!!!'; }
  public get ERROR_CONDITION() { return  'application.error'; }
  public get COMMON_WARNING_CONDITION() { return  'common.warning'; }
  public get COMMON_WARNING_MESSAGE() { return  '2COMMON WARNING'; }
  public get COMMON_ERROR_CONDITION() { return  'common.error'; }
  public get COMMON_ERROR_MESSAGE() { return  'COMMON ERROR!'; }
  public get provider() { return 'example'; }

  public validate(name = ''): IValidationResult {
    let warnings: string[] = [],
        errors: string[] = [];
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
      warnings: warnings,
      errors: errors,
    };
  }
}

export class ExampleApplicationNameValidator2 implements IApplicationNameValidator {
  public get WARNING_MESSAGE() { return '2WARNING!!!!'; }
  public get WARNING_CONDITION() { return 'application.warning2'; }
  public get ERROR_MESSAGE() { return '2ERRORRRRRR!!!'; }
  public get ERROR_CONDITION() { return  'application.error™'; }
  public get COMMON_WARNING_CONDITION() { return  'common.warning'; }
  public get COMMON_WARNING_MESSAGE() { return  '2COMMON WARNING'; }
  public get COMMON_ERROR_CONDITION() { return  'common.error'; }
  public get COMMON_ERROR_MESSAGE() { return  '2COMMON ERROR!'; }
  public get provider() { return 'example2'; }

  public validate(name = ''): IValidationResult {
    let warnings: string[] = [],
        errors: string[] = [];
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
      warnings: warnings,
      errors: errors,
    };
  }
}

export const EXAMPLE_APPLICATION_NAME_VALIDATOR = 'spinnaker.core.application.modal.validation.example.applicationName';

module(EXAMPLE_APPLICATION_NAME_VALIDATOR, [
  APPLICATION_NAME_VALIDATOR,
  CLOUD_PROVIDER_REGISTRY,
  require('core/config/settings.js'),
]).service('exampleApplicationNameValidator', ExampleApplicationNameValidator)
  .service('exampleApplicationNameValidator2', ExampleApplicationNameValidator2)
  .run((applicationNameValidator: ApplicationNameValidator,
        exampleApplicationNameValidator: ExampleApplicationNameValidator,
        exampleApplicationNameValidator2: ExampleApplicationNameValidator2) => {
    applicationNameValidator.registerValidator('example', exampleApplicationNameValidator);
    applicationNameValidator.registerValidator('example2', exampleApplicationNameValidator2);
  })
  .config((cloudProviderRegistryProvider: CloudProviderRegistry, settings: any) => {
    settings.providers.example = {};
    settings.providers.example2 = {};
    cloudProviderRegistryProvider.registerProvider('example', {name: 'example'});
    cloudProviderRegistryProvider.registerProvider('example2', {name: 'example2'});
  });
