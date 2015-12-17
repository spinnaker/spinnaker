 'use strict';

 xdescribe('Directives: azureVpcTag', function () {

   var $q, vpcReader;

   beforeEach(
     window.module(
       require('./vpcTag.directive.js'),
       require('./vpc.read.service.js')
     )
   );

   beforeEach(window.inject(function ($rootScope, $compile, _$q_, _azureVpcReader_) {
     this.scope = $rootScope.$new();
     this.compile = $compile;
     $q = _$q_;
     vpcReader = _azureVpcReader_;
   }));

   describe('vpc tag rendering - no VPC provided', function () {

     it('displays default message when no vpcId supplied', function () {
       var domNode = this.compile('<vpc-tag></vpc-tag>')(this.scope);
       this.scope.$digest();
       expect(domNode.find('span').text()).toBe('None (EC2 Classic)');
     });

     it('displays default message when undefined vpcId supplied', function () {
       var domNode = this.compile('<vpc-tag vpc-id="notDefined"></vpc-tag>')(this.scope);
       this.scope.$digest();
       expect(domNode.find('span').text()).toBe('None (EC2 Classic)');
     });

     it('displays default message when null vpcId supplied', function () {
       this.scope.vpcId = null;
       var domNode = this.compile('<vpc-tag vpc-id="vpcId"></vpc-tag>')(this.scope);
       this.scope.$digest();
       expect(domNode.find('span').text()).toBe('None (EC2 Classic)');
     });
   });

   describe('vpc tag rendering - VPC provided', function () {

     it('displays vpc name when found', function() {
       spyOn(vpcReader, 'getVpcName').and.callFake(function() {
         return $q.when('Main VPC');
       });
       this.scope.vpcId = 'vpc-1';
       var domNode = this.compile('<vpc-tag vpc-id="vpcId"></vpc-tag>')(this.scope);
       this.scope.$digest();
       expect(domNode.find('span').text()).toBe('Main VPC (vpc-1)');
     });

     it('displays vpc id when not found', function() {
       spyOn(vpcReader, 'getVpcName').and.callFake(function() {
         return $q.when(null);
       });
       this.scope.vpcId = 'vpc-2';
       var domNode = this.compile('<vpc-tag vpc-id="vpcId"></vpc-tag>')(this.scope);
       this.scope.$digest();
       expect(domNode.find('span').text()).toBe('(vpc-2)');
     });
   });
 });
