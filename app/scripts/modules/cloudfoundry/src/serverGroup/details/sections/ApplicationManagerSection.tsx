import * as React from 'react';

import { CollapsibleSection } from '@spinnaker/core';
import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';

export class ApplicationManagerSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <>
        {serverGroup.appsManagerUri && (
          <CollapsibleSection heading="Apps Manager" defaultExpanded={true}>
            <div>
              <a href={serverGroup.appsManagerUri} target="_blank">
                Apps Manager
              </a>
            </div>
          </CollapsibleSection>
        )}
      </>
    );
  }
}
