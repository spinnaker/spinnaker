import { module } from 'angular';
import * as React from 'react';
import { react2angular } from 'react2angular';

import { IArtifactAccount } from 'core/account';
import { TetheredSelect } from 'core/presentation';

export interface IArtifactAccountSelectorProps {
  accounts: IArtifactAccount[];
  selected: IArtifactAccount;
  onChange: (_: IArtifactAccount) => void;
  className?: string;
}

export interface IArtifactAccountSelectorOption {
  value: string;
  label: string;
}

export class ArtifactAccountSelector extends React.Component<IArtifactAccountSelectorProps> {
  constructor(props: IArtifactAccountSelectorProps) {
    super(props);
  }

  private onChange = (option: IArtifactAccountSelectorOption) => {
    const account = this.props.accounts.find(a => a.name === option.value);
    this.props.onChange(account);
  };

  public render() {
    const options = this.props.accounts.map(a => ({ value: a.name, label: a.name }));
    const value = this.props.selected ? { value: this.props.selected.name, label: this.props.selected.name } : null;
    return (
      <TetheredSelect
        className={this.props.className || ''}
        options={options}
        value={value}
        onChange={this.onChange}
        clearable={false}
      />
    );
  }
}

export const ARTIFACT_ACCOUNT_SELECTOR_COMPONENT_REACT = 'spinnaker.core.artifacts.account.selector.react';
module(ARTIFACT_ACCOUNT_SELECTOR_COMPONENT_REACT, []).component(
  'artifactAccountSelectorReact',
  react2angular(ArtifactAccountSelector, ['accounts', 'className', 'onChange', 'selected']),
);
