'use strict';

describe('DeployInitializerCtrl', function() {

  beforeEach(module('deckApp.pipelines.stage.deploy'));

  beforeEach(inject(function ($controller, $rootScope, _, $q) {
    this.$controller = $controller;
    this.serverGroupCommandBuilder = {};
    this.securityGroupService = {};
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

        this.controller = this.$controller('DeployInitializerCtrl', {
          $scope: $scope,
          serverGroupCommandBuilder: this.serverGroupCommandBuilder,
          securityGroupService: this.securityGroupService,
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
            region: 'us-east-1'
          },
          {
            name: 'sg2',
            cluster: 'cluster1',
            account: 'prod',
            region: 'us-east-1'
          },
          {
            name: 'sg2',
            cluster: 'cluster1',
            account: 'prod',
            region: 'us-east-1'
          },
        ]
      };

      this.$scope.application = application;

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
