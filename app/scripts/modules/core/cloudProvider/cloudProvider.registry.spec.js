'use strict';

describe('cloudProviderRegistry: API', function() {
  var configurer;

  beforeEach(
    window.module(
      require('./cloudProvider.registry.js'),
      function(cloudProviderRegistryProvider) {
        configurer = cloudProviderRegistryProvider;
      }
    )
  );

  describe('registration', function() {
    it('registers providers', window.inject(function() {
      expect(configurer.$get().getProvider('aws')).toBeUndefined();
      var config = { key: 'a' };
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
      var nested = configurer.$get().getValue('aws', 'nested');
      expect(nested.good).toBe('nice');
      nested.good = 'mean';
      expect(configurer.$get().getValue('aws', 'nested').good).toBe('nice');
    });

    it('returns falsy values', window.inject(function() {
      configurer.registerProvider('aws', this.config);
      expect(configurer.$get().getValue('aws', 'nested.falsy')).toBe(false);
      expect(configurer.$get().getValue('aws', 'nested.nully')).toBe(null);
      expect(configurer.$get().getValue('aws', 'nested.zero')).toBe(0);
    }));

    it('returns null when provider or property is not found', window.inject(function() {
      configurer.registerProvider('aws', this.config);
      expect(configurer.$get().getValue('gce', 'a')).toBe(null);
      expect(configurer.$get().getValue('aws', 'b')).toBe(null);
      expect(configurer.$get().getValue('aws', 'a.b')).toBe(null);
    }));
  });


});
