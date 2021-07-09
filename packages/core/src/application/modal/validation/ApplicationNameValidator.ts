import { AccountService } from '../../../account/AccountService';

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
  private static providerMap: Map<string, IApplicationNameValidator[]> = new Map<string, IApplicationNameValidator[]>();

  /**
   * Registers a validator for a cloud provider.
   * @param cloudProvider the key of the cloud provider, e.g. "aws", "gce"
   * @param validator the actual validator
   */
  public static registerValidator(cloudProvider: string, validator: IApplicationNameValidator) {
    if (!this.providerMap.has(cloudProvider)) {
      this.providerMap.set(cloudProvider, []);
    }
    this.providerMap.get(cloudProvider).push(validator);
  }

  /**
   * Overwrites the validators of a cloud provider with the given array.
   * @param cloudProvider the key of the cloud provider, e.g. "aws", "gce"
   * @param validators the validators to use for the provider
   */
  public static setValidators(cloudProvider: string, validators: IApplicationNameValidator[]) {
    this.providerMap.set(cloudProvider, validators);
  }

  /**
   * Performs the actual validation. If there are no providers supplied, all configured validators will fire
   * and add their messages to the result.
   * @param applicationName the name of the application
   * @param providersToTest the configured cloud providers; if empty, validators for all providers will fire
   * @returns {{errors: Array, warnings: Array}}
   */
  public static validate(
    applicationName: string,
    providersToTest: string[],
  ): PromiseLike<IApplicationNameValidationResult> {
    return AccountService.listProviders().then((availableProviders: string[]) => {
      const toCheck = providersToTest && providersToTest.length ? providersToTest : availableProviders;

      const errors: IApplicationNameValidationMessage[] = [];
      const warnings: IApplicationNameValidationMessage[] = [];
      toCheck.forEach((provider: string) => {
        if (this.providerMap.has(provider)) {
          this.providerMap.get(provider).forEach((validator) => {
            const results = validator.validate(applicationName);
            results.warnings.forEach((message) => warnings.push({ cloudProvider: provider, message }));
            results.errors.forEach((message) => errors.push({ cloudProvider: provider, message }));
          });
        }
      });
      return { errors, warnings };
    });
  }
}
