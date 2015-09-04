'use strict';

describe('DeployInitializerCtrl', function() {

  beforeEach(
    window.module(
      require('./deployInitializer.controller')
    )
  );

  beforeEach(window.inject(function ($controller, $rootScope, _, $q) {
    this.$controller = $controller;
    this.serverGroupCommandBuilder = {};
    this.securityGroupReader = {};
    this.deploymentStrategyService = {};
    this._ = _;
    this.$scope = $rootScope.$new();
    this.$q = $q;

  }));

  describe('template initialization', function() {
    beforeEach(function() {
      var $q = this.$q,
          $scope = this.$scope,
          _ = this._;
      this.initializeController = function () {
        var deploymentStrategiesMock = {
          listAvailableStrategies: function() { return $q.when([]); }
        };

        this.controller = this.$controller('awsDeployInitializerCtrl', {
          $scope: $scope,
          serverGroupCommandBuilder: this.serverGroupCommandBuilder,
          securityGroupReader: this.securityGroupReader,
          deploymentStrategyService: deploymentStrategiesMock,
          _: _
        });
      };

    });
    it('creates separate template options for each account and region of a cluster', function() {

      var application = {
        serverGroups: [
          {
            name: 'sg1',
            cluster: 'cluster1',
            account: 'test',
            region: 'us-east-1',
            type: 'aws',
          },
          {
            name: 'sg2',
            cluster: 'cluster1',
            account: 'prod',
            region: 'us-east-1',
            type: 'aws',
          },
          {
            name: 'sg2',
            cluster: 'cluster1',
            account: 'prod',
            region: 'us-east-1',
            type: 'aws',
          },
        ]
      };

      this.$scope.application = application;
      this.$scope.command = { viewState: {} };
      this.$scope.state = {};

      this.initializeController();

      var templates = this.$scope.templates;
      expect(templates.length).toBe(3);

      // first template is always "None"
      expect(templates[1].cluster).toBe('cluster1');
      expect(templates[1].cluster).toBe('cluster1');
      expect(templates[2].cluster).toBe('cluster1');



    });
  });

});
