'use strict';

describe('FastProperty Write Service:', function () {

  var service;

  beforeEach(
    window.module(
      require('./fastProperty.write.service')
    )
  );

  beforeEach(window.inject(function (_fastPropertyWriter_) {
    service = _fastPropertyWriter_;
  }));

  describe('flatten fast property and scope', function () {
    var json = {
      'key': 'foo',
      'value': 'bar',
      'description': 'hi',
      'email': 'zthrash@gmail.com',
      'cmc': 'fooBar',
      'impactCount': '6589',
      'selectedScope': {
        'appId': 'api',
        'region': 'us-east-1'
      }
    };

    it('should flatten the objcet with embedded scope attributes', function () {
      var flattened = service.flattenFastProperty(json);

      expect(flattened).toEqual({
        'key': 'foo',
        'value': 'bar',
        'description': 'hi',
        'email': 'zthrash@gmail.com',
        'cmc': 'fooBar',
        'appId': 'api',
        'region': 'us-east-1',
        'sourceOfUpdate': 'spinnaker',
        'updatedBy':'[anonymous]'
      });

    });

  });

  describe('create promotional payload', function () {
    var json = {
      'key': 'foo',
      'value': 'bar',
      'description': 'hi',
      'email': 'zthrash@gmail.com',
      'cmc': 'fooBar',
      'impactCount': '6589',
      'selectedScope': {
        'appId': 'api',
        'region': 'us-east-1'
      }
    };

    it('should transform the payload', function () {
      var payload = service.createPromotedPayload(json);

      expect(payload).toEqual({
        'key': 'foo',
        'value': 'bar',
        'description': 'hi',
        'email': 'zthrash@gmail.com',
        'cmc': 'fooBar',
        'updatedBy': '[anonymous]',
        'sourceOfUpdate': 'spinnaker',
        'scope': {
          'appId': 'api',
          'region': 'us-east-1'
        }
      });
    });

  });

  describe('extract all the scope properties into a selectedScope', function () {
    var fpJson = {
      'key': 'foo',
      'value': 'bar',
      'description': 'hi',
      'email': 'zthrash@gmail.com',
      'cmc': 'fooBar',
      'appId': 'api',
      'region': 'us-east-1',
      'asg': 'asg',
      'stack': 'prod',
      'serverId': 'i-8888888',
      'zone': 'us-east-1a',
      'cluster': 'mahe-prod',
      'sourceOfUpdate': 'spinnaker',
      'updatedBy':'[anonymous]'
    };

    it('should create a fastProperty object with a selectedScope object', function () {
      var fp = service.extractScopeIntoSelectedScope(fpJson);

      expect(fp.selectedScope).toEqual({
        'appId': 'api',
        'region': 'us-east-1',
        'asg': 'asg',
        'stack': 'prod',
        'serverId': 'i-8888888',
        'zone': 'us-east-1a',
        'cluster': 'mahe-prod'
      });
    });

  });

  describe('extract only one of the scope properties into a selectedScope', function () {
    var fpJson = {
      'key': 'foo',
      'value': 'bar',
      'description': 'hi',
      'email': 'zthrash@gmail.com',
      'cmc': 'fooBar',
      'appId': 'api',
      'sourceOfUpdate': 'spinnaker',
      'updatedBy':'[anonymous]'
    };

    it('should create a fastProperty object with a selectedScope object', function () {
      var fp = service.extractScopeIntoSelectedScope(fpJson);

      expect(fp.selectedScope).toEqual({
        'appId': 'api',
      });
    });

  });
});
