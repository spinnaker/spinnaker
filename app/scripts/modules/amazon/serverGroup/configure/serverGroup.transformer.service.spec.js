'use strict';

describe('awsServerGroupTransformer', function () {
  const angular = require('angular');

  var transformer;

  beforeEach(
    window.module(
      require('./serverGroup.transformer.service.js')
    )
  );

  beforeEach(function () {
    window.inject(function (_awsServerGroupTransformer_) {
      transformer = _awsServerGroupTransformer_;
    });
  });

  describe('command transforms', function () {

    it('sets amiName from allImageSelection', function () {
      var command = {
        viewState: {
          mode: 'create',
          useAllImageSelection: true,
          allImageSelection: 'something-packagebase',
        },
        application: { name: 'theApp'}
      };

      var transformed = transformer.convertServerGroupCommandToDeployConfiguration(command);

      expect(transformed.amiName).toBe('something-packagebase');

    });

    it('removes subnetType property when null', function () {
      var command = {
        viewState: {
          mode: 'create',
          useAllImageSelection: true,
          allImageSelection: 'something-packagebase',
        },
        subnetType: null,
        application: { name: 'theApp'}
      };

      var transformed = transformer.convertServerGroupCommandToDeployConfiguration(command);
      expect(transformed.subnetType).toBe(undefined);

      command.subnetType = 'internal';
      transformed = transformer.convertServerGroupCommandToDeployConfiguration(command);
      expect(transformed.subnetType).toBe('internal');
    });

  });
});
