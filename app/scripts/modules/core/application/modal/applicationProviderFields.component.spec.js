'use strict';

describe('Controller: ApplicationProviderFieldsCtrl', function () {

  let controller,
    scope,
    cloudProviderRegistry,
    angular = require('angular');

  beforeEach(
    window.module(
      require('./applicationProviderFields.component.js'),
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _cloudProviderRegistry_) {
      scope = $rootScope.$new();
      cloudProviderRegistry = _cloudProviderRegistry_;

      let application = {
        cloudProviders: []
      };

      let cloudProviders = [
        'aws',
        'gce',
      ];

      spyOn(cloudProviderRegistry, 'getValue').and.returnValue('path/to/template');
      spyOn(cloudProviderRegistry, 'hasValue').and.returnValue(true);

      controller = $controller('ApplicationProviderFieldsCtrl', {
        $scope: scope,
        cloudProviderRegistry: cloudProviderRegistry,
      }, {
        application,
        cloudProviders,
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });

  describe('getRelevantProviderFieldsTemplates', function() {

    it('returns all templateUrls if the application has no selected cloud providers', function() {

      let templates = controller.getRelevantProviderFieldsTemplates();
      expect(templates.length).toEqual(2);
      expect(templates.every(template => template === 'path/to/template')).toEqual(true);

    });

    it('if application has selected cloud providers, it asks cloudProviderRegistry for only those templateUrls', function() {

      controller.application.cloudProviders.push('gce');

      let templates = controller.getRelevantProviderFieldsTemplates();
      expect(templates.length).toEqual(1);

      let [ hasValueSpy, getValueSpy ] = [ cloudProviderRegistry.hasValue, cloudProviderRegistry.getValue];

      [ hasValueSpy, getValueSpy ]
        .forEach(spy => {
          expect(spy).toHaveBeenCalledWith('gce', 'applicationProviderFields.templateUrl');
          expect(spy).not.toHaveBeenCalledWith('aws', 'applicationProviderFields.templateUrl');
        });

    });

  });

});
