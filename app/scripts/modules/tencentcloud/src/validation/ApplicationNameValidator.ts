import { ApplicationNameValidator, FirewallLabels, IApplicationNameValidator } from '@spinnaker/core';

class TencentcloudApplicationNameValidator implements IApplicationNameValidator {
  private validateSpecialCharacters(name: string, errors: string[]): void {
    const pattern = /^[a-zA-Z_0-9.]*$/g;
    if (!pattern.test(name)) {
      errors.push('Only dot(.) and underscore(_) special characters are allowed.');
    }
  }

  private validateLoadBalancerCharacters(name: string, warnings: string[]) {
    if (name.includes('.') || name.includes('_')) {
      warnings.push(`If the application's name contains an underscore(_) or dot(.),
            you will not be able to create a load balancer,
            preventing it from being used as a front end service.`);
    }
  }

  private validateLength(name: string, warnings: string[], errors: string[]) {
    if (name.length > 250) {
      errors.push('The maximum length for an application in Tencentcloud is 250 characters.');
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
    if (name.length > 20) {
      if (name.length > 32) {
        warnings.push(`You will not be able to create an Tencentcloud load balancer for this application if the
            application's name is longer than 32 characters (currently: ${name.length} characters)`);
      } else {
        if (name.length >= 30) {
          warnings.push(`If you plan to create load balancers for this application, be aware that the character limit
              for load balancer names is 32 (currently: ${name.length} characters). With separators ("-"), you will not
              be able to add a stack and detail field to any load balancer.`);
        } else {
          const remaining = 30 - name.length;
          warnings.push(`If you plan to create load balancers for this application, be aware that the character limit
              for load balancer names is 32. You will only have ~${remaining} characters to add a stack or detail
              field to any load balancer.`);
        }
      }
    }
  }

  public validate(name = '') {
    const warnings: string[] = [];
    const errors: string[] = [];

    if (name.length) {
      this.validateSpecialCharacters(name, errors);
      this.validateLoadBalancerCharacters(name, warnings);
      this.validateLength(name, warnings, errors);
    }

    return {
      warnings,
      errors,
    };
  }
}
ApplicationNameValidator.registerValidator('tencentcloud', new TencentcloudApplicationNameValidator());
