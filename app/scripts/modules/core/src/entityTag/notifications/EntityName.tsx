import * as React from 'react';

import { IEntityTags } from 'core/domain';
import { NgReact } from 'core/reactShims';

export interface IEntityNameProps {
  tag: IEntityTags;
}

/** Renders an entity name and its account and region */
export class EntityName extends React.Component<IEntityNameProps, void> {
  public render() {
    const entityRef = this.props.tag.entityRef;
    const { AccountTag } = NgReact;
    if (!entityRef.account && !entityRef.region) {
      return null;
    }

    return (
      <div className="entityname">
        <span className="entityref"><strong>{entityRef.entityId}</strong></span>
        <span className="account-region">
          {' ( '}
          {entityRef.account && <AccountTag account={entityRef.account}/>}
          {' '}
          <span>{entityRef.region}</span>
          {' ) '}
        </span>
      </div>
    );
  };
}
