describe('window.angulartics', function(){
  beforeEach(function(){
    jasmine.Clock.useMock();
  });
  afterEach(function(){
    delete window.angularticsTestVendor;
  });

  it('should manage vendor wait count', function(){
    spy = jasmine.createSpy('vendorCallback');
    spyWhenLoaded = jasmine.createSpy('vendorCallbackWhenLoaded');
    angulartics.waitForVendorApi('angularticsTestVendor', 1, 'loaded', spy);
    angulartics.waitForVendorApi('angularticsTestVendor', 1, spyWhenLoaded);
    expect(window.angulartics.waitForVendorCount).toEqual(2);

    jasmine.Clock.tick(1);
    expect(window.angulartics.waitForVendorCount).toEqual(2);

    window.angularticsTestVendor = {};
    jasmine.Clock.tick(1);
    expect(angulartics.waitForVendorCount).toEqual(1);

    window.angularticsTestVendor.loaded = true;
    jasmine.Clock.tick(1);
    expect(window.angulartics.waitForVendorCount).toEqual(0);
    expect(spyWhenLoaded).toHaveBeenCalledWith(window.angularticsTestVendor);
  });

});

describe('Module: angulartics', function() {
  'use strict';

  beforeEach(module('angulartics'));

  it('should be configurable', function() {
    module(function(_$analyticsProvider_) {
      _$analyticsProvider_.virtualPageviews(false);
    });
    inject(function(_$analytics_) {
      expect(_$analytics_.settings.pageTracking.autoTrackVirtualPages).toBe(false);
    });
  });

  describe('Provider: analytics', function() {

    describe('initialize', function() {
      it('should track pages by default', function() {
        inject(function(_$analytics_) {
          expect(_$analytics_.settings.pageTracking.autoTrackVirtualPages).toBe(true);
        });
      });
    });

    describe('ngRoute support', function () {
      var analytics,
        rootScope,
        location;
      beforeEach(module('ngRoute'));
      beforeEach(inject(function(_$analytics_, _$rootScope_, _$location_) {
        analytics = _$analytics_;
        location = _$location_;
        rootScope = _$rootScope_;

        spyOn(analytics, 'pageTrack');
      }));

      it('should track pages on route change', function() {
        location.path('/abc');
        rootScope.$emit('$routeChangeSuccess');
        expect(analytics.pageTrack).toHaveBeenCalledWith('/abc', location);
      });
    });

    describe('ui-router support', function () {
      var analytics,
        rootScope,
        location;
      beforeEach(module('ui.router'));
      beforeEach(inject(function(_$analytics_, _$rootScope_, _$location_) {
        analytics = _$analytics_;
        location = _$location_;
        rootScope = _$rootScope_;

        spyOn(analytics, 'pageTrack');
      }));

      it('should track pages on route change', function() {
        location.path('/abc');
        rootScope.$emit('$stateChangeSuccess');
        expect(analytics.pageTrack).toHaveBeenCalledWith('/abc', location);
      });
    });

  });

  describe('$analyticsProvider', function(){

    describe('registration', function(){
      var expectedHandler = [
        'pageTrack',
        'eventTrack',
        'setUsername',
        'setUserProperties',
        'setUserPropertiesOnce',
        'setSuperProperties',
        'setSuperPropertiesOnce'
      ];
      var capitalize = function (input) {
          return input.replace(/^./, function (match) {
              return match.toUpperCase();
          });
      };

      var $analytics, $analyticsProvider;
      beforeEach(function(){
        module(function(_$analyticsProvider_){
          $analyticsProvider = _$analyticsProvider_;
        });
        inject(function(_$analytics_){
          $analytics = _$analytics_;
        });
      });
      angular.forEach(expectedHandler, function(handlerName){
        it('should install a register function for "'+handlerName+'" on $analyticsProvider', function(){
          var fn = $analyticsProvider['register'+capitalize(handlerName)];
          expect(fn).toBeDefined();
          expect(typeof fn).toEqual('function');
        });
        it('should expose a handler "'+handlerName+'" on $analytics', function(){
          var fn = $analytics[handlerName];
          expect(fn).toBeDefined();
          expect(typeof fn).toEqual('function');
        });
      });
    });
  });

  describe('$analytics', function(){
    describe('buffering', function(){
      var $analytics, $analyticsProvider, eventTrackSpy;
      beforeEach(function(){
        module(function(_$analyticsProvider_){
          $analyticsProvider = _$analyticsProvider_;
          $analyticsProvider.settings.bufferFlushDelay = 0;
        });
        inject(function(_$analytics_){
          $analytics = _$analytics_;
        });
      });

      beforeEach(function(){
        eventTrackSpy = jasmine.createSpy('eventTrackSpy');
      });

      it('should buffer events if waiting on a vendor', function(){
        angulartics.waitForVendorCount++; // Mock that we're waiting for a vendor api
        $analytics.eventTrack('foo'); // These events should be buffered
        $analytics.eventTrack('bar'); // This event should be buffered

        $analyticsProvider.registerEventTrack(eventTrackSpy); // This should immediately flush
        expect(eventTrackSpy.calls.length).toEqual(2);
        expect(eventTrackSpy.calls[0].args).toEqual(['foo']);
        expect(eventTrackSpy.calls[1].args).toEqual(['bar']);
      });

      it('should not buffer events if not waiting on any vendors', function(){
        angulartics.waitForVendorCount = 0; // Mock that we're waiting for a vendor api
        $analytics.eventTrack('foo'); // These events should be buffered
        $analyticsProvider.registerEventTrack(eventTrackSpy); // This should immediately flush
        expect(eventTrackSpy).not.toHaveBeenCalled();
      });

      it('should continue to buffer events until all vendors are resolved', function(){
        angulartics.waitForVendorCount = 2; // Mock that we're waiting for a vendor api
        $analytics.eventTrack('foo'); // These events should be buffered

        $analyticsProvider.registerEventTrack(eventTrackSpy); // This should immediately flush
        expect(eventTrackSpy).toHaveBeenCalledWith('foo');

        $analytics.eventTrack('bar');
        expect(eventTrackSpy.calls.length).toEqual(2);
        expect(eventTrackSpy.calls[1].args).toEqual(['bar']);

        var secondVendor = jasmine.createSpy('secondVendor');
        $analyticsProvider.registerEventTrack(secondVendor); // This should immediately flush
        expect(secondVendor.calls.length).toEqual(2);
        expect(secondVendor.calls[0].args).toEqual(['foo']);
        expect(secondVendor.calls[1].args).toEqual(['bar']);

      });
    });
  });

  describe('Directive: analyticsOn', function () {
    var analytics,
      elem,
      scope;

    function compileElem() {
      inject(function ($compile) {
        $compile(elem)(scope);
      });
      scope.$digest();
    }

    beforeEach(inject(function(_$analytics_, _$rootScope_) {
      analytics = _$analytics_;
      scope = _$rootScope_.$new();
    }));

    it('should not send on and event fields to the eventTrack function', function () {
      elem = angular.element('<div>').attr({
        'analytics-on': 'click',
        'analytics-event': 'InitiateSearch',
        'analytics-category': 'Search'
      });
      spyOn(analytics, 'eventTrack');
      expect(analytics.eventTrack).not.toHaveBeenCalled();

      compileElem();
      elem.triggerHandler('click');
      expect(analytics.eventTrack).toHaveBeenCalledWith('InitiateSearch', {category : 'Search', eventType : 'click'});
    });
  });

});
