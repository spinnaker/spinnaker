import { get } from 'lodash';
import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageFailureMessage } from '../../../details';
import { RenderOutputFile } from '../../../../presentation/RenderOutputFile';

export function ScriptExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const scriptRanAndFailed =
    stage.isFailed && !stage.failureMessage && get(stage.context, 'buildInfo.result') === 'FAILURE';

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-12">
          <h5>Script Stage Configuration</h5>
          <dl className="dl-narrow dl-horizontal">
            {stage.context.repoUrl && <dt>Repository Url</dt>}
            {stage.context.repoUrl && <dd>{stage.context.repoUrl}</dd>}
            {stage.context.repoBranch && <dt>Repository Branch</dt>}
            {stage.context.repoBranch && <dd>{stage.context.repoBranch}</dd>}
            <dt>Script Path</dt>
            <dd>{stage.context.scriptPath}</dd>
            <dt>Command</dt>
            <dd>{stage.context.command}</dd>
            {stage.context.propertyFile && <dt>Properties</dt>}
            {stage.context.propertyFile && <dd>{stage.context.propertyFile}</dd>}
          </dl>
        </div>
      </div>

      {stage.context.propertyFileContents && (
        <div className="row">
          <div className="col-md-12">
            <h5 style={{ marginBottom: '0px', paddingBottom: '5px' }}>Property File</h5>
            <RenderOutputFile outputFileObject={stage.context.propertyFileContents} />
          </div>
        </div>
      )}

      {!scriptRanAndFailed && stage.context.buildInfo && stage.context.buildInfo.url && (
        <div className="row ng-scope">
          <div className="col-md-12">
            <div className="well alert alert-info">
              <a href={`${stage.context.buildInfo.url}consoleText`} target="_blank">
                Script Results
              </a>
            </div>
          </div>
        </div>
      )}

      {scriptRanAndFailed && (
        <div>
          <div className="alert alert-danger">
            Script execution failed.
            <span>Check </span>
            {stage.context.buildInfo && stage.context.buildInfo.url && (
              <a href={`${stage.context.buildInfo.url}consoleText`} target="_blank">
                the script results
              </a>
            )}
            <span> for details.</span>
          </div>
        </div>
      )}

      <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace ScriptExecutionDetails {
  export const title = 'scriptConfig';
}
