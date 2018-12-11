import * as React from 'react';
import { $q } from 'ngimport';
import { flatten, isEqual, map, uniq, xor } from 'lodash';

import { createFakeReactSyntheticEvent } from 'core/presentation/forms/inputs/utils';
import { IFormInputProps } from 'core/presentation';

import { AccountService, IAccount } from './AccountService';

export interface IAccountSelectInputProps extends IFormInputProps {
  accounts: IAccount[] | string[];
  provider: string;
  loading?: boolean;
  readOnly?: boolean;
}

export interface IAccountSelectInputState {
  mergedAccounts: string[];
  primaryAccounts: string[];
  secondaryAccounts: string[];
}

const isExpression = (account: string) => !!account && account.includes('${');

export class AccountSelectInput extends React.Component<IAccountSelectInputProps, IAccountSelectInputState> {
  public state: IAccountSelectInputState = {
    mergedAccounts: [],
    primaryAccounts: [],
    secondaryAccounts: [],
  };

  private groupAccounts = (accounts: IAccount[] | string[]) => {
    const { name, value, onChange, provider } = this.props;

    if (!accounts || !accounts.length) {
      return;
    }

    if (isExpression(value)) {
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

      if (!mergedAccounts.includes(value)) {
        onChange(createFakeReactSyntheticEvent({ value: '', name }));
      }

      this.setState({ mergedAccounts, primaryAccounts, secondaryAccounts });
    });
  };

  public componentDidMount() {
    this.groupAccounts(this.props.accounts);
  }

  public componentWillReceiveProps(nextProps: IAccountSelectInputProps) {
    if (!isEqual(nextProps.accounts, this.props.accounts)) {
      this.groupAccounts(nextProps.accounts);
    }
  }

  public render() {
    const { value, onChange, readOnly, ...otherProps } = this.props;
    const { primaryAccounts, secondaryAccounts } = this.state;

    if (isExpression(value)) {
      return (
        <div className="sm-control-field">
          <span>
            Resolved at runtime from expression: <code>{value}</code>
          </span>
        </div>
      );
    }

    if (readOnly) {
      return (
        <div>
          <p className="form-control-static">{value}</p>
        </div>
      );
    }

    const showSeparator = primaryAccounts.length > 0 && secondaryAccounts.length > 0;

    return (
      <div>
        <select className="form-control input-sm" value={value} onChange={onChange} required={true} {...otherProps}>
          <option value="" disabled={true}>
            Select...
          </option>

          {primaryAccounts.map(account => (
            <option key={account} value={account}>
              {account}
            </option>
          ))}

          {showSeparator && <option disabled={true}>---------------</option>}

          {secondaryAccounts.map(account => (
            <option key={account} value={account}>
              {account}
            </option>
          ))}
        </select>
      </div>
    );
  }
}
