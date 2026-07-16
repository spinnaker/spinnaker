import { ReactInjector } from '@spinnaker/core';

import { getTitusServerGroupConfigurationService } from './serverGroupConfiguration.service';

describe('getTitusServerGroupConfigurationService', () => {
  it('does not resolve Angular-backed services while creating the service', () => {
    const cacheInitializer = spyOnProperty(ReactInjector, 'cacheInitializer', 'get').and.throwError(
      'cacheInitializer should not be resolved during construction',
    );
    const securityGroupReader = spyOnProperty(ReactInjector, 'securityGroupReader', 'get').and.throwError(
      'securityGroupReader should not be resolved during construction',
    );

    expect(() => getTitusServerGroupConfigurationService()).not.toThrow();
    expect(cacheInitializer).not.toHaveBeenCalled();
    expect(securityGroupReader).not.toHaveBeenCalled();
  });
});
