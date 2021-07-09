'use strict';

import _ from 'lodash';

import { CloudProviderRegistry } from '../../cloudProvider';
import { SETTINGS } from '../../config/settings';

describe('Controller: ApplicationProviderFieldsCtrl', function () {
  let controller, scope;

  beforeEach(window.module(require('./applicationProviderFields.component').name));

  beforeEach(() => {
    SETTINGS.providers.aws.defaultPath = '/path/to/somewhere';
  });

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();

      let application = {
        cloudProviders: [],
      };

      let cloudProviders = ['aws', 'gce'];

      spyOn(CloudProviderRegistry, 'getValue').and.returnValue('path/to/template');
      spyOn(CloudProviderRegistry, 'hasValue').and.returnValue(true);
      controller = $controller(
        'ApplicationProviderFieldsCtrl',
        {
          $scope: scope,
        },
        {
          application,
          cloudProviders,
        },
      );
    }),
  );

  afterEach(SETTINGS.resetToOriginal);

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });

  describe('getRelevantProviderFieldsTemplates', function () {
    it('returns all templateUrls if the application has no selected cloud providers', function () {
      let templates = controller.getRelevantProviderFieldsTemplates();
      expect(templates.length).toEqual(2);
      expect(templates.every((template) => template === 'path/to/template')).toEqual(true);
    });

    it('if application has selected cloud providers, it asks CloudProviderRegistry for only those templateUrls', function () {
      controller.application.cloudProviders.push('gce');

      let templates = controller.getRelevantProviderFieldsTemplates();
      expect(templates.length).toEqual(1);

      let [hasValueSpy, getValueSpy] = [CloudProviderRegistry.hasValue, CloudProviderRegistry.getValue];

      [hasValueSpy, getValueSpy].forEach((spy) => {
        expect(spy).toHaveBeenCalledWith('gce', 'applicationProviderFields.templateUrl');
        expect(spy).not.toHaveBeenCalledWith('aws', 'applicationProviderFields.templateUrl');
      });
    });

    it(`accommodates typeof application.cloudProviders === 'string'`, function () {
      spyOn(controller, 'getRelevantProviderFieldsTemplates').and.callThrough();

      controller.application.cloudProviders = 'gce,aws';
      let templates = controller.getRelevantProviderFieldsTemplates();
      expect(templates.length).toEqual(2);
      expect(controller.getRelevantProviderFieldsTemplates).not.toThrow();
    });
  });

  describe('initializeApplicationFields', function () {
    it('does not mutate the application if the setting has been already defined within the application', function () {
      _.set(controller.application, 'providerSettings.aws.defaultPath', '/path/to/somewhere/else');
      let applicationBeforeFunctionCall = _.cloneDeep(controller.application);

      controller.initializeApplicationField('aws.defaultPath');

      expect(_.isEqual(controller.application, applicationBeforeFunctionCall)).toEqual(true);
      expect(_.get(controller.application, 'providerSettings.aws.defaultPath')).toEqual('/path/to/somewhere/else');
    });

    it('does not mutate the application if the path does not exist within the global provider settings', function () {
      SETTINGS.providers.aws = undefined;
      let applicationBeforeFunctionCall = _.cloneDeep(controller.application);

      controller.initializeApplicationField('aws.defaultPath');

      expect(_.isEqual(controller.application, applicationBeforeFunctionCall)).toEqual(true);
    });

    it(`mutates the application to match the global provider settings
      if the setting has not been defined within the application`, function () {
      controller.initializeApplicationField('aws.defaultPath');

      expect(_.get(controller.application, 'providerSettings.aws.defaultPath')).toEqual('/path/to/somewhere');
    });
  });
});
