import React from 'react';

import { AccountTag } from '../../account';
import { IEntityTags } from '../../domain';

export interface IEntityNameProps {
  tag: IEntityTags;
}

/** Renders an entity name and its account and region */
export class EntityName extends React.Component<IEntityNameProps> {
  public render() {
    const entityRef = this.props.tag.entityRef;
    if (!entityRef.account && !entityRef.region) {
      return null;
    }

    return (
      <div className="entityname">
        <span className="entityref">
          <strong>{entityRef.entityId}</strong>
        </span>
        <span className="account-region">
          {' ( '}
          {entityRef.account && <AccountTag account={entityRef.account} />} <span>{entityRef.region}</span>
          {' ) '}
        </span>
      </div>
    );
  }
}
