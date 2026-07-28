import React from 'react';

import { ArtifactIcon } from './ArtifactIcon';
import type { IArtifactAccount } from '../../account';
import { TetheredSelect } from '../../presentation';

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
