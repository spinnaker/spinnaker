'use strict';

describe('Service: azureServerGroupConfiguration', function () {

  var service, $q, accountService, securityGroupReader,
    cacheInitializer, loadBalancerReader, $scope;

  beforeEach(
    window.module(
      require('./serverGroupConfiguration.service.js')
      )
    );


  beforeEach(window.inject(function (_azureServerGroupConfigurationService_, _$q_, _accountService_,
    _securityGroupReader_, _cacheInitializer_,
    _loadBalancerReader_, $rootScope) {
    service = _azureServerGroupConfigurationService_;
    $q = _$q_;
    accountService = _accountService_;
    securityGroupReader = _securityGroupReader_;
    cacheInitializer = _cacheInitializer_;
    loadBalancerReader = _loadBalancerReader_;
    $scope = $rootScope.$new();

    this.allLoadBalancers = [
      {
        name: 'elb-1',
        accounts: [
          {
            name: 'test',
            regions: [
              {
                name: 'us-east-1',
                loadBalancers: [
                  { region: 'us-east-1', vpcId: null, name: 'elb-1' },
                  { region: 'us-east-1', vpcId: 'vpc-1', name: 'elb-1' },
                ]
              }
            ]
          }
        ]
      },
      {
        name: 'elb-2',
        accounts: [
          {
            name: 'test',
            regions: [
              {
                name: 'us-east-1',
                loadBalancers: [
                  { region: 'us-east-1', vpcId: null, name: 'elb-2' },
                  { region: 'us-east-1', vpcId: 'vpc-2', name: 'elb-2' },
                ]
              },
              {
                name: 'us-west-1',
                loadBalancers: [
                  { region: 'us-west-1', vpcId: null, name: 'elb-2' },
                ]
              }
            ],
          }
        ]
      }
    ];
  }));

  describe('configureSecurityGroupOptions', function () {

    beforeEach(function () {
      this.allSecurityGroups = {
        azurecred1: {
          eastus: [
            {
              'onlyazure-web': {
                account: 'azure-cred1',
                accountName: 'azure-cred1',
                id: 'onlyazure-web',
                name: 'onlyazure-web',
                network: 'na',
                provider: 'azure',
                region: 'eastus'
              }
            },{
              'onlyazure-cache': {
                account: 'azure-cred1',
                accountName: 'azure-cred1',
                id: 'onlyazure-cache',
                name: 'onlyazure-cache',
                network: 'na',
                provider: 'azure',
                region: 'eastus'
              }
            }
          ],
          westus: [
            {
              'onlyazure-web': {
                account: 'azure-cred1',
                accountName: 'azure-cred1',
                id: 'onlyazure-web',
                name: 'onlyazure-web',
                network: 'na',
                provider: 'azure',
                region: 'westus'
              }
            }
          ]
        }
      };

      this.command = {
        backingData: {
          securityGroups: this.allSecurityGroups,
          filtered: {}
        },
        viewState: {
          securityGroupsConfigured: false
        },
        credentials: 'azurecred1',
        region: 'westus'
      };
    });

    it('finds matching security groups and assigns them to the filtered list the first time', function () {
      this.command.region = 'westus';
      var expected = this.allSecurityGroups.azurecred1['westus'];

      var result = service.configureSecurityGroupOptions(this.command);

      expect(this.command.backingData.filtered.securityGroups).toEqual(expected);
      expect(result).toEqual({ dirty: {securityGroups: true} });
      expect(this.command.viewState.securityGroupConfigured).toBeTrue;
    });

    it('finds matcing security groups, sets dirty flag for subsequent time', function () {
      this.command.region = 'eastus';
      this.command.backingData.filtered.securityGroups = this.allSecurityGroups.azurecred1['westus'];
      var expected = this.allSecurityGroups.azurecred1['eastus'];

      var result = service.configureSecurityGroupOptions(this.command);

      expect(this.command.backingData.filtered.securityGroups).toEqual(expected);
      expect(result).toEqual({ dirty: {securityGroups: true} });
      expect(this.command.viewState.securityGroupConfigured).toBeTrue;
    });

    it('clears the selected securityGroup', function () {
      this.command.selectedSecurityGroup = {
              'onlyazure-web': {
                account: 'azure-cred1',
                accountName: 'azure-cred1',
                id: 'onlyazure-web',
                name: 'onlyazure-web',
                network: 'na',
                provider: 'azure',
                region: 'westus'
              }
            };
      this.command.region = 'eastus';

      var result = service.configureSecurityGroupOptions(this.command);

      expect(this.command.selectedSecurityGroup).toBeNull;
      expect(result).toEqual({ dirty: {securityGroups: true} });
      expect(this.command.viewState.securityGroupConfigured).toBeTrue;
    });

    it('returns no security groups if none match', function() {
      this.command.region = 'eastasia';
      this.command.backingData.filtered.securityGroups = this.allSecurityGroups.azurecred1['westus'];

      var result = service.configureSecurityGroupOptions(this.command);

      expect(this.command.selectedSecurityGroup).toBeUndefined;
      expect(result).toEqual({ dirty: {securityGroups: true} });
      expect(this.command.backingData.filtered.securityGroups).toEqual([]);
      expect(this.command.viewState.securityGroupConfigured).toBeFalse;
    });
  });
});
