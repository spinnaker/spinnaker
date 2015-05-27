'use strict';

describe('Service: pageTitleService', function() {

  beforeEach(module('spinnaker.pageTitle'));

  beforeEach(inject(function (pageTitleService, $stateParams, $rootScope) {
    this.pageTitleService = pageTitleService;
    this.$stateParams = $stateParams;
    this.$rootScope = $rootScope;
  }));

  describe('handleRoutingStart', function() {
    it('sets page title, routing flag on root scope', function() {
      var scope = this.$rootScope;

      expect(scope.routing).toBeFalsy();
      expect(scope.pageTitle).toBeUndefined();

      this.pageTitleService.handleRoutingStart();
      expect(scope.routing).toBe(true);
      expect(scope.pageTitle).toBe('Spinnaker: Loading...');
    });
  });

  describe('handleRoutingError', function() {
    it('sets page title, clears routing flag on root scope', function() {
      var scope = this.$rootScope;

      this.pageTitleService.handleRoutingStart();
      expect(scope.routing).toBe(true);
      expect(scope.pageTitle).toBe('Spinnaker: Loading...');

      this.pageTitleService.handleRoutingError();
      expect(scope.routing).toBe(false);
      expect(scope.pageTitle).toBe('Spinnaker: Error');

    });
  });

  describe('handleRoutingSuccess', function() {

    afterEach(function() {
      expect(this.$rootScope.routing).toBe(false);
    });

    it('falls back to "Spinnaker" when nothing configured', function() {
      this.pageTitleService.handleRoutingSuccess();
      expect(this.$rootScope.pageTitle).toBe('Spinnaker');
    });

    it('sets page title when only main is configured with a label', function() {
      this.pageTitleService.handleRoutingSuccess({pageTitleMain: {label: 'expected result'}});
      expect(this.$rootScope.pageTitle).toBe('expected result');
    });

    it('sets page title from state params when main is configured with a field', function() {
      this.$stateParams.someParam = 'expected result from field';
      this.pageTitleService.handleRoutingSuccess({pageTitleMain: {field: 'someParam'}});
      expect(this.$rootScope.pageTitle).toBe('expected result from field');
    });

    it('ignores field value when label provided in main configuration', function() {
      this.$stateParams.someParam = 'expected result from field';
      this.pageTitleService.handleRoutingSuccess({pageTitleMain: {field: 'someParam', label: 'Overruled!'}});
      expect(this.$rootScope.pageTitle).toBe('Overruled!');
    });

    it('handles section config - title only', function() {
      this.pageTitleService.handleRoutingSuccess({pageTitleSection: {title: 'The Section'}});
      expect(this.$rootScope.pageTitle).toBe('Spinnaker · The Section');
    });

    it('appends name if provided in section config', function() {
      this.$stateParams.sectionNameParam = 'Some thing';
      this.pageTitleService.handleRoutingSuccess({pageTitleSection: {title: 'The Section', nameParam: 'sectionNameParam'}});
      expect(this.$rootScope.pageTitle).toBe('Spinnaker · The Section: Some thing');
    });

    it('appends account and region in section config, separated by colon if both provided', function() {
      this.$stateParams.sectionNameParam = 'Some thing';
      this.$stateParams.sectionAccountParam = 'test';
      this.$stateParams.sectionRegionParam = 'us-east-1';

      this.pageTitleService.handleRoutingSuccess({pageTitleSection: {
        title: 'The Section',
        nameParam: 'sectionNameParam',
        accountParam: 'sectionAccountParam'
      }});
      expect(this.$rootScope.pageTitle).toBe('Spinnaker · The Section: Some thing (test)');

      this.pageTitleService.handleRoutingSuccess({pageTitleSection: {
        title: 'The Section',
        nameParam: 'sectionNameParam',
        regionParam: 'sectionRegionParam'
      }});
      expect(this.$rootScope.pageTitle).toBe('Spinnaker · The Section: Some thing (us-east-1)');

      this.pageTitleService.handleRoutingSuccess({pageTitleSection: {
        title: 'The Section',
        nameParam: 'sectionNameParam',
        accountParam: 'sectionAccountParam',
        regionParam: 'sectionRegionParam'
      }});
      expect(this.$rootScope.pageTitle).toBe('Spinnaker · The Section: Some thing (test:us-east-1)');
    });

    it('handles details config the same way it handles section config', function() {
      this.$stateParams.sectionNameParam = 'Some thing';
      this.$stateParams.sectionAccountParam = 'test';
      this.$stateParams.sectionRegionParam = 'us-east-1';
      this.$stateParams.detailsNameParam = 'Some specific thing';
      this.$stateParams.detailsAccountParam = 'prod';
      this.$stateParams.detailsRegionParam = 'us-east-1';

      this.pageTitleService.handleRoutingSuccess({
        pageTitleSection: {
          title: 'The Section',
          nameParam: 'sectionNameParam',
          accountParam: 'sectionAccountParam'
        },
        pageTitleDetails: {
          title: 'The Details',
          nameParam: 'detailsNameParam',
          accountParam: 'detailsAccountParam'
        }
      });
      expect(this.$rootScope.pageTitle).toBe('Spinnaker · The Section: Some thing (test) · The Details: Some specific thing (prod)');

      this.pageTitleService.handleRoutingSuccess({
        pageTitleSection: {
          title: 'The Section'
        },
        pageTitleDetails: {
          title: 'The Details',
          nameParam: 'detailsNameParam',
          accountParam: 'detailsAccountParam',
          regionParam: 'detailsRegionParam'
        }
      });
      expect(this.$rootScope.pageTitle).toBe('Spinnaker · The Section · The Details: Some specific thing (prod:us-east-1)');

      this.pageTitleService.handleRoutingSuccess({
        pageTitleSection: {
          title: 'The Section'
        },
        pageTitleDetails: {
          title: 'The Details',
          nameParam: 'detailsNameParam',
          regionParam: 'detailsRegionParam'
        }
      });
      expect(this.$rootScope.pageTitle).toBe('Spinnaker · The Section · The Details: Some specific thing (us-east-1)');

      this.pageTitleService.handleRoutingSuccess({
        pageTitleSection: {
          title: 'The Section'
        },
        pageTitleDetails: {
          title: 'The Details',
          nameParam: 'detailsNameParam'
        }
      });
      expect(this.$rootScope.pageTitle).toBe('Spinnaker · The Section · The Details: Some specific thing');

      this.pageTitleService.handleRoutingSuccess({
        pageTitleSection: {
          title: 'The Section'
        },
        pageTitleDetails: {
          title: 'The Details'
        }
      });
      expect(this.$rootScope.pageTitle).toBe('Spinnaker · The Section · The Details');
    })
  });

});
