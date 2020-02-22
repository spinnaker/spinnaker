import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps, StageFailureMessage } from 'core/pipeline';
import { IGitTrigger } from 'core/domain';
import { SETTINGS } from 'core/config';

const NOT_FOUND = 'Not found';

export function ImportDeliveryConfigExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const trigger = props.execution.trigger as IGitTrigger;
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
            <dd>{trigger.source ?? NOT_FOUND}</dd>
            <dt>Project</dt>
            <dd>{trigger.project ?? NOT_FOUND}</dd>
            <dt>Repository</dt>
            <dd>{trigger.slug ?? NOT_FOUND}</dd>
            <dt>Manifest Path</dt>
            <dd>{manifestPath ?? NOT_FOUND}</dd>
            <dt>Branch</dt>
            <dd>{trigger.branch ?? NOT_FOUND}</dd>
            <dt>Commit</dt>
            <dd>{trigger.hash?.substring(0, 7) ?? NOT_FOUND}</dd>
          </dl>
        </div>
      </div>

      <StageFailureMessage stage={stage} message={stage.context.error || stage.failureMessage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace ImportDeliveryConfigExecutionDetails {
  export const title = 'Configuration';
}
