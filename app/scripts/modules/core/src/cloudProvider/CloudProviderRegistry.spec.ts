import { mock } from 'angular';

import { CloudProviderRegistry } from './CloudProviderRegistry';

describe('CloudProviderRegistry: API', function() {
  describe('registration', function() {
    it(
      'registers providers',
      mock.inject(function() {
        expect(CloudProviderRegistry.getProvider('aws')).toBeNull();
        const config = { name: 'a', key: 'a' };
        CloudProviderRegistry.registerProvider('aws', config);
        expect(CloudProviderRegistry.getProvider('aws')).toEqual(config);
      }),
    );
  });

  describe('property lookup', function() {
    beforeEach(function() {
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

    it('returns simple or nested properties', function() {
      CloudProviderRegistry.registerProvider('aws', this.config);
      expect(CloudProviderRegistry.getValue('aws', 'key')).toEqual('a');
      expect(CloudProviderRegistry.getValue('aws', 'nested')).toEqual(this.config.nested);
      expect(CloudProviderRegistry.getValue('aws', 'nested.good')).toEqual('nice');
    });

    it('returns a copy of properties, not actual registered values', function() {
      CloudProviderRegistry.registerProvider('aws', this.config);

      expect(CloudProviderRegistry.getValue('aws', 'nested')).not.toBe(this.config.nested);
      expect(CloudProviderRegistry.getValue('aws', 'nested')).toEqual(this.config.nested);

      // the above tests should be sufficient, but just to really drive home the point
      const nested = CloudProviderRegistry.getValue('aws', 'nested');
      expect(nested.good).toBe('nice');
      nested.good = 'mean';
      expect(CloudProviderRegistry.getValue('aws', 'nested').good).toBe('nice');
    });

    it(
      'returns falsy values',
      mock.inject(function() {
        CloudProviderRegistry.registerProvider('aws', this.config);
        expect(CloudProviderRegistry.getValue('aws', 'nested.falsy')).toBe(false);
        expect(CloudProviderRegistry.getValue('aws', 'nested.nully')).toBe(null);
        expect(CloudProviderRegistry.getValue('aws', 'nested.zero')).toBe(0);
      }),
    );

    it(
      'returns null when provider or property is not found',
      mock.inject(function() {
        CloudProviderRegistry.registerProvider('aws', this.config);
        expect(CloudProviderRegistry.getValue('gce', 'a')).toBe(null);
        expect(CloudProviderRegistry.getValue('aws', 'b')).toBe(null);
        expect(CloudProviderRegistry.getValue('aws', 'a.b')).toBe(null);
      }),
    );
  });

  describe('hasValue', function() {
    beforeEach(function() {
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

    it('returns true on simple or nested properties', function() {
      CloudProviderRegistry.registerProvider('aws', this.config);
      expect(CloudProviderRegistry.hasValue('aws', 'key')).toBe(true);
      expect(CloudProviderRegistry.hasValue('aws', 'nested')).toBe(true);
      expect(CloudProviderRegistry.hasValue('aws', 'nested.good')).toBe(true);
      expect(CloudProviderRegistry.hasValue('aws', 'nested.falsy')).toBe(true);
      expect(CloudProviderRegistry.hasValue('aws', 'nested.zero')).toBe(true);
    });

    it('returns false on null properties, non-existent properties or non-existent providers', function() {
      CloudProviderRegistry.registerProvider('aws', this.config);
      expect(CloudProviderRegistry.hasValue('aws', 'nested.nully')).toBe(false);
      expect(CloudProviderRegistry.hasValue('aws', 'nonexistent')).toBe(false);
      expect(CloudProviderRegistry.hasValue('aws', 'definitely.nonexistent')).toBe(false);
      expect(CloudProviderRegistry.hasValue('boo', 'bar')).toBe(false);
      expect(CloudProviderRegistry.hasValue('boo', 'bar.baz')).toBe(false);
    });
  });

  describe('skinned provider configs', () => {
    it('returns a value from a skinned provider config when skin is specified', () => {
      CloudProviderRegistry.registerProvider('kubernetes', { name: 'kubernetes', skin: 'v1' });
      CloudProviderRegistry.registerProvider('kubernetes', { name: 'kubernetes', skin: 'v2' });

      expect(CloudProviderRegistry.getValue('kubernetes', 'skin', 'v1')).toBe('v1');
      expect(CloudProviderRegistry.getValue('kubernetes', 'skin', 'v2')).toBe('v2');
    });

    it('returns a value from the default skin if skin is not specified', () => {
      CloudProviderRegistry.registerProvider('kubernetes', { name: 'kubernetes', skin: 'v1' });
      CloudProviderRegistry.registerProvider('kubernetes', { name: 'kubernetes', skin: 'v2', defaultSkin: true });

      expect(CloudProviderRegistry.getValue('kubernetes', 'skin')).toBe('v2');
    });

    // This behavior is implicitly tested in other tests, but demonstrates that the provider
    // configs do not need to add a `defaultSkin` flag if there is only one config for that provider.
    it('behaves reasonably if a provider does not define a default config version', () => {
      CloudProviderRegistry.registerProvider('gce', { name: 'gce', key: 'value' });

      expect(CloudProviderRegistry.getValue('gce', 'key')).toBe('value');
    });
  });
});
