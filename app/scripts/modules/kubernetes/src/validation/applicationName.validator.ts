import { ApplicationNameValidator } from '@spinnaker/core';

export class KubernetesApplicationNameValidator {
  // general k8s resource restriction: 253 characters - https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#names
  // safe bet is 63 characters - (see also RFC 1035 or RFC 1123 - both setting DNS label maximum to 63 chars)
  // for service names: - https://github.com/kubernetes/kubernetes/pull/29523 (until K8s 1.4.0 it was 24 characters https://github.com/kubernetes/kubernetes/issues/12463)
  // or annotations:    - https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/#syntax-and-character-set
  private static MAX_RESOURCE_NAME_LENGTH = 63;

  private static validateSpecialCharacters(name: string, errors: string[]): void {
    const alphanumWithDashPattern = /^([a-zA-Z][a-zA-Z0-9-]*)?$/;
    if (!alphanumWithDashPattern.test(name)) {
      errors.push(
        'The application name must begin with a letter and must contain only letters, digits, or dashes. ' +
          'No special characters are allowed.',
      );
    }
  }

  private static validateLength(name: string, errors: string[]): void {
    if (name.length > KubernetesApplicationNameValidator.MAX_RESOURCE_NAME_LENGTH) {
      errors.push(
        `The maximum length for an application in Kubernetes is ${KubernetesApplicationNameValidator.MAX_RESOURCE_NAME_LENGTH} characters.`,
      );
    }
  }

  public validate(name = '') {
    const warnings: string[] = [];
    const errors: string[] = [];

    if (name && name.length) {
      KubernetesApplicationNameValidator.validateSpecialCharacters(name, errors);
      KubernetesApplicationNameValidator.validateLength(name, errors);
    }

    return {
      warnings,
      errors,
    };
  }
}

ApplicationNameValidator.registerValidator('kubernetes', new KubernetesApplicationNameValidator());
