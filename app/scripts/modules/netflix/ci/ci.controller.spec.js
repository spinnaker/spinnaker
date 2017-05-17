import { APPLICATION_DATA_SOURCE_REGISTRY, APPLICATION_MODEL_BUILDER } from '@spinnaker/core';

describe('Controller: NetflixCiCtrl', function () {

  var controller;

  beforeEach(
    window.module(
      require('./ci.controller.js'),
      APPLICATION_MODEL_BUILDER,
      APPLICATION_DATA_SOURCE_REGISTRY
    )
  );

  let buildControllerWithAppAttributes = (attributes) => {
    window.inject(function ($rootScope, $controller, applicationModelBuilder, applicationDataSourceRegistry) {
      let scope = $rootScope.$new();
      applicationDataSourceRegistry.registerDataSource({key: 'ci'});
      let app = applicationModelBuilder.createApplication(applicationDataSourceRegistry.getDataSources());
      app.attributes = attributes;
      controller = $controller('NetflixCiCtrl', {
        $scope: scope,
        app: app,
      });
    });
  };

  it('should instantiate the controller', function () {
    buildControllerWithAppAttributes({});
    expect(controller).toBeDefined();
  });

  it('should not have all config if repoType missing', function() {
    buildControllerWithAppAttributes({ repoProjectKey: 'testproject', repoSlug: 'myrepo' });
    expect(controller.viewState.hasAllConfig).toBe(false);
  });

  it('should not have all config if repoProjectKey missing', function() {
    buildControllerWithAppAttributes({ repoType: 'stash', repoSlug: 'myrepo' });
    expect(controller.viewState.hasAllConfig).toBe(false);
  });

  it('should not have all config if repoSlug missing', function() {
    buildControllerWithAppAttributes({ repoType: 'stash', repoProjectKey: 'testproject' });
    expect(controller.viewState.hasAllConfig).toBe(false);
  });

  it('should have all config if repoType, repoProjectKey, and repoSlug defined', function() {
    buildControllerWithAppAttributes({ repoType: 'stash', repoProjectKey: 'testproject', repoSlug: 'myrepo' });
    expect(controller.viewState.hasAllConfig).toBe(true);
  });
});
