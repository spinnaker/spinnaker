import { isEmpty } from 'lodash';
import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';

export class EnvironmentVariablesSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <>
        {!isEmpty(serverGroup.env) && (
          <CollapsibleSection heading="Environment Variables" defaultExpanded={true}>
            <dl className="dl-horizontal dl-narrow">
              {Object.entries(serverGroup.env).map(([k, v], index) => {
                return (
                  <div key={index}>
                    <dt>{k}</dt>
                    <dd>{v}</dd>
                  </div>
                );
              })}
            </dl>
          </CollapsibleSection>
        )}
      </>
    );
  }
}
