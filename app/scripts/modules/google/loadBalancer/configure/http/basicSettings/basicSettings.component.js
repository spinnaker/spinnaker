'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.basicSettings.component', [
    require('../../../../../core/account/account.service.js'),
    require('../../../../../core/loadBalancer/loadBalancer.read.service.js'),
    require('../../../../../core/utils/lodash.js'),
    require('../../../elSevenUtils.service.js'),
  ])
  .component('gceHttpLoadBalancerBasicSettings', {
    bindings: {
      loadBalancer: '=',
      application: '=',
      isNew: '='
    },
    templateUrl: require('./basicSettings.component.html'),
    controller: function ($scope, accountService, loadBalancerReader, _, elSevenUtils, $q) {

      this.getName = (loadBalancer, applicationName) => {
        let loadBalancerName = [applicationName, (loadBalancer.stack || ''), (loadBalancer.detail || '')].join('-');
        return _.trimRight(loadBalancerName, '-');
      };

      this.updateName = (lb, appName) => {
        lb.name = this.getName(lb, appName);
      };

      if (!this.loadBalancer.name) {
        this.updateName(this.loadBalancer, this.application.name);
      }

      let accountsPromise = accountService
        .listAccounts('gce');

      let loadBalancersKeyedByAccountPromise = loadBalancerReader
        .listLoadBalancers('gce')
        .then((lbs) => {
          return _(lbs)
            .map(lb => lb.accounts)
            .flatten()
            .groupBy('name')
            .mapValues((accounts) => {
              return _(accounts)
                .map(a => a.regions)
                .flatten()
                .filter(region => region.name === elSevenUtils.getElSevenRegion())
                .map(region => region.loadBalancers)
                .flatten()
                .map(lb => lb.name)
                .uniq()
                .valueOf();
            })
            .valueOf();
        });

      $q.all({
        accounts: accountsPromise,
        globalLoadBalancersKeyedByAccount: loadBalancersKeyedByAccountPromise,
      })
      .then(({ accounts, globalLoadBalancersKeyedByAccount }) => {
        // account view setup
        this.accounts = accounts;
        let accountNames = _.pluck(accounts, 'name');
        if (!_.contains(accountNames, _.get(this.loadBalancer, 'credentials.name'))) {
          this.loadBalancer.credentials = _.first(accountNames);
        }

        // name collision detection setup
        this.globalLoadBalancersKeyedByAccount = globalLoadBalancersKeyedByAccount;
        this.updateExistingLoadBalancerNames(this.loadBalancer.credentials);
      });

      this.updateExistingLoadBalancerNames = (account) => {
        this.existingLoadBalancerNames = this.globalLoadBalancersKeyedByAccount[account];
      };
    }
  });
