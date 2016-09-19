import modelBuilderModule from '../../../core/application/applicationModel.builder.ts';

describe('Controller: Azure ServerGroupDetailsCtrl', function () {
  var controller;
  var $scope;

  beforeEach(
    window.module(
      require('./serverGroupDetails.azure.controller'),
      modelBuilderModule
      )
    );

  beforeEach(
    window.inject(function ($controller, $rootScope, $state, applicationModelBuilder) {
      $scope = $rootScope.$new();
      let application = applicationModelBuilder.createApplication({ key: 'serverGroups', lazy: true }, { key: 'loadBalancers', lazy: true });
      spyOn($state, 'go').and.returnValue(null);

      controller = $controller('azureServerGroupDetailsCtrl', {
        $scope: $scope,
        $state: $state,
        app: application,
        serverGroup: {}
      });
    })
  );

  describe('Determine if a serverGroup is the only one in the Cluster', function () {

    it('should return true if the serverGroup is the only one in the Cluster', function () {

      var serverGroup = {
        cluster: 'foo',
        account: 'test',
        region: 'us-east-1',
      };

      var application = {
        clusters: [
          {
            name: 'foo',
            account: 'test',
            region: 'us-east-1',
            serverGroups: [serverGroup]
          }
        ]
      };

      var result = controller.isLastServerGroupInRegion(serverGroup, application);

      expect(result).toBe(true);
    });

    it('should return false if the serverGroup is NOT the only one in the Cluster', function () {

      var serverGroup = {
        cluster: 'foo'
      };

      var application = {
        clusters: [
          {
            name: 'foo',
            serverGroups: [serverGroup, serverGroup]
          }
        ]
      };

      var result = controller.isLastServerGroupInRegion(serverGroup, application);

      expect(result).toBe(false);
    });

    it('should return false if an error is thrown', function () {

      var serverGroup = {};
      var application = {};

      var result = controller.isLastServerGroupInRegion(serverGroup, application);

      expect(result).toBe(false);
    });

  });

});
