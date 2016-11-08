'use strict';

import {matchers} from '../../../../../test/helpers/customMatchers';

describe('Directives: reservation report', function () {

  require('./reservationReport.directive.html');

  beforeEach(window.module(require('./reservationReport.directive')));
  beforeEach(function() {
    jasmine.addMatchers(matchers);
  });

  beforeEach(window.inject(function ($rootScope, $compile, reservationReportReader, $httpBackend, settings, accountService, $q) {
    this.scope = $rootScope.$new();
    this.compile = $compile;
    this.reservationReportReader = reservationReportReader;
    this.$http = $httpBackend;
    this.settings = settings;

    spyOn(accountService, 'challengeDestructiveActions').and.returnValue($q.when(false));

    this.$http.expectGET([this.settings.gateUrl, 'reports', 'reservation', 'v2'].join('/')).respond(200, {
      reservations: [
        { availabilityZone: 'us-east-1a', instanceType: 'm3.medium', os: 'LINUX', region: 'us-east-1',
          accounts: {
            prod: {reserved: 3, used: 1, surplus: 2, reservedVpc: 5, usedVpc: 1, surplusVpc: 4 },
            test: {reserved: 3, used: 0, surplus: 3, reservedVpc: 1, usedVpc: 1, surplusVpc: 0 }
          },
        },
        { availabilityZone: 'us-east-1a', instanceType: 'm3.large', os: 'LINUX', region: 'us-east-1',
          accounts: {
            prod: {reserved: 1, used: 1, surplus: 0, reservedVpc: 1, usedVpc: 1, surplusVpc: 0 },
            test: {reserved: 3, used: 1, surplus: 2, reservedVpc: 2, usedVpc: 1, surplusVpc: 1 }
          },
        },
        { availabilityZone: 'us-east-1b', instanceType: 'm3.medium', os: 'LINUX', region: 'us-east-1',
          accounts: {
            prod: {reserved: 13, used: 11, surplus: 2, reservedVpc: 15, usedVpc: 11, surplusVpc: 4 },
            test: {reserved: 13, used: 10, surplus: 3, reservedVpc: 11, usedVpc: 11, surplusVpc: 0 }
          },
        },
        { availabilityZone: 'us-east-1b', instanceType: 'm3.large', os: 'LINUX', region: 'us-east-1',
          accounts: {
            prod: {reserved: 1, used: 2, surplus: -1, reservedVpc: 11, usedVpc: 11, surplusVpc: 0 },
            test: {reserved: 13, used: 1, surplus: 12, reservedVpc: 12, usedVpc: 11, surplusVpc: 1 }
          },
        },
        { availabilityZone: 'us-east-1c', instanceType: 'm3.medium', os: 'LINUX', region: 'us-east-1',
          accounts: {
            prod: {reserved: 23, used: 1, surplus: 22, reservedVpc: 25, usedVpc: 21, surplusVpc: 4 },
            test: {reserved: 23, used: 33, surplus: -10, reservedVpc: 21, usedVpc: 21, surplusVpc: 0 }
          },
        },
        { availabilityZone: 'us-east-1c', instanceType: 'm3.large', os: 'LINUX', region: 'us-east-1',
          accounts: {
            prod: {reserved: 31, used: 1, surplus: 30, reservedVpc: 31, usedVpc: 31, surplusVpc: 0 },
            test: {reserved: 33, used: 1, surplus: 32, reservedVpc: 2, usedVpc: 31, surplusVpc: -29 }
          },
        },
      ]
    });

  }));

  it('displays two rows for report', function () {
    var scope = this.scope,
        compile = this.compile;

    scope.account = 'prod';
    scope.region = 'us-east-1';
    scope.instanceType = 'm3.medium';
    scope.zones = ['us-east-1a', 'us-east-1c'];
    scope.isVpc = true;

    var report = compile('<reservation-report account="account" region="region" instance-type="instanceType" zones="zones" is-vpc="isVpc"></reservation-report>')(scope);

    scope.$digest();
    this.$http.flush();

    expect(report.find('h4')).textMatch('VPC Reservations for m3.medium in prod');
    expect(report.find('tbody tr').size()).toBe(2);
  });

  it('updates report title, rows when account, region, instanceType, zones, vpc flag change', function () {
    var scope = this.scope,
        compile = this.compile;

    scope.account = 'prod';
    scope.region = 'us-east-1';
    scope.instanceType = 'm3.medium';
    scope.zones = ['us-east-1a', 'us-east-1c'];
    scope.isVpc = false;

    var report = compile('<reservation-report account="account" region="region" instance-type="instanceType" zones="zones" is-vpc="isVpc"></reservation-report>')(scope);

    scope.$digest();
    this.$http.flush();

    expect(report.find('h4')).textMatch('Reservations for m3.medium in prod');
    expect(report.find('tbody tr td:eq(2)')).textMatch('3');

    scope.isVpc = true;
    scope.$digest();
    expect(report.find('h4')).textMatch('VPC Reservations for m3.medium in prod');
    expect(report.find('tbody tr td:eq(2)')).textMatch('5');

    scope.instanceType = 'm3.large';
    scope.$digest();
    expect(report.find('h4')).textMatch('VPC Reservations for m3.large in prod');
    expect(report.find('tbody tr td:eq(2)')).textMatch('1');

    scope.account = 'test';
    scope.$digest();
    expect(report.find('h4')).textMatch('VPC Reservations for m3.large in test');
    expect(report.find('tbody tr td:eq(2)')).textMatch('2');

    scope.region = 'us-west-2';
    scope.$digest();
    expect(report.find('h4')).textMatch('VPC Reservations for m3.large in test');
    expect(report.find('tbody tr').size()).toBe(0);

    scope.region = 'us-east-1';
    scope.zones = ['us-east-1a'];
    scope.$digest();
    expect(report.find('h4')).textMatch('VPC Reservations for m3.large in test');
    expect(report.find('tbody tr').size()).toBe(1);
    expect(report.find('tbody tr td:eq(2)')).textMatch('2');
  });
});
