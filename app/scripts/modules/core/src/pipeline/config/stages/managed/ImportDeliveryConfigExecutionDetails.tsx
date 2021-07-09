import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { SETTINGS } from '../../../../config';
import { StageFailureMessage } from '../../../details';
import { IGitTrigger } from '../../../../domain';
import { CollapsibleSection, Markdown } from '../../../../presentation';

const NOT_FOUND = 'Not found';

const ERROR_MESSAGE_MAP = {
  missing_property: 'The following property is missing:',
  invalid_type: 'The type of the following property is invalid:',
  invalid_format: 'The format of the following property is invalid:',
  invalid_value: 'The value of the following property is invalid:',
};

interface IDeliveryConfigImportErrorDetails {
  error: 'missing_property' | 'invalid_type' | 'invalid_format' | 'invalid_value';
  message: string;
  pathExpression: string;
}

interface IDeliveryConfigImportError {
  message: string;
  details?: IDeliveryConfigImportErrorDetails;
}

const CustomErrorMessage = ({ summary, debugDetails }: { summary: string; debugDetails?: string }) => {
  return (
    <div>
      <div className="alert alert-danger">
        <b>There was an error importing your delivery config file.</b>
        <br />
        <Markdown message={summary} style={{ wordBreak: 'break-word' }} />
        <br />
        {debugDetails && (
          <CollapsibleSection heading={({ chevron }) => <span>{chevron} Debug Details</span>}>
            <pre style={{ whiteSpace: 'pre-wrap' }}>{debugDetails}</pre>
          </CollapsibleSection>
        )}
      </div>
    </div>
  );
};

function extractCustomError(error: IDeliveryConfigImportError): { summary: string; debugDetails?: string } {
  if (!error) {
    return null;
  }

  if (!error.details) {
    return { summary: error.message };
  }

  // Replace dots with slashes for paths because it feels more familiar. Also ditch the very first slash/dot as it just adds noise.
  const pathExpression = error.details.pathExpression.substring(1).replace(/\./g, '/');

  const errorMessage = ERROR_MESSAGE_MAP[error.details.error]
    ? `${ERROR_MESSAGE_MAP[error.details.error]}<br/> \`${pathExpression}\``
    : 'Unknown error';

  return { summary: errorMessage, debugDetails: error.details.message };
}

export function ImportDeliveryConfigExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const trigger = props.execution.trigger as IGitTrigger;
  const manifestPath =
    SETTINGS.managedDelivery?.manifestBasePath +
    '/' +
    (stage.context.manifest ?? SETTINGS.managedDelivery?.defaultManifest);

  const customError = extractCustomError(stage.context.error as IDeliveryConfigImportError);

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

      {customError ? (
        <CustomErrorMessage summary={customError.summary} debugDetails={customError.debugDetails} />
      ) : (
        <StageFailureMessage stage={stage} />
      )}
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace ImportDeliveryConfigExecutionDetails {
  export const title = 'Configuration';
}
