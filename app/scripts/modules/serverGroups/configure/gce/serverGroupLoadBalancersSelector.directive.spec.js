'use strict';

describe('Directive: GCE Load Balancers Selector', function() {

  beforeEach(loadDeckWithoutCacheInitializer);
  beforeEach(module('spinnaker.serverGroup.configure.gce',
                    'spinnaker.templates',
                    'ui.select',
                    'spinnaker.utils.timeFormatters'));

  var selector = angular.element('<gce-server-group-load-balancers-selector command="command" />');
  var element, gceServerGroupConfigurationService;

  beforeEach(inject(function(_gceServerGroupConfigurationService_, _infrastructureCaches_){
    gceServerGroupConfigurationService = _gceServerGroupConfigurationService_;

    var lastRefreshed = '2015-01-01T00:00:00';
    var d = new Date(lastRefreshed);
    var t = d.getTime();
    _infrastructureCaches_.loadBalancers = {
      getStats: function() {return {ageMax: t}}
    };
  }));

  beforeEach(inject(function($rootScope, $compile) {
    this.scope = $rootScope.$new();
    this.compile = $compile;

    this.scope.command = {backingData: {filtered: {loadBalancers: []}}};
    element = this.compile(selector)(this.scope);
    this.scope.$apply();
  }));

  it('should render the last refreshed time', function() {
    var refreshedSpan = element.find('span:contains("last refreshed")');
    expect(refreshedSpan.length).toEqual(1);
    expect(refreshedSpan.html()).toEqual('last refreshed 2015-01-01 00:00:00');
  });

  it('should refresh the load balancer cache', function(){
    spyOn(gceServerGroupConfigurationService, 'refreshLoadBalancers');
    element = this.compile(selector)(this.scope);
    this.scope.$apply();

    var a = element.find('a');
    $(a).click().trigger('click');
    this.scope.$apply();
    expect(gceServerGroupConfigurationService.refreshLoadBalancers.calls.any()).toBeTruthy();
  });
});
