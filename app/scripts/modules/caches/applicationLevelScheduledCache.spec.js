'use strict';


describe('applicationLevelScheduledCache', function() {

  beforeEach(window.module(
    require('./applicationLevelScheduledCache.js')
  ));

  beforeEach(window.inject(function(applicationLevelScheduledCache, $rootScope, $stateParams) {
    this.applicationLevelScheduledCache = applicationLevelScheduledCache;
    this.$rootScope = $rootScope;
    this.$stateParams = $stateParams;
    this.currentScope = $rootScope.$new();
  }));

  describe('watching for changes in $stateParams.application', function() {
    beforeEach(function() {
      this.$stateParams.application = 'foo';
      this.currentScope.$emit('$stateChangeStart');
      expect(this.$stateParams.application).toEqual('foo');
      this.applicationLevelScheduledCache.put('foo', 'bar');
    });

    it('should remove all scoped keys from the cache on application change', function() {
      expect(this.applicationLevelScheduledCache.get('foo')).toEqual('bar');
      expect(this.applicationLevelScheduledCache.toRemove()).toContain('foo');
      this.$stateParams.application = 'bar';
      expect(this.$stateParams.application).toBe('bar');
      this.currentScope.$emit('$stateChangeStart');
      expect(this.applicationLevelScheduledCache.toRemove()).not.toContain('foo');
      expect(this.applicationLevelScheduledCache.get('foo')).toBeUndefined();
    });

    it('should not remove scoped keys when the application does not change', function() {
      this.currentScope.$emit('$stateChangeStart');
      expect(this.applicationLevelScheduledCache.toRemove()).toContain('foo');
      expect(this.applicationLevelScheduledCache.get('foo')).toEqual('bar');
    });
  });
});
