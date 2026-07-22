import { AccountService, ApplicationNameValidator, FirewallLabels } from '@spinnaker/core';

import { GoogleApplicationNameValidator } from './ApplicationNameValidator';
import '../gce.module';

describe('Google application name validator', () => {
  const validator = new GoogleApplicationNameValidator();

  it('requires a leading letter followed only by letters or digits', () => {
    expect(validator.validate('valid123')).toEqual({ errors: [], warnings: [] });
    expect(validator.validate('1invalid-name').errors).toEqual([
      'The application name must begin with a letter and must contain only letters or digits. No special characters are allowed.',
    ]);
  });

  it('rejects application names longer than the Google resource maximum', () => {
    expect(validator.validate('a'.repeat(64)).errors).toContain(
      'The maximum length for an application in Google is 63 characters.',
    );
  });

  it('warns as load-balancer, server-group, and firewall name space is exhausted', () => {
    const loadBalancerWarnings = validator.validate('a'.repeat(35)).warnings.join(' ');
    const serverGroupWarnings = validator.validate('a'.repeat(47)).warnings.join(' ');
    const firewallWarnings = validator.validate('a'.repeat(61)).warnings.join(' ');

    expect(loadBalancerWarnings).toContain(
      'If you plan to include a stack or detail field for Google load balancers, you will only',
    );
    expect(serverGroupWarnings).toContain('Google server groups');
    expect(firewallWarnings).toContain(`Google ${FirewallLabels.get('firewalls')}`);
  });

  it('is registered directly when the Google module is imported', async () => {
    spyOn(AccountService, 'listProviders').and.returnValue(Promise.resolve(['gce']));

    const result = await ApplicationNameValidator.validate('invalid-name', ['gce']);

    expect(result.errors).toEqual([
      {
        cloudProvider: 'gce',
        message:
          'The application name must begin with a letter and must contain only letters or digits. No special characters are allowed.',
      },
    ]);
  });
});
