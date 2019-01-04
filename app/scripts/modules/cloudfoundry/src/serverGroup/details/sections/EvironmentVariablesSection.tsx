import * as React from 'react';

import { isEmpty, map } from 'lodash';

import { CollapsibleSection } from '@spinnaker/core';

import { ICloudFoundryEnvVar } from 'cloudfoundry/domain';
import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';

export class EvironmentVariablesSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <>
        {!isEmpty(serverGroup.env) && (
          <CollapsibleSection heading="Environment Variables" defaultExpanded={true}>
            <dl className="dl-horizontal dl-flex">
              {map(serverGroup.env, (obj: ICloudFoundryEnvVar, index: number) => {
                return (
                  <div key={index}>
                    <dt>{obj.key}</dt>
                    <dd>{obj.value}</dd>
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
