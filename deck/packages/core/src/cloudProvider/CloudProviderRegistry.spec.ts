import { mock } from 'angular';

import { CloudProviderRegistry } from './CloudProviderRegistry';
import { SETTINGS } from '../config/settings';

describe('CloudProviderRegistry: API', function () {
  const registrationTestProvider = 'cloudProviderRegistryRegistrationTest';
  const lookupTestProvider = 'cloudProviderRegistryLookupTest';

  beforeEach(function () {
    SETTINGS.providers[registrationTestProvider] = {};
    SETTINGS.providers[lookupTestProvider] = {};
  });

  afterEach(function () {
    delete SETTINGS.providers[registrationTestProvider];
    delete SETTINGS.providers[lookupTestProvider];
    (CloudProviderRegistry as any).providers.delete(registrationTestProvider);
    (CloudProviderRegistry as any).providers.delete(lookupTestProvider);
  });

  describe('registration', function () {
    it(
      'registers providers',
      mock.inject(function () {
        expect(CloudProviderRegistry.getProvider(registrationTestProvider)).toBeNull();
        const config = { name: 'a', key: 'a' };
        CloudProviderRegistry.registerProvider(registrationTestProvider, config);
        expect(CloudProviderRegistry.getProvider(registrationTestProvider)).toEqual(config);
      }),
    );
  });

  describe('property lookup', function () {
    beforeEach(function () {
      this.config = {
        key: 'a',
        nested: {
          good: 'nice',
          falsy: false,
          nully: null,
          zero: 0,
        },
      };
    });

    it('returns simple or nested properties', function () {
      CloudProviderRegistry.registerProvider(lookupTestProvider, this.config);
      expect(CloudProviderRegistry.getValue(lookupTestProvider, 'key')).toEqual('a');
      expect(CloudProviderRegistry.getValue(lookupTestProvider, 'nested')).toEqual(this.config.nested);
      expect(CloudProviderRegistry.getValue(lookupTestProvider, 'nested.good')).toEqual('nice');
    });

    it('returns a copy of properties, not actual registered values', function () {
      CloudProviderRegistry.registerProvider(lookupTestProvider, this.config);

      expect(CloudProviderRegistry.getValue(lookupTestProvider, 'nested')).not.toBe(this.config.nested);
      expect(CloudProviderRegistry.getValue(lookupTestProvider, 'nested')).toEqual(this.config.nested);

      // the above tests should be sufficient, but just to really drive home the point
      const nested = CloudProviderRegistry.getValue(lookupTestProvider, 'nested');
      expect(nested.good).toBe('nice');
      nested.good = 'mean';
      expect(CloudProviderRegistry.getValue(lookupTestProvider, 'nested').good).toBe('nice');
    });

    it(
      'returns falsy values',
      mock.inject(function () {
        CloudProviderRegistry.registerProvider(lookupTestProvider, this.config);
        expect(CloudProviderRegistry.getValue(lookupTestProvider, 'nested.falsy')).toBe(false);
        expect(CloudProviderRegistry.getValue(lookupTestProvider, 'nested.nully')).toBe(null);
        expect(CloudProviderRegistry.getValue(lookupTestProvider, 'nested.zero')).toBe(0);
      }),
    );

    it(
      'returns null when provider or property is not found',
      mock.inject(function () {
        CloudProviderRegistry.registerProvider(lookupTestProvider, this.config);
        expect(CloudProviderRegistry.getValue('gce', 'a')).toBe(null);
        expect(CloudProviderRegistry.getValue(lookupTestProvider, 'b')).toBe(null);
        expect(CloudProviderRegistry.getValue(lookupTestProvider, 'a.b')).toBe(null);
      }),
    );
  });

  describe('hasValue', function () {
    beforeEach(function () {
      this.config = {
        key: 'a',
        nested: {
          good: 'nice',
          falsy: false,
          nully: null,
          zero: 0,
        },
      };
    });

    it('returns true on simple or nested properties', function () {
      CloudProviderRegistry.registerProvider(lookupTestProvider, this.config);
      expect(CloudProviderRegistry.hasValue(lookupTestProvider, 'key')).toBe(true);
      expect(CloudProviderRegistry.hasValue(lookupTestProvider, 'nested')).toBe(true);
      expect(CloudProviderRegistry.hasValue(lookupTestProvider, 'nested.good')).toBe(true);
      expect(CloudProviderRegistry.hasValue(lookupTestProvider, 'nested.falsy')).toBe(true);
      expect(CloudProviderRegistry.hasValue(lookupTestProvider, 'nested.zero')).toBe(true);
    });

    it('returns false on null properties, non-existent properties or non-existent providers', function () {
      CloudProviderRegistry.registerProvider(lookupTestProvider, this.config);
      expect(CloudProviderRegistry.hasValue(lookupTestProvider, 'nested.nully')).toBe(false);
      expect(CloudProviderRegistry.hasValue(lookupTestProvider, 'nonexistent')).toBe(false);
      expect(CloudProviderRegistry.hasValue(lookupTestProvider, 'definitely.nonexistent')).toBe(false);
      expect(CloudProviderRegistry.hasValue('boo', 'bar')).toBe(false);
      expect(CloudProviderRegistry.hasValue('boo', 'bar.baz')).toBe(false);
    });
  });
});
