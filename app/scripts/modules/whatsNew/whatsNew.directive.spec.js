'use strict';

describe('Directives: whatsNew', function () {

  beforeEach(module('spinnaker.whatsNew.directive'));
  beforeEach(module('spinnaker.templates'));
  beforeEach(module('spinnaker.utils.timeFormatters'));

  beforeEach(inject(function ($rootScope, $compile, whatsNewReader, viewStateCache, $q, $filter, $modal) {
    this.scope = $rootScope.$new();
    this.compile = $compile;
    this.whatsNewReader = whatsNewReader;
    this.viewStateCache = viewStateCache;
    this.$filter = $filter;
    this.$q = $q;
    this.$modal = $modal;
  }));

  describe('with content', function() {

    beforeEach(function() {
      var lastUpdated = new Date().toString(),
        expectedDate = this.$filter('timestamp')(lastUpdated);
      spyOn(this.whatsNewReader, 'getWhatsNewContents').and.returnValue(this.$q.when({
        contents: 'stuff',
        lastUpdated: lastUpdated,
      }));

      this.lastUpdated = lastUpdated;
      this.expectedDate = expectedDate;
    });

    it('should show updated label when view state has not been cached', function() {
      var domNode = this.compile('<whats-new></whats-new>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('a.unread').size()).toBe(1);
      expect(domNode.find('a.unread').html().indexOf(this.expectedDate)).not.toBe(-1);
    });

    it('should show updated label when view state has different lastUpdated value than file', function() {
      this.viewStateCache.whatsNew = {
        get: function() {
          return {
            updateLastViewed: 'something else',
          };
        }
      };

      var domNode = this.compile('<whats-new></whats-new>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('a.unread').size()).toBe(1);
      expect(domNode.find('a.unread').html().indexOf(this.expectedDate)).not.toBe(-1);
    });

    it('should NOT show updated label when view state has same lastUpdated value as file', function() {
      var lastUpdated = this.lastUpdated;
      this.viewStateCache.whatsNew = {
        get: function() {
          return {
            updateLastViewed: lastUpdated,
          };
        }
      };

      var domNode = this.compile('<whats-new></whats-new>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('a.unread').size()).toBe(0);
    });

    it('should open modal when clicked, update and cache view state, then hide unread label', function() {
      var writtenToCache = null;
      this.viewStateCache.whatsNew = {
        get: function() {
          return {
            updateLastViewed: null,
          };
        },
        put: function(id, val) {
          writtenToCache = val;
        }
      };
      spyOn(this.$modal, 'open').and.returnValue({});

      var domNode = this.compile('<whats-new></whats-new>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('a.unread').size()).toBe(1);
      domNode.find('a').click();
      this.scope.$digest();

      expect(this.$modal.open).toHaveBeenCalled();
      expect(writtenToCache).not.toBeNull();
      expect(writtenToCache.updateLastViewed).toBe(this.lastUpdated);

      this.scope.$digest();
      expect(domNode.find('a.unread').size()).toBe(0);
    });
  });

  describe('no content', function() {
    it('should not render the <ul>', function() {
      spyOn(this.whatsNewReader, 'getWhatsNewContents').and.returnValue(this.$q.when(null));

      var domNode = this.compile('<whats-new></whats-new>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('ul').size()).toBe(0);
    });

  });

});
