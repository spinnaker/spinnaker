import React from 'react';

import { AccountTag } from '../../../../account';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageFailureMessage } from '../../../details';
import { IFindAmiStageContext } from './findAmiStage';

export function FindAmiExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const regions = stage.context && stage.context.regions && stage.context.regions.join(', ');
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-6">
          <dl className="dl-narrow dl-horizontal">
            <dt>Account</dt>
            <dd>
              <AccountTag account={stage.context.credentials} />
            </dd>
            {regions && <dt>Regions</dt>}
            {regions && <dd>{regions}</dd>}
            <dt>Cluster</dt>
            <dd>{stage.context.cluster}</dd>
          </dl>
        </div>
      </div>

      <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />

      {stage.context.amiDetails && (
        <div className="row">
          <div className="col-md-12">
            <div className="well alert alert-info">
              <h4>Results</h4>
              {stage.context.amiDetails.map((image: IFindAmiStageContext) => (
                <dl key={image.imageId} className="dl-narrow dl-horizontal">
                  {image.region && <dt>Region</dt>}
                  {image.region && <dd>{image.region}</dd>}
                  <dt>Image ID</dt>
                  <dd>{image.imageId}</dd>
                  {image.imageName && <dt>Name</dt>}
                  {image.imageName && <dd>{image.imageName}</dd>}
                </dl>
              ))}
            </div>
          </div>
        </div>
      )}
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace FindAmiExecutionDetails {
  export const title = 'findImageConfig';
}
