import * as React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';

export class PackageSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <>
        {serverGroup.droplet && serverGroup.droplet.sourcePackage && (
          <CollapsibleSection heading="Package" defaultExpanded={true}>
            <dl className="dl-horizontal dl-flex">
              {serverGroup.ciBuild && serverGroup.ciBuild.version && (
                <div>
                  <dt>Version</dt>
                  <dd>{serverGroup.ciBuild.version}</dd>
                </div>
              )}
              {serverGroup.ciBuild && serverGroup.ciBuild.jobName && (
                <div>
                  <dt>Job</dt>
                  <dd>{serverGroup.ciBuild.jobName}</dd>
                </div>
              )}
              {serverGroup.ciBuild && serverGroup.ciBuild.jobNumber && (
                <div>
                  <dt>Build</dt>
                  {serverGroup.ciBuild.jobUrl ? (
                    <dd>
                      <a target="_blank" href={serverGroup.ciBuild.jobUrl}>
                        {serverGroup.ciBuild.jobNumber}
                      </a>
                    </dd>
                  ) : (
                    <dd>{serverGroup.ciBuild.jobNumber}</dd>
                  )}
                </div>
              )}
              <dt>Checksum</dt>
              <dd>{serverGroup.droplet.sourcePackage.checksum}</dd>
            </dl>
          </CollapsibleSection>
        )}
      </>
    );
  }
}
