import React from 'react';
import { ExecutionDetailsSection, StageFailureMessage } from '@spinnaker/core';

export function EcsFindImageFromTagsExecutionDetails(props: any) {
  const { stage } = props;
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-12">
          <dl className="dl-narrow dl-horizontal">
            <dt>Provider</dt>
            <dd>ECS</dd>
          </dl>
        </div>
      </div>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      {stage.context?.amiDetails && (
        <div className="row">
          <div className="col-md-12">
            <div className="well alert alert-info">
              <h4>Results</h4>
              {stage.context.amiDetails.map((image: any, index: number) => (
                <dl key={index} className="dl-narrow dl-horizontal">
                  <dt>Region</dt>
                  <dd>{image.region}</dd>
                  <dt>Image ID</dt>
                  <dd>{image.imageId}</dd>
                  <dt>Unique ID</dt>
                  <dd>{image.imageName}</dd>
                </dl>
              ))}
            </div>
          </div>
        </div>
      )}
    </ExecutionDetailsSection>
  );
}

EcsFindImageFromTagsExecutionDetails.title = 'findImageConfig';
