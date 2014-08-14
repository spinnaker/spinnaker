'use strict';

describe('Filter: regionAbbreviator', function () {

  // load the controller's module
  beforeEach(module('deckApp'));

  var filter;

  beforeEach(inject(function ($filter) {
    filter = $filter('regionAbbreviator');
  }));

  it('should handle cardinal directions', function () {
    expect(filter('us-west-1')).toBe('US-W1');
    expect(filter('eu-east-2')).toBe('EU-E2');
    expect(filter('sa-north-1')).toBe('SA-N1');
    expect(filter('ap-south-1')).toBe('AP-S1');
  });

  it('should handle intermediate directions', function() {
    expect(filter('ap-southeast-2')).toBe('AP-SE2');
    expect(filter('ap-northeast-1')).toBe('AP-NE1');
    expect(filter('ap-northwest-1')).toBe('AP-NW1');
    expect(filter('ap-southwest-3')).toBe('AP-SW3');
  });

  it('should handle multiple regions', function() {
    expect(filter('ap-southeast-2, us-west-1')).toBe('AP-SE2, US-W1');
  });

  it('should handle non-region characters', function() {
    expect(filter('[us-west-1, us-east-1, eu-west-1]')).toBe('[US-W1, US-E1, EU-W1]');
  });

  it('should ignore capitalized content', function() {
    expect(filter('US-west-1')).toBe('US-west-1');
    expect(filter('us-West-1')).toBe('us-West-1');
  });

  it('should ignore invalid intermediate directions', function() {
    expect(filter('ap-eastnorth-1')).toBe('ap-eastnorth-1');
  });

  it('should ignore invalid values but translate valid ones', function() {
    expect(filter('ap-southeast-2, us-wist-1, us-east-1')).toBe('AP-SE2, us-wist-1, US-E1');
  });

  it('should abbreviate availability zones', function() {
    expect(filter('us-west-1d')).toBe('US-W1d');
  });
});
