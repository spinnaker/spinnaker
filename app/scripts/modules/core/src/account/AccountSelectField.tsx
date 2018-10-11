import * as React from 'react';
import { $q } from 'ngimport';
import { flatten, has, isEqual, map, uniq, xor } from 'lodash';

import { IAccount } from 'core/account';
import { AccountService } from 'core/account/AccountService';

export interface IAccountSelectFieldProps {
  accounts: IAccount[] | string[];
  component: { [key: string]: any };
  field: string;
  provider: string;
  loading?: boolean;
  onChange?: (account: string) => void;
  labelColumns?: number;
  readOnly?: boolean;
}

export interface IAccountSelectFieldState {
  accountContainsExpression: boolean;
  mergedAccounts: string[];
  primaryAccounts: string[];
  secondaryAccounts: string[];
}

const isExpression = (account: string) => !!account && account.includes('${');

export class AccountSelectField extends React.Component<IAccountSelectFieldProps, IAccountSelectFieldState> {
  public state: IAccountSelectFieldState = {
    accountContainsExpression: false,
    mergedAccounts: [],
    primaryAccounts: [],
    secondaryAccounts: [],
  };

  private groupAccounts = (accounts: IAccount[] | string[]) => {
    const { component, field, onChange, provider } = this.props;

    if (!accounts || !accounts.length) {
      return;
    }
    if (has(component, field) && isExpression(component[field])) {
      this.setState({ accountContainsExpression: true });
      return;
    }

    const accountsAreObjects = Boolean((accounts[0] as IAccount).name);
    let getAccountDetails = $q.when([]);
    if (provider) {
      if (accountsAreObjects) {
        const providers = uniq(map(accounts as IAccount[], 'type'));
        getAccountDetails = $q
          .all(providers.map(p => AccountService.getAllAccountDetailsForProvider(p)))
          .then(details => flatten(details));
      } else {
        getAccountDetails = AccountService.getAllAccountDetailsForProvider(provider);
      }
    }

    getAccountDetails.then(details => {
      const accountNames: string[] = accountsAreObjects ? map(accounts as IAccount[], 'name') : (accounts as any);
      let mergedAccounts = accountNames;
      let primaryAccounts: string[] = [];
      let secondaryAccounts: string[] = [];

      if (accountNames) {
        primaryAccounts = accountNames.sort();
      }
      if (accountNames && accountNames.length && details.length) {
        primaryAccounts = accountNames
          .filter(account => {
            return details.some(detail => detail.name === account && detail.primaryAccount);
          })
          .sort();
        secondaryAccounts = xor(accountNames, primaryAccounts).sort();
        mergedAccounts = flatten([primaryAccounts, secondaryAccounts]);
      }

      if (component) {
        if (!mergedAccounts.includes(component[field])) {
          onChange('');
        }
      }

      this.setState({ mergedAccounts, primaryAccounts, secondaryAccounts });
    });
  };

  public componentWillReceiveProps(nextProps: IAccountSelectFieldProps) {
    if (!isEqual(nextProps.accounts, this.props.accounts)) {
      this.groupAccounts(nextProps.accounts);
    }
  }

  public render() {
    const { component, field, onChange, readOnly } = this.props;
    const { accountContainsExpression, primaryAccounts, secondaryAccounts } = this.state;

    const value = component[field];

    if (accountContainsExpression) {
      return (
        <div className="sm-control-field">
          <span>
            Resolved at runtime from expression: <code>{value}</code>
          </span>
        </div>
      );
    }

    return (
      <div>
        {!readOnly && (
          <select
            className="form-control input-sm"
            value={value}
            onChange={e => onChange(e.target.value)}
            required={true}
          >
            <option value="" disabled={true}>
              Select...
            </option>
            {primaryAccounts.map(account => (
              <option key={account} value={account}>
                {account}
              </option>
            ))}
            {primaryAccounts.length > 0 &&
              secondaryAccounts.length > 0 && <option disabled={true}>---------------</option>}
            {secondaryAccounts.map(account => (
              <option key={account} value={account}>
                {account}
              </option>
            ))}
          </select>
        )}

        {readOnly && <p className="form-control-static">{value}</p>}
      </div>
    );
  }
}
