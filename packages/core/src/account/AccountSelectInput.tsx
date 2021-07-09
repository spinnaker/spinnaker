import { flatten, isEqual, map, uniq, xor } from 'lodash';
import { $q } from 'ngimport';
import React from 'react';
import { Option } from 'react-select';

import { AccountService, IAccount } from './AccountService';
import { IFormInputProps } from '../presentation/forms/inputs';
import { ReactSelectInput } from '../presentation/forms/inputs/ReactSelectInput';
import { SelectInput } from '../presentation/forms/inputs/SelectInput';
import { createFakeReactSyntheticEvent } from '../presentation/forms/inputs/utils';

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
        .all(providers.map((p) => AccountService.getAllAccountDetailsForProvider(p)))
        .then((details) => flatten(details));
    }

    getAccountDetails.then((details) => {
      const accountNames: string[] = accountsAreObjects ? map(accounts as IAccount[], 'name') : (accounts as any);
      let mergedAccounts = accountNames;
      let primaryAccounts: string[] = [];
      let secondaryAccounts: string[] = [];

      if (accountNames) {
        primaryAccounts = accountNames.sort();
      }
      if (accountNames && accountNames.length && details.length) {
        primaryAccounts = accountNames
          .filter((account) => {
            return details.some((detail) => detail.name === account && detail.primaryAccount);
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
    const { value, readOnly } = this.props;
    const { mergedAccounts } = this.state;

    const { primaryAccounts, secondaryAccounts } = this.state;
    const showSeparator = primaryAccounts.length > 0 && secondaryAccounts.length > 0;
    const options: Array<Option<string>> = [{ label: 'Select...', value: '', disabled: true }];
    options.push(...primaryAccounts.map((a) => ({ label: a, value: a })));
    if (showSeparator) {
      options.push({ label: '---------------', value: '-', disabled: true });
    }
    options.push(...secondaryAccounts.map((a) => ({ label: a, value: a })));

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

    const { renderFilterableSelectThreshold, ...otherProps } = this.props;
    const useSimpleSelect = mergedAccounts.length < renderFilterableSelectThreshold;

    if (useSimpleSelect) {
      // When this select is used in Angular, the event is accessed in a $timeout, and React will have
      // re-rendered the input, setting its value (the event.target.value) back to the previous value
      // This can go away once we're out of Angular land.
      const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        e.persist();
        this.props.onChange(e);
      };

      return (
        <SelectInput
          {...otherProps}
          inputClassName="form-control input-sm"
          onChange={handleChange}
          required={true}
          options={options}
        />
      );
    } else {
      return (
        <ReactSelectInput
          {...otherProps}
          clearable={false}
          required={true}
          options={options}
          mode="PLAIN"
          menuContainerStyle={{ minWidth: '150px' }}
        />
      );
    }
  }
}
