import modelBuilderModule from '../../../core/application/applicationModel.builder';

describe('Controller: cfServerGroupDetailsCtrl', function () {
  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./serverGroupDetails.cf.controller'),
      modelBuilderModule
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, applicationModelBuilder) {
      scope = $rootScope.$new();
      let app = applicationModelBuilder.createApplication({key: 'serverGroups', lazy: true}, {key: 'loadBalancers', lazy: true});
      controller = $controller('cfServerGroupDetailsCtrl', {
        $scope: scope,
        app: app,
        serverGroup: {}
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});

