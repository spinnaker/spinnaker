import { find, get, map } from 'lodash';
import React from 'react';
import Select, { Creatable, Option } from 'react-select';

import { IAccountDetails } from '@spinnaker/core';

export interface INamespaceSelectorProps {
  onChange: (namespace: string) => void;
  accounts: IAccountDetails[];
  selectedAccount: string;
  selectedNamespace: string;
  createable?: boolean;
}

export class NamespaceSelector extends React.Component<INamespaceSelectorProps> {
  public defaultProps = { createable: false };

  private getNamespaceOptions(): Array<Option<string>> {
    const { accounts, selectedAccount, selectedNamespace } = this.props;
    const selectedAccountDetails = find(accounts, (a) => a.name === selectedAccount);
    const namespaces = get(selectedAccountDetails, 'namespaces', []);
    const options = map(namespaces, (n) => ({ label: n, value: n }));
    // only create a value for selectedNamespace if it contains a SPeL expression
    if (this.props.createable && selectedNamespace.includes('${')) {
      options.push({ label: selectedNamespace, value: selectedNamespace });
    }
    return options;
  }

  public render() {
    const componentProps = {
      clearable: false,
      options: this.getNamespaceOptions(),
      value: this.props.selectedNamespace,
      onChange: (option: Option) => this.props.onChange(option.value.toString()),
    };
    return this.props.createable ? <Creatable {...componentProps} /> : <Select {...componentProps} />;
  }
}
