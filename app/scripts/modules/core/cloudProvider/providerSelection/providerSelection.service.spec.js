'use strict';

fdescribe('providerSelectionService: API', () => {

  let cloudProvider;
  beforeEach(
    window.module(
      require('../cloudProvider.registry'),
      function (cloudProviderRegistryProvider) {
        cloudProvider = cloudProviderRegistryProvider;
      }
    )
  );

  beforeEach(window.module(require('./providerSelection.service'),
    require('../../account/account.service')));

  // required to ensure registry provider is available
  let $q, $scope, $modal, accountService, cloudProviderRegistry, providerService;
  beforeEach(
    window.inject(
      (_$q_, $rootScope, _$uibModal_, _accountService_, _cloudProviderRegistry_, _providerSelectionService_) => {
        $q = _$q_;
        $scope = $rootScope.$new();
        $modal = _$uibModal_;
        accountService = _accountService_;
        cloudProviderRegistry = _cloudProviderRegistry_;
        providerService = _providerSelectionService_;
      }));

  let hasValue, providers;
  beforeEach(() => {
    spyOn(accountService, 'listProviders').and.callFake(() => $q.when(providers));
    spyOn(cloudProviderRegistry, 'hasValue').and.callFake(() => hasValue);
    spyOn($modal, 'open').and.callFake(() => {
      return {
        result: $q.when('modalProvider')
      };
    });
  });

  beforeEach(() => {
    window.spinnakerSettings.providers.testProvider = {
      defaults: {
        account: 'testProviderAccount',
        region: 'testProviderRegion'
      }
    };
  });

  let application, config;
  beforeEach(() => {

    hasValue = false;
    providers = [];
    delete window.spinnakerSettings.defaultProvider;

    application = {
      name: 'testApplication',
      attributes: {
        cloudProviders: 'testProvider'
      }
    };

    config = {
      name: 'testProvider',
      securityGroup: {}
    };
  });

  it('should use the specified, default provider if the requested provider cannot be found', () => {

    let provider = '';
    window.spinnakerSettings.defaultProvider = 'defaultProvider';

    cloudProvider.registerProvider('fakeProvider', config);
    providerService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('defaultProvider');
  });

  it('should use "aws" as the default provider if the requested provider cannot be found and there is no default set', () => {

    let provider = '';
    cloudProvider.registerProvider('fakeProvider', config);
    providerService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('aws');
  });

  it('should return the specified provider if that provider is registered', () => {

    let provider = '';
    hasValue = true;
    providers = ['testProvider'];
    cloudProvider.registerProvider('testProvider', config);
    providerService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('testProvider');
  });

  it('should return the "use provider" value instead of the configured one if one is specified', () => {

    let provider = '';
    hasValue = true;
    providers = ['testProvider'];
    config.securityGroup.useProvider = 'titus';
    cloudProvider.registerProvider('testProvider', config);
    providerService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('titus');
  });

  it('should use the specified provider from the configuration', () => {

    let provider = '';
    hasValue = true;
    providers = ['aws', 'titus'];
    cloudProvider.registerProvider('aws', { securityGroup: {} });
    cloudProvider.registerProvider('titus', { securityGroup: { useProvider: 'aws' } });

    providerService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('aws');
  });

  it('should use the provider "selected" from the "modal"', () => {

    let provider = '';
    hasValue = true;
    providers = ['aws', 'titus', 'testProvider'];
    cloudProvider.registerProvider('aws', {securityGroup: {}});
    cloudProvider.registerProvider('titus', {securityGroup: {useProvider: 'aws'}});
    cloudProvider.registerProvider('testProvider', config);

    providerService.selectProvider(application, 'securityGroup').then((_provider) => {
      provider = _provider;
    });
    $scope.$digest();
    expect(provider).toBe('modalProvider');
  });
});
