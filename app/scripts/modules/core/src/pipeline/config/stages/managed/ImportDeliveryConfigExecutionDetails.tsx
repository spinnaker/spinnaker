import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps, StageFailureMessage } from 'core/pipeline';
import { IGitTrigger } from 'core/domain';
import { SETTINGS } from 'core/config';

export function ImportDeliveryConfigExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const trigger = props.execution.trigger as IGitTrigger;
  const errorDetailsAvailable = stage.isFailed && !stage.failureMessage && stage.context.error;
  const manifestPath =
    SETTINGS.managedDelivery?.manifestBasePath +
    '/' +
    (stage.context.manifest ?? SETTINGS.managedDelivery?.defaultManifest);

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-12">
          <dl className="dl-narrow dl-horizontal">
            <dt>SCM</dt>
            <dd>{trigger.source}</dd>
            <dt>Project</dt>
            <dd>{trigger.project}</dd>
            <dt>Repository</dt>
            <dd>{trigger.slug}</dd>
            <dt>Manifest Path</dt>
            <dd>{manifestPath}</dd>
            <dt>Branch</dt>
            <dd>{trigger.branch}</dd>
            <dt>Commit</dt>
            <dd>{trigger.hash.substring(0, 7)}</dd>
          </dl>
        </div>
      </div>

      {errorDetailsAvailable && (
        <div>
          <div className="alert alert-danger">
            Something went wrong during import:
            <pre>${stage.context.error}</pre>
          </div>
        </div>
      )}

      <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace ImportDeliveryConfigExecutionDetails {
  export const title = 'Configuration';
}
