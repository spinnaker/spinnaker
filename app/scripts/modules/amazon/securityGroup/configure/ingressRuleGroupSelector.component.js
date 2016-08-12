'use strict';

const angular = require('angular');

require('./ingressRuleGroupSelector.component.less');

module.exports = angular
  .module('spinnaker.amazon.securityGroup.configure.ingressRuleGroupSelector', [
    require('../../../core/utils/lodash'),
  ])
  .component('ingressRuleGroupSelector', {
    bindings: {
      rule: '=',
      securityGroup: '=',
      accounts: '=',
      vpcs: '=',
      allSecurityGroups: '=',
      coordinatesChanged: '=',
      allSecurityGroupsUpdated: '=',
    },
    templateUrl: require('./ingressRuleGroupSelector.component.html'),
    controller: function(_) {

      this.infiniteScroll = {
        currentItems: 20,
      };

      this.addMoreItems = () => this.infiniteScroll.currentItems += 20;

      this.setAvailableSecurityGroups = () => {
        var account = this.rule.accountName || this.securityGroup.credentials;
        var regions = this.securityGroup.regions;
        var vpcId = this.rule.vpcId || this.securityGroup.vpcId || null;

        var existingSecurityGroupNames = [];
        var availableSecurityGroups = [];

        if (regions.length > 1) {
          this.disableCrossAccount();
        }

        regions.forEach(region => {
          var regionalVpcId = null;
          if (vpcId) {
            var [baseVpc] = this.vpcs.filter(vpc => vpc.id === vpcId),
                [regionalVpc] = this.vpcs.filter(vpc => vpc.account === account && vpc.region === region && vpc.name === baseVpc.name);
            regionalVpcId = regionalVpc ? regionalVpc.id : undefined;
          }

          var regionalGroupNames = _.get(this.allSecurityGroups, [account, 'aws', region].join('.'), [])
            .filter(sg => sg.vpcId === regionalVpcId)
            .map(sg => sg.name);

          existingSecurityGroupNames = _.uniq(existingSecurityGroupNames.concat(regionalGroupNames));

          if (!availableSecurityGroups.length) {
            availableSecurityGroups = existingSecurityGroupNames;
          } else {
            availableSecurityGroups = _.intersection(availableSecurityGroups, regionalGroupNames);
          }
        });
        if (regions.length === 1) {
          this.configureAvailableVpcs();
        }
        this.availableSecurityGroups = availableSecurityGroups;
        if (availableSecurityGroups.indexOf(this.rule.name) === -1 && !this.rule.existing) {
          this.rule.name = null;
        }
      };

      let addRegionalVpc = (vpc) => {
        let account = vpc.account;
        if (!this.regionalVpcs[account]) {
          this.regionalVpcs[account] = [];
        }
        this.regionalVpcs[account].push({
          id: vpc.id,
          label: vpc.label,
          deprecated: vpc.deprecated,
        });
      };

      let reconcileRuleVpc = (filtered) => {
        if (this.rule.vpcId && !this.rule.existing) {
          if (!this.securityGroup.vpcId) {
            this.rule.vpcId = null;
            this.rule.name = null;
            return;
          }
          let [baseVpc] = filtered.filter(vpc => vpc.id === this.rule.vpcId),
              [regionalVpc] = filtered.filter(vpc => vpc.account === this.rule.accountName && vpc.name === baseVpc.name);
          if (regionalVpc) {
            this.rule.vpcId = regionalVpc.id;
          } else {
            this.rule.vpcId = null;
            this.rule.name = null;
          }
        }
      };

      this.configureAvailableVpcs = () => {
        let region = this.securityGroup.regions[0];
        let filtered = this.vpcs.filter(vpc => vpc.region === region);
        this.regionalVpcs = {};
        filtered.forEach(addRegionalVpc);
        reconcileRuleVpc(filtered);
      };

      this.subscriptions = [
        this.allSecurityGroupsUpdated.subscribe(this.setAvailableSecurityGroups),
        this.coordinatesChanged.subscribe(this.setAvailableSecurityGroups)
      ];

      this.enableCrossAccount = () => {
        this.rule.crossAccountEnabled = true;
        this.rule.accountName = this.securityGroup.credentials;
        this.rule.vpcId = this.securityGroup.vpcId;
      };

      this.disableCrossAccount = () => {
        this.rule.crossAccountEnabled = false;
        this.rule.accountName = undefined;
        this.rule.vpcId = undefined;
      };

      this.$onInit = this.setAvailableSecurityGroups;

      this.$onDestroy = () => {
        this.subscriptions.forEach(subscription => subscription.dispose());
      };
    }
  });
