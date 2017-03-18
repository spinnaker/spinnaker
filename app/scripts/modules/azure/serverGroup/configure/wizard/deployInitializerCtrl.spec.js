 'use strict';

 describe('DeployInitializerCtrl', function() {

   beforeEach(
     window.module(
       require('./deployInitializer.controller')
     )
   );

   beforeEach(window.inject(function ($controller, $rootScope, $q) {
     this.$controller = $controller;
     this.serverGroupCommandBuilder = {};
     this.securityGroupReader = {};
     this.deploymentStrategyService = {};
     this.$scope = $rootScope.$new();
     this.$q = $q;

   }));

   describe('template initialization', function() {
     beforeEach(function() {
       var $q = this.$q,
           $scope = this.$scope;
       this.initializeController = function () {
         var deploymentStrategiesMock = {
           listAvailableStrategies: function() { return $q.when([]); }
         };

         this.controller = this.$controller('azureDeployInitializerCtrl', {
           $scope: $scope,
           serverGroupCommandBuilder: this.serverGroupCommandBuilder,
           securityGroupReader: this.securityGroupReader,
           deploymentStrategyService: deploymentStrategiesMock
         });
       };

     });
     it('creates separate template options for each account and region of a cluster', function() {

       var application = {
         serverGroups: { data: [
           {
             name: 'sg1',
             cluster: 'cluster1',
             account: 'test',
             region: 'us-east-1',
             type: 'azure',
           },
           {
             name: 'sg2',
             cluster: 'cluster1',
             account: 'prod',
             region: 'us-east-1',
             type: 'azure',
           },
           {
             name: 'sg2',
             cluster: 'cluster1',
             account: 'prod',
             region: 'us-east-1',
             type: 'azure',
           },
         ]}
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
