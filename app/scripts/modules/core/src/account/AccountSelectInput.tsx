import * as React from 'react';
import { $q } from 'ngimport';
import { flatten, isEqual, map, uniq, xor } from 'lodash';
import Select, { Option } from 'react-select';

import { createFakeReactSyntheticEvent } from 'core/presentation/forms/inputs/utils';
import { IFormInputProps } from 'core/presentation';

import { AccountService, IAccount } from './AccountService';

export interface IAccountSelectInputProps extends IFormInputProps {
  accounts: IAccount[] | string[];
  provider: string;
  readOnly?: boolean;
  renderFilterableSelectThreshold?: number;
}

export interface IAccountSelectInputState {
  mergedAccounts: string[];
  primaryAccounts: string[];
  secondaryAccounts: string[];
}

const isExpression = (account: string) => !!account && account.includes('${');

export class AccountSelectInput extends React.Component<IAccountSelectInputProps, IAccountSelectInputState> {
  public static defaultProps = {
    renderFilterableSelectThreshold: 6,
  };

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
      getAccountDetails = AccountService.getAllAccountDetailsForProvider(provider);
    }
    if (!provider && accountsAreObjects) {
      const providers = uniq(map(accounts as IAccount[], 'type'));
      getAccountDetails = $q
        .all(providers.map(p => AccountService.getAllAccountDetailsForProvider(p)))
        .then(details => flatten(details));
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

  private SimpleSelect = () => {
    const { value, onChange, ...otherProps } = this.props;
    delete otherProps.renderFilterableSelectThreshold; // not for DOM consumption
    const { primaryAccounts, secondaryAccounts } = this.state;
    const showSeparator = primaryAccounts.length > 0 && secondaryAccounts.length > 0;
    // When this select is used in Angular, the event is accessed in a $timeout, and React will have
    // re-rendered the input, setting its value (the event.target.value) back to the previous value
    // This can go away once we're out of Angular land.
    const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
      onChange(createFakeReactSyntheticEvent({ value: e.target.value }));
    };
    return (
      <div>
        <select className="form-control input-sm" value={value} onChange={handleChange} required={true} {...otherProps}>
          <option value="" disabled={true}>
            Select...
          </option>

          {primaryAccounts.map(account => (
            <option key={account} value={account}>
              {account}
            </option>
          ))}

          {showSeparator && (
            <option value="-" disabled={true}>
              ---------------
            </option>
          )}

          {secondaryAccounts.map(account => (
            <option key={account} value={account}>
              {account}
            </option>
          ))}
        </select>
      </div>
    );
  };

  private FilterableSelect = () => {
    const { value, onChange, ...otherProps } = this.props;
    delete otherProps.renderFilterableSelectThreshold; // not for DOM consumption
    const { primaryAccounts, secondaryAccounts } = this.state;
    const showSeparator = primaryAccounts.length > 0 && secondaryAccounts.length > 0;
    const options: Option[] = primaryAccounts.map(a => ({ label: a, value: a }));
    if (showSeparator) {
      options.push({ label: '---------------', value: '-', disabled: true });
    }
    options.push(...secondaryAccounts.map(a => ({ label: a, value: a })));
    return (
      <Select
        className="form-control input-sm"
        options={options}
        clearable={false}
        value={value}
        onChange={(option: Option<string>) => onChange(createFakeReactSyntheticEvent({ value: option.value }))}
        required={true}
        {...otherProps}
      />
    );
  };

  public render() {
    const { value, readOnly, renderFilterableSelectThreshold } = this.props;
    const { mergedAccounts } = this.state;
    const { FilterableSelect, SimpleSelect } = this;

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

    const useSimpleSelect = mergedAccounts.length < renderFilterableSelectThreshold;

    if (useSimpleSelect) {
      return <SimpleSelect />;
    } else {
      return <FilterableSelect />;
    }
  }
}
