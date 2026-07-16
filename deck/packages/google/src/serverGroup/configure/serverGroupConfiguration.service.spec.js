'use strict';

import { GCE_DISTRIBUTION_POLICY_TARGET_SHAPES } from './serverGroupConfiguration.service';

describe('gceServerGroupConfigurationService target shapes', function () {
  it('exposes BALANCED and ANY_SINGLE_ZONE alongside ANY and EVEN', function () {
    expect(GCE_DISTRIBUTION_POLICY_TARGET_SHAPES).toEqual(['ANY', 'EVEN', 'BALANCED', 'ANY_SINGLE_ZONE']);
  });
});
