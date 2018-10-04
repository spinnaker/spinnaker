'use strict';

const angular = require('angular');
import * as momentTimezone from 'moment-timezone';

import { InfrastructureCaches, SETTINGS, TIME_FORMATTERS } from '@spinnaker/core';

require('./loadBalancerSelector.directive.html');

describe('Directive: GCE Load Balancers Selector', function() {
  beforeEach(
    window.module(
      require('./loadBalancerSelector.directive.js').name,
      require('./../../serverGroupConfiguration.service.js').name,
      require('ui-select'),
      TIME_FORMATTERS,
    ),
  );

  let selector, element, gceServerGroupConfigurationService, expectedTime;

  beforeEach(
    window.inject(function(_gceServerGroupConfigurationService_, _cacheInitializer_) {
      gceServerGroupConfigurationService = _gceServerGroupConfigurationService_;

      const lastRefreshed = new Date('2015-01-01T00:00:00').getTime();
      _cacheInitializer_.refreshCache('loadBalancers');
      InfrastructureCaches.get('loadBalancers').getStats = function() {
        return { ageMax: lastRefreshed };
      };
      const m = momentTimezone.tz(lastRefreshed, SETTINGS.defaultTimeZone);
      expectedTime = m.format('YYYY-MM-DD HH:mm:ss z');

      selector = angular.element('<gce-server-group-load-balancer-selector command="command" />');
    }),
  );

  beforeEach(
    window.inject(function($rootScope, $compile) {
      this.scope = $rootScope.$new();
      this.compile = $compile;

      this.scope.command = { backingData: { filtered: { loadBalancers: [] } } };
      element = this.compile(selector)(this.scope);
      this.scope.$apply();
    }),
  );

  it('should render the last refreshed time', function() {
    const refreshedSpan = element.find('span:contains("last refreshed")');
    expect(refreshedSpan.length).toEqual(1);
    expect(refreshedSpan.html()).toEqual(`last refreshed ${expectedTime}`);
  });

  it('should refresh the load balancer cache', function() {
    spyOn(gceServerGroupConfigurationService, 'refreshLoadBalancers').and.returnValue({ then: angular.noop });
    element = this.compile(selector)(this.scope);
    this.scope.$apply();

    const a = element.find('a');
    $(a)
      .click()
      .trigger('click');
    this.scope.$apply();
    expect(gceServerGroupConfigurationService.refreshLoadBalancers).toHaveBeenCalled();
  });
});
