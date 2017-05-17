import {mock} from 'angular';

import {CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry} from './cloudProvider.registry';

describe('cloudProviderRegistry: API', function() {
  let configurer: CloudProviderRegistry;

  beforeEach(
    mock.module(
      CLOUD_PROVIDER_REGISTRY,
      function(cloudProviderRegistryProvider: CloudProviderRegistry) {
        configurer = cloudProviderRegistryProvider;
      }
    )
  );

  describe('registration', function() {
    it('registers providers', mock.inject(function() {
      expect(configurer.$get().getProvider('aws')).toBeNull();
      const config = { name: 'a', key: 'a' };
      configurer.registerProvider('aws', config);
      expect(configurer.$get().getProvider('aws')).toEqual(config);
    }));
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
        }
      };
    });

    it('returns simple or nested properties', function() {
      configurer.registerProvider('aws', this.config);
      expect(configurer.$get().getValue('aws', 'key')).toEqual('a');
      expect(configurer.$get().getValue('aws', 'nested')).toEqual(this.config.nested);
      expect(configurer.$get().getValue('aws', 'nested.good')).toEqual('nice');
    });

    it('returns a copy of properties, not actual registered values', function() {
      configurer.registerProvider('aws', this.config);

      expect(configurer.$get().getValue('aws', 'nested')).not.toBe(this.config.nested);
      expect(configurer.$get().getValue('aws', 'nested')).toEqual(this.config.nested);

      // the above tests should be sufficient, but just to really drive home the point
      const nested = configurer.$get().getValue('aws', 'nested');
      expect(nested.good).toBe('nice');
      nested.good = 'mean';
      expect(configurer.$get().getValue('aws', 'nested').good).toBe('nice');
    });

    it('returns falsy values', mock.inject(function() {
      configurer.registerProvider('aws', this.config);
      expect(configurer.$get().getValue('aws', 'nested.falsy')).toBe(false);
      expect(configurer.$get().getValue('aws', 'nested.nully')).toBe(null);
      expect(configurer.$get().getValue('aws', 'nested.zero')).toBe(0);
    }));

    it('returns null when provider or property is not found', mock.inject(function() {
      configurer.registerProvider('aws', this.config);
      expect(configurer.$get().getValue('gce', 'a')).toBe(null);
      expect(configurer.$get().getValue('aws', 'b')).toBe(null);
      expect(configurer.$get().getValue('aws', 'a.b')).toBe(null);
    }));
  });

  describe('hasValue', function () {
    beforeEach(function() {
      this.config = {
        key: 'a',
        nested: {
          good: 'nice',
          falsy: false,
          nully: null,
          zero: 0,
        }
      };
    });

    it('returns true on simple or nested properties', function() {
      configurer.registerProvider('aws', this.config);
      expect(configurer.$get().hasValue('aws', 'key')).toBe(true);
      expect(configurer.$get().hasValue('aws', 'nested')).toBe(true);
      expect(configurer.$get().hasValue('aws', 'nested.good')).toBe(true);
      expect(configurer.$get().hasValue('aws', 'nested.falsy')).toBe(true);
      expect(configurer.$get().hasValue('aws', 'nested.zero')).toBe(true);
    });

    it('returns false on null properties, non-existent properties or non-existent providers', function () {
      configurer.registerProvider('aws', this.config);
      expect(configurer.$get().hasValue('aws', 'nested.nully')).toBe(false);
      expect(configurer.$get().hasValue('aws', 'nonexistent')).toBe(false);
      expect(configurer.$get().hasValue('aws', 'definitely.nonexistent')).toBe(false);
      expect(configurer.$get().hasValue('boo', 'bar')).toBe(false);
      expect(configurer.$get().hasValue('boo', 'bar.baz')).toBe(false);
    });
  });


});
