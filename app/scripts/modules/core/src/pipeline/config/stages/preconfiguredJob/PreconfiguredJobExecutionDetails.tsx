import * as React from 'react';

import {
  IExecutionDetailsSectionProps,
  ExecutionDetailsSection,
  StageExecutionLogs,
  StageFailureMessage,
} from 'core/pipeline';
import { IPreconfiguredJobParameter } from './preconfiguredJobStage';
import { JobStageExecutionLogs } from 'core/manifest/stage/JobStageExecutionLogs';
import { get } from 'lodash';

export class PreconfiguredJobExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'preconfiguredJobConfig';

  private executionLogsComponent(cloudProvider: string) {
    const { stage } = this.props;

    if (cloudProvider === 'kubernetes') {
      const manifest = get(stage, ['context', 'manifest'], null);
      const namespace = get(manifest, ['metadata', 'namespace']);
      const deployedJobs = get(this.props.stage, ['context', 'deploy.jobs']);
      const deployedName = get(deployedJobs, namespace, [])[0] || '';
      return (
        <div className="well">
          <JobStageExecutionLogs
            manifest={manifest}
            deployedName={deployedName}
            account={this.props.stage.context.account}
            application={this.props.application}
          />
        </div>
      );
    }

    return <StageExecutionLogs stage={stage} />;
  }

  public render() {
    const { stage, name, current } = this.props;
    const { cloudProvider } = stage.context;

    const parameters =
      stage.context.preconfiguredJobParameters && stage.context.parameters ? (
        <div>
          <dl className="dl-horizontal">
            {stage.context.preconfiguredJobParameters.map((parameter: IPreconfiguredJobParameter) => (
              <React.Fragment>
                <dt>{parameter.label}</dt>
                <dd>{stage.context.parameters[parameter.name]}</dd>
              </React.Fragment>
            ))}
          </dl>
        </div>
      ) : (
        <div>No details provided.</div>
      );

    const logsComponent = this.executionLogsComponent(cloudProvider);

    return (
      <ExecutionDetailsSection name={name} current={current}>
        {parameters}
        <StageFailureMessage stage={stage} message={stage.failureMessage} />
        {logsComponent}
      </ExecutionDetailsSection>
    );
  }
}
