import { module } from 'angular';
import React from 'react';
import { react2angular } from 'react2angular';

import { ArtifactIcon } from './ArtifactIcon';
import { IArtifactAccount } from '../../account';
import { TetheredSelect } from '../../presentation';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export interface IArtifactAccountSelectorProps {
  accounts: IArtifactAccount[];
  selected: IArtifactAccount;
  onChange: (account: IArtifactAccount) => void;
  className?: string;
}

export class ArtifactAccountSelector extends React.Component<IArtifactAccountSelectorProps> {
  constructor(props: IArtifactAccountSelectorProps) {
    super(props);
  }

  private renderOption = (account: IArtifactAccount) => {
    return (
      <span>
        <ArtifactIcon type={account.types[0]} width="16" height="16" />
        {account.name}
      </span>
    );
  };

  public render() {
    return (
      <TetheredSelect
        className={this.props.className || ''}
        options={this.props.accounts}
        value={this.props.selected}
        onChange={this.props.onChange}
        optionRenderer={this.renderOption}
        valueRenderer={this.renderOption}
        clearable={false}
        valueKey="name"
      />
    );
  }
}

export const ARTIFACT_ACCOUNT_SELECTOR_COMPONENT_REACT = 'spinnaker.core.artifacts.account.selector.react';
module(ARTIFACT_ACCOUNT_SELECTOR_COMPONENT_REACT, []).component(
  'artifactAccountSelectorReact',
  react2angular(withErrorBoundary(ArtifactAccountSelector, 'artifactAccountSelectorReact'), [
    'accounts',
    'className',
    'onChange',
    'selected',
  ]),
);
