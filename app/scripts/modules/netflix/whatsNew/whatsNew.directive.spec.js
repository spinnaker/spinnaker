'use strict';

describe('Directives: whatsNew', function () {

  require('./whatsNew.directive.html');

  beforeEach(
    window.module(
      require('./whatsNew.directive'),
      require('angular-ui-bootstrap')
    )
  );

  beforeEach(window.inject(function ($rootScope, $compile, whatsNewReader, viewStateCache, $q, $filter, $uibModal) {
    this.scope = $rootScope.$new();
    this.compile = $compile;
    this.whatsNewReader = whatsNewReader;
    this.viewStateCache = viewStateCache;
    this.$filter = $filter;
    this.$q = $q;
    this.$uibModal = $uibModal;
  }));

  function createWhatsNew(compile, scope) {
    var domNode;

    domNode = compile('<whats-new></whats-new>')(scope);
    scope.$digest();

    // ng-if creates a sibling if used on the root element in the directive
    // so grab the sibling with .next()
    return domNode.next();
  }

  function hasUnread(domNode) {
    return domNode.find('a.unread').size() === 1;
  }

  describe('with content', function() {

    beforeEach(function() {
      var lastUpdated = new Date().getTime();
      var expectedDate = this.$filter('timestamp')(lastUpdated);

      spyOn(this.whatsNewReader, 'getWhatsNewContents').and.returnValue(this.$q.when({
        contents: 'stuff',
        lastUpdated: lastUpdated,
      }));

      this.lastUpdated = lastUpdated;
      this.expectedDate = expectedDate;
    });

    it('should show updated label when view state has not been cached', function() {
      var domNode;

      domNode = createWhatsNew(this.compile, this.scope);

      expect(hasUnread(domNode)).toBe(true);
    });

    it('should show updated label when view state has different lastUpdated value than file', function() {
      var domNode;

      this.viewStateCache.whatsNew = {
        get: function() {
          return {
            updateLastViewed: 'something else',
          };
        }
      };

      domNode = createWhatsNew(this.compile, this.scope);

      expect(hasUnread(domNode)).toBe(true);
    });

    it('should NOT show updated label when view state has same lastUpdated value as file', function() {
      var lastUpdated, domNode;

      lastUpdated = this.lastUpdated;
      this.viewStateCache.whatsNew = {
        get: function() {
          return {
            updateLastViewed: lastUpdated,
          };
        }
      };

      domNode = createWhatsNew(this.compile, this.scope);

      expect(hasUnread(domNode)).toBe(false);
    });

    it('should open modal when clicked, update and cache view state, then hide timestamp label', function() {
      var writtenToCache, domNode;

      writtenToCache = null;
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
      spyOn(this.$uibModal, 'open').and.returnValue({});

      domNode = createWhatsNew(this.compile, this.scope);

      expect(hasUnread(domNode)).toBe(true);
      domNode.find('a').click();
      this.scope.$digest();

      expect(this.$uibModal.open).toHaveBeenCalled();
      expect(writtenToCache).not.toBeNull();
      expect(writtenToCache.updateLastViewed).toBe(this.lastUpdated);

      this.scope.$digest();
      expect(hasUnread(domNode)).toBe(false);
    });
  });

  describe('no content', function() {
    it('should not render the <ul>', function() {
      var domNode;

      spyOn(this.whatsNewReader, 'getWhatsNewContents').and.returnValue(this.$q.when(null));

      domNode = createWhatsNew(this.compile, this.scope);

      expect(domNode.find('ul').size()).toBe(0);
    });

  });

});
