 'use strict';

 import {SECURITY_GROUP_READER} from 'core/securityGroup/securityGroupReader.service';

 describe('Controller: Azure.CreateSecurityGroup', function () {

   beforeEach(
     window.module(
       SECURITY_GROUP_READER,
       require('./CreateSecurityGroupCtrl.js')
     )
   );

   describe('filtering', function() {

     // Initialize the controller and a mock scope
     beforeEach(window.inject(function ($controller, $rootScope, $q, accountService, securityGroupReader,
                                 taskMonitorService, azureSecurityGroupWriter) {

       this.$scope = $rootScope.$new();
       this.$q = $q;
       this.accountService = accountService;
       this.securityGroupReader = securityGroupReader;
       this.taskMonitorService = taskMonitorService;
       this.securityGroupWriter = azureSecurityGroupWriter;

       spyOn(this.accountService, 'listAccounts').and.returnValue(
         $q.when(['prod', 'test'])
       );

       spyOn(this.accountService, 'getRegionsForAccount').and.returnValue(
         $q.when(['us-east-1', 'us-west-1'])
       );

       spyOn(this.securityGroupReader, 'getAllSecurityGroups').and.returnValue(
         $q.when(
           {
             prod: {
               azure: {
                 'us-east-1': [
                   { name: 'group1', vpcId: null, id: '1'},
                   { name: 'group2', vpcId: null, id: '2'},
                   { name: 'group3', vpcId: 'vpc1-pe', id: '3'},
                 ],
                 'us-west-1': [
                   { name: 'group1', vpcId: null, id: '1'},
                   { name: 'group3', vpcId: 'vpc2-pw', id: '3'},
                 ]
               }
             },
             test: {
               azure: {
                 'us-east-1': [
                   { name: 'group1', vpcId: null, id: '1'},
                   { name: 'group2', vpcId: 'vpc1-te', id: '2'},
                   { name: 'group3', vpcId: 'vpc1-te', id: '3'},
                   { name: 'group4', vpcId: 'vpc2-te', id: '4'},
                 ],
                 'us-west-1': [
                   { name: 'group1', vpcId: null, id: '1'},
                   { name: 'group3', vpcId: 'vpc1-tw', id: '3'},
                   { name: 'group3', vpcId: 'vpc2-tw', id: '3'},
                   { name: 'group5', vpcId: 'vpc2-tw', id: '5'},
                 ]
               }
             }
           }
         )
       );

       this.initializeCtrl = function() {
         this.ctrl = $controller('azureCreateSecurityGroupCtrl', {
           $scope: this.$scope,
           $uibModalInstance: { result: this.$q.when(null) },
           accountService: this.accountService,
           securityGroupReader: this.securityGroupReader,
           taskMonitorService: this.taskMonitorService,
           securityGroupWriter: this.securityGroupWriter,
           application: {},
           securityGroup: { regions: [], securityGroupIngress: [] },
         });
         this.$scope.$digest();
       };

     }));

     it('initializes with no security groups available for ingress permissions', function () {
       this.initializeCtrl();
       expect(this.$scope.securityGroup.securityRules.length).toBe(0);
     });

   });
 });
