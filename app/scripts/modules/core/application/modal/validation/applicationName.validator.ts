import {module} from 'angular';

export interface IApplicationNameValidationMessage {
  cloudProvider?: string;
  message: string;
}

export interface IValidationResult {
  warnings: string[];
  errors: string[];
}

export interface IApplicationNameValidationResult {
  warnings: IApplicationNameValidationMessage[];
  errors: IApplicationNameValidationMessage[];
}

export interface IApplicationNameValidator {
  validate: (applicationName: string) => IValidationResult;
}

/**
 * Responsible for registering validators around the application name, then running them
 * when creating a new application.
 */
export class ApplicationNameValidator {
  private providerMap: Map<string, IApplicationNameValidator[]> = new Map<string, IApplicationNameValidator[]>();

  static get $inject() { return ['cloudProviderRegistry']; }

  public constructor(private cloudProviderRegistry: any) {}

  /**
   * Registers a validator for a cloud provider.
   * @param cloudProvider the key of the cloud provider, e.g. "aws", "gce"
   * @param validator the actual validator
   */
  public registerValidator(cloudProvider: string, validator: IApplicationNameValidator) {
    if (this.cloudProviderRegistry.getProvider(cloudProvider)) {
      if (!this.providerMap.has(cloudProvider)) {
        this.providerMap.set(cloudProvider, []);
      }
      this.providerMap.get(cloudProvider).push(validator);
    }
  }

  /**
   * Performs the actual validation. If there are no providers supplied, all configured validators will fire
   * and add their messages to the result.
   * @param applicationName the name of the application
   * @param providersToTest the configured cloud providers; if empty, validators for all providers will fire
   * @returns {{errors: Array, warnings: Array}}
   */
  public validate(applicationName: string, providersToTest: string[]): IApplicationNameValidationResult {
    const toCheck = providersToTest && providersToTest.length ? providersToTest : this.cloudProviderRegistry.listRegisteredProviders();

    const errors: IApplicationNameValidationMessage[] = [],
          warnings: IApplicationNameValidationMessage[] = [];

    toCheck.forEach((provider: string) => {
      if (this.providerMap.has(provider)) {
        this.providerMap.get(provider).forEach(validator => {
          const results = validator.validate(applicationName);
          results.warnings.forEach(message => warnings.push({ cloudProvider: provider, message: message }));
          results.errors.forEach(message => errors.push({ cloudProvider: provider, message: message }));
        });
      }
    });

    return { errors, warnings };
  }

}

export const APPLICATION_NAME_VALIDATOR = 'spinnaker.core.application.name.validator';

module(APPLICATION_NAME_VALIDATOR, [
  require('core/cloudProvider/cloudProvider.registry'),
]).service('applicationNameValidator', ApplicationNameValidator);
