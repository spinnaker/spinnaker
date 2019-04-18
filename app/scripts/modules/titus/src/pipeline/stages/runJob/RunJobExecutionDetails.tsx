import * as React from 'react';
import { get } from 'lodash';

import {
  AccountService,
  AccountTag,
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  StageFailureMessage,
} from '@spinnaker/core';

export interface IRunJobExecutionDetailsState {
  titusUiEndpoint?: string;
}

export class RunJobExecutionDetails extends React.Component<
  IExecutionDetailsSectionProps,
  IRunJobExecutionDetailsState
> {
  public static title = 'runJobConfig';
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
    AccountService.getAccountDetails(context.credentials).then(details => {
      const titusUiEndpoint = details.regions.find(r => r.name === context.cluster.region).endpoint;
      this.mounted && this.setState({ titusUiEndpoint });
    });
  }

  public componentWillUnmount() {
    this.mounted = false;
  }

  public componentWillReceiveProps() {
    this.setEndpoint();
  }

  public render() {
    const { stage, current, name } = this.props;
    const { titusUiEndpoint } = this.state;
    const { context } = stage;
    const { cluster } = context;
    const { resources, env } = cluster;
    const jobId = cluster ? get(context['deploy.jobs'], cluster.region, [])[0] : null;
    const taskId = get(context, 'jobStatus.completionDetails.taskId');

    const renderProperty = (entry: string) => {
      const linkPattern = /^https?\:\/\/([^\s])*$/;
      return linkPattern.test(entry) ? (
        <a href={entry} target="_blank">
          {entry}
        </a>
      ) : (
        <span>{entry}</span>
      );
    };

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-9">
            <dl className="dl-narrow dl-horizontal">
              <dt>Account</dt>
              <dd>
                <AccountTag account={context.credentials} />
              </dd>
              {cluster && (
                <>
                  <dt>Image</dt>
                  <dd>{cluster.imageId}</dd>
                  {cluster.entryPoint && (
                    <>
                      <dt>Entrypoint</dt>
                      <dd>{cluster.entryPoint}</dd>
                    </>
                  )}
                </>
              )}
              {jobId && (
                <>
                  <dt>Titus Job Id</dt>
                  <dd>
                    <a target="_blank" href={`${titusUiEndpoint}jobs/${jobId}`}>
                      {jobId}
                    </a>
                  </dd>
                </>
              )}
              {taskId && (
                <>
                  <dt>Titus Task Id</dt>
                  <dd>
                    <a target="_blank" href={`${titusUiEndpoint}jobs/${jobId}/tasks/${taskId}`}>
                      {taskId}
                    </a>
                  </dd>
                </>
              )}
              {resources && Object.keys(resources) && (
                <>
                  <dt>Resources</dt>
                  <dd>
                    <ul className="nostyle">
                      {Object.keys(resources).map(key => (
                        <li>
                          {key}: {resources[key]}
                        </li>
                      ))}
                    </ul>
                  </dd>
                </>
              )}
            </dl>
          </div>
        </div>
        {env && Object.keys(env) && (
          <div className="row">
            <div className="col-md-12">
              <h5 style={{ marginBottom: 0, paddingBottom: '5px' }}>Environment Variables</h5>
              <dl>
                {Object.keys(env).map(key => (
                  <>
                    <dt>{key}</dt>
                    <dd>{env[key]}</dd>
                  </>
                ))}
              </dl>
            </div>
          </div>
        )}
        {context.propertyFileContents && (
          <div className="row">
            <div className="col-md-12">
              <h5 style={{ marginBottom: 0, paddingBottom: '5px' }}>Property File</h5>
              <dl>
                {Object.keys(context.propertyFileContents)
                  .sort((a: string, b: string) =>
                    context.propertyFileContents[a].toString().length >
                    context.propertyFileContents[b].toString().length
                      ? 1
                      : -1,
                  )
                  .map(key => (
                    <React.Fragment key={key}>
                      <dt>{key}</dt>
                      <dd>{renderProperty(context.propertyFileContents[key])}</dd>
                    </React.Fragment>
                  ))}
              </dl>
            </div>
          </div>
        )}
        <StageFailureMessage
          stage={stage}
          message={stage.failureMessage || get(context, 'completionDetails.message')}
        />

        {taskId && (
          <div className="row">
            <div className="col-md-12">
              <div className="well alert alert-info">
                <a target="_blank" href={`${titusUiEndpoint}jobs/${jobId}/tasks/${taskId}/logs`}>
                  View Execution Logs
                </a>
              </div>
            </div>
          </div>
        )}
      </ExecutionDetailsSection>
    );
  }
}
