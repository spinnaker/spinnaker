'use strict';

describe('Directives: vpcTag', function () {

  beforeEach(
    window.module(
      require('./vpcTag.directive.js'),
      require('./vpc.read.service.js')
    )
  );

  beforeEach(window.inject(function ($rootScope, $compile, $q, vpcReader) {
    this.scope = $rootScope.$new();
    this.compile = $compile;
    spyOn(vpcReader, 'listVpcs').and.callFake(function() {
      return $q.when([{id: 'vpc-1', name: 'Main VPC'}]);
    });
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
      this.scope.vpcId = 'vpc-1';
      var domNode = this.compile('<vpc-tag vpc-id="vpcId"></vpc-tag>')(this.scope);
      this.scope.$digest();
      expect(domNode.find('span').text()).toBe('Main VPC (vpc-1)');
    });

    it('displays vpc id when not found', function() {
      this.scope.vpcId = 'vpc-2';
      var domNode = this.compile('<vpc-tag vpc-id="vpcId"></vpc-tag>')(this.scope);
      this.scope.$digest();
      expect(domNode.find('span').text()).toBe('(vpc-2)');
    });
  });
});