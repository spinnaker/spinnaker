'use strict';

const angular = require('angular');
import { has } from 'lodash';
import { AccountService } from 'core/account/AccountService';

module.exports = angular
  .module('spinnaker.core.account.accountSelectField.directive', [])
  .directive('accountSelectField', function() {
    return {
      restrict: 'E',
      templateUrl: require('./accountSelectField.directive.html'),
      controller: 'AccountSelectFieldCtrl',
      controllerAs: 'vm',
      scope: {},
      bindToController: {
        accounts: '=',
        component: '=',
        field: '@',
        provider: '=',
        loading: '=',
        onChange: '&',
        labelColumns: '@',
        readOnly: '=',
        multiselect: '=',
      },
    };
  })
  .controller('AccountSelectFieldCtrl', function($scope, $q) {
    this.mergedAccounts = [];

    const isExpression = account => !!account && account.includes('${');

    const groupAccounts = accounts => {
      if (!accounts || !accounts.length) {
        return;
      }
      // TODO(dpeach): I don't see any usages of the multiselect option in Deck,
      // so I'm not handling cases where users select a list of accounts.
      if (
        !this.multiselect &&
        has(this.component, this.field) &&
        !Array.isArray(this.component[this.field]) &&
        isExpression(this.component[this.field])
      ) {
        this.accountContainsExpression = true;
        return;
      }
      this.accountContainsExpression = false;

      const accountsAreObjects = accounts[0].name;
      let getAccountDetails = this.provider
        ? AccountService.getAllAccountDetailsForProvider(this.provider)
        : $q.when([]);
      if (!this.provider && accountsAreObjects) {
        const providers = _.uniq(_.map(accounts, 'type'));
        getAccountDetails = $q
          .all(providers.map(provider => AccountService.getAllAccountDetailsForProvider(provider)))
          .then(details => _.flatten(details));
      }

      getAccountDetails.then(details => {
        const accountNames = accountsAreObjects ? _.map(accounts, 'name') : accounts;
        this.mergedAccounts = accountNames;
        if (accountNames) {
          this.primaryAccounts = accountNames.sort();
        }
        if (accountNames && accountNames.length && details.length) {
          this.primaryAccounts = accountNames
            .filter(account => {
              return details.some(detail => detail.name === account && detail.primaryAccount);
            })
            .sort();
          this.secondaryAccounts = _.xor(accountNames, this.primaryAccounts).sort();
          this.mergedAccounts = _.flatten([this.primaryAccounts, this.secondaryAccounts]);
        }

        if (this.component) {
          const mergedAccounts = this.mergedAccounts || [];
          const component = _.flatten([this.component[this.field]]);

          if (_.intersection(mergedAccounts, component).length !== component.length) {
            this.component[this.field] = null;
          }
        }
      });
    };

    this.groupBy = account => {
      if (this.secondaryAccounts && this.secondaryAccounts.includes(account)) {
        return '---------------';
      }

      if (this.primaryAccounts.includes(account)) {
        return undefined;
      }
    };

    $scope.$watch(() => this.accounts, groupAccounts);
  });
