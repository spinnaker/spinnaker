import React from 'react';

import type { IExecutionDetailsSectionProps, IExecutionStage } from '@spinnaker/core';
import { ExecutionDetailsSection, StageFailureMessage } from '@spinnaker/core';

import type { ILabelSelectors, IManifestNamesByNamespace } from './ManifestCoordinates';
import { ManifestCoordinates } from './ManifestCoordinates';

interface IManifestExecutionDetailsProps extends IExecutionDetailsSectionProps {
  context: {
    account: string;
    manifestName: string;
    location: string;
    cluster: string;
    criteria: string;
    labelSelectors: ILabelSelectors;
    manifestNamesByNamespace: IManifestNamesByNamespace;
  };
}

// extraSections lets a specific stage append content below the coordinates list without changing what other manifest stages show.
export const manifestExecutionDetails = (
  stageName: string,
  extraSections?: (stage: IExecutionStage) => React.ReactNode,
) => {
  return class ManifestExecutionDetails extends React.Component<IManifestExecutionDetailsProps> {
    public static title = stageName;

    public render() {
      const { stage, current, name } = this.props;
      const {
        account,
        manifestName,
        location,
        cluster,
        criteria,
        labelSelectors,
        manifestNamesByNamespace,
      } = stage.context;
      return (
        <ExecutionDetailsSection name={name} current={current}>
          <div className="row">
            <div className="col-md-9">
              <dl className="dl-narrow dl-horizontal">
                <ManifestCoordinates
                  account={account}
                  manifestName={manifestName}
                  location={location}
                  cluster={cluster}
                  criteria={criteria}
                  labelSelectors={labelSelectors}
                  manifestNamesByNamespace={manifestNamesByNamespace}
                />
              </dl>
              {extraSections && extraSections(stage)}
            </div>
          </div>
          <StageFailureMessage stage={stage} message={stage.failureMessage} />
        </ExecutionDetailsSection>
      );
    }
  };
};
