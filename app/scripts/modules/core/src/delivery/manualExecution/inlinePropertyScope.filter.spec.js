'use strict';

describe('inline property filter', function () {

  let filter;

  beforeEach(
    window.module(require('./inlinePropertyScope.filter'))
  );

  beforeEach(
    window.inject(function($filter) {
      filter = $filter('inlinePropertyScope');
    })
  );

  describe('only display fields with values', function () {

    it('should only show env', function () {
      const input = {'env':'prod','appIdList':[''],'cluster':'','asg':'','region':'','stack':''};
      expect(filter(input)).toBe('Env: prod');
    });

    it('should only show env and the one app', function () {
      const input = {'env':'prod','appIdList':['mahe'],'cluster':'','asg':'','region':'','stack':''};
      expect(filter(input)).toBe('Env: prod, Apps: mahe');
    });

    it('should only show env and both apps', function () {
      const input = {'env':'prod','appIdList':['mahe', 'deck'],'cluster':'','asg':'','region':'','stack':''};
      expect(filter(input)).toBe('Env: prod, Apps: mahe,deck');
    });

    it('should only show cluster', function () {
      const input = {'env':'','appIdList':[''],'cluster':'cluster-1','asg':'','region':'','stack':''};
      expect(filter(input)).toBe('Cluster: cluster-1');
    });

    it('should only show asg', function () {
      const input = {'env':'','appIdList':[''],'cluster':'','asg':'asg-main','region':'','stack':''};
      expect(filter(input)).toBe('ASG: asg-main');
    });

    it('should only show region', function () {
      const input = {'env':'','appIdList':[''],'cluster':'','asg':'','region':'us-east-1','stack':''};
      expect(filter(input)).toBe('Region: us-east-1');
    });

    it('should only show stack', function () {
      const input = {'env':'','appIdList':[''],'cluster':'','asg':'','region':'','stack':'foo-main-1'};
      expect(filter(input)).toBe('Stack: foo-main-1');
    });
  });
});

