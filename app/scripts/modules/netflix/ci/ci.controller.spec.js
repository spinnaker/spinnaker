import modelBuilderModule from 'core/application/applicationModel.builder';

describe('Controller: NetflixCiCtrl', function () {

  var controller;

  beforeEach(
    window.module(
      require('./ci.controller.js'),
      modelBuilderModule
    )
  );

  let buildControllerWithAppAttributes = (attributes) => {
    window.inject(function ($rootScope, $controller, applicationModelBuilder) {
      let scope = $rootScope.$new();
      let app = applicationModelBuilder.createApplication({});
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
