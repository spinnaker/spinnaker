import { get, last, sortBy } from 'lodash';
import React from 'react';

import { AccountService } from '../../../../account';
import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageExecutionLogs, StageFailureMessage } from '../../../details';
import { IJobOwnedPodStatus, IStage } from '../../../../domain';
import { DefaultPodNameProvider } from '../../../../manifest';
import { JobStageExecutionLogs } from '../../../../manifest/stage/JobStageExecutionLogs';
import { IPreconfiguredJobParameter } from './preconfiguredJob.reader';

export interface ITitusExecutionLogsProps {
  stage: IStage;
}

export interface ITitusExecutionLogsState {
  titusUiEndpoint?: string;
}

export class TitusExecutionLogs extends React.Component<ITitusExecutionLogsProps, ITitusExecutionLogsState> {
  private mounted = false;

  constructor(props: IExecutionDetailsSectionProps) {
    super(props);
    this.state = {};
  }

  public componentDidMount() {
    this.mounted = true;
    this.setEndpoint();
  }

  private setEndpoint(): void {
    const { context } = this.props.stage;
    AccountService.getAccountDetails(context.credentials).then((details) => {
      const titusUiEndpoint = details.regions.find((r) => r.name === context.cluster.region).endpoint;
      this.mounted && this.setState({ titusUiEndpoint });
    });
  }

  public render() {
    const artificialStageJustForLogs: any = {};
    const { stage } = this.props;
    const { titusUiEndpoint } = this.state;
    const { context } = stage;
    const { cluster } = context;
    const jobId = cluster ? get(context['deploy.jobs'], cluster.region, [])[0] : null;
    const taskId = get(context, 'jobStatus.completionDetails.taskId');
    if (titusUiEndpoint) {
      artificialStageJustForLogs.context = {
        execution: {
          logs: `${titusUiEndpoint}jobs/${jobId}/tasks/${taskId}/logs`,
        },
      };
      return <StageExecutionLogs stage={artificialStageJustForLogs} />;
    }
    return null;
  }
}

export class PreconfiguredJobExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'preconfiguredJobConfig';

  private mostRecentlyCreatedPodName(podsStatuses: IJobOwnedPodStatus[]): string {
    const sorted = sortBy(podsStatuses, (p: IJobOwnedPodStatus) => p.status.startTime);
    const mostRecent = last(sorted);
    return mostRecent ? mostRecent.name : '';
  }

  private executionLogsComponent(cloudProvider: string) {
    const { stage } = this.props;

    if (cloudProvider === 'kubernetes') {
      const namespace = get<string>(stage, ['context', 'jobStatus', 'location']);
      const deployedJobs = get(this.props.stage, ['context', 'deploy.jobs']);
      const deployedName = get(deployedJobs, namespace, [])[0] || '';
      const externalLink = get<string>(this.props.stage, ['context', 'execution', 'logs']);
      const podName = this.mostRecentlyCreatedPodName(get(stage.context, ['jobStatus', 'pods'], []));
      const podNameProvider = new DefaultPodNameProvider(podName);
      return (
        <div className="well">
          <JobStageExecutionLogs
            deployedName={deployedName}
            account={this.props.stage.context.account}
            application={this.props.application}
            location={namespace}
            externalLink={externalLink}
            podNamesProviders={[podNameProvider]}
          />
        </div>
      );
    } else if (cloudProvider === 'titus') {
      return <TitusExecutionLogs stage={stage} />;
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
