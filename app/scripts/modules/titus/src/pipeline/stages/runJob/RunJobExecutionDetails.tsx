import { get } from 'lodash';
import React from 'react';

import {
  AccountService,
  AccountTag,
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  LabeledValue,
  LabeledValueList,
  Markdown,
  RenderOutputFile,
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
    AccountService.getAccountDetails(context.credentials).then((details) => {
      const titusUiEndpoint = details.regions.find((r) => r.name === context.cluster.region).endpoint;
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

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-9">
            <LabeledValueList className="dl-narrow dl-horizontal">
              <LabeledValue label="Account" value={<AccountTag account={context.credentials} />} />
              {cluster && (
                <>
                  <LabeledValue label="Image" value={cluster.imageId} />
                  {cluster.entryPoint && <LabeledValue label="Entrypoint" value={cluster.entryPoint} />}
                </>
              )}
              {jobId && (
                <LabeledValue
                  label="Titus Job Id"
                  value={<Markdown message={`[${jobId}](${titusUiEndpoint}jobs/${jobId})`} />}
                />
              )}
              {taskId && (
                <LabeledValue
                  label="Titus Task Id"
                  value={<Markdown message={`[${taskId}](${titusUiEndpoint}jobs/${jobId}/tasks/${taskId})`} />}
                />
              )}
              {resources && Object.keys(resources) && (
                <LabeledValue
                  label="Resources"
                  value={
                    <ul className="nostyle">
                      {Object.keys(resources).map((key) => (
                        <li key={key}>
                          {key}: {resources[key]}
                        </li>
                      ))}
                    </ul>
                  }
                />
              )}
            </LabeledValueList>
          </div>
        </div>
        {env && Object.keys(env) && (
          <div className="row">
            <div className="col-md-12">
              <h5 style={{ marginBottom: 0, paddingBottom: '5px' }}>Environment Variables</h5>
              <LabeledValueList>
                {Object.keys(env).map((key) => (
                  <LabeledValue key={key} label={key} value={env[key]} />
                ))}
              </LabeledValueList>
            </div>
          </div>
        )}
        {context.propertyFileContents && (
          <div className="row">
            <div className="col-md-12">
              <h5 style={{ marginBottom: '0px', paddingBottom: '5px' }}>Property File</h5>
              <RenderOutputFile outputFileObject={context.propertyFileContents} />
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
                <br />
                <a
                  target="_blank"
                  href={`${titusUiEndpoint}jobs/${jobId}/tasks/${taskId}/logs/archived?file=stdout&view=Finished&open=true`}
                >
                  Stdout
                </a>
                <br />
                <a
                  target="_blank"
                  href={`${titusUiEndpoint}jobs/${jobId}/tasks/${taskId}/logs/archived?file=stderr&view=Finished&open=true`}
                >
                  Stderr
                </a>
              </div>
            </div>
          </div>
        )}
      </ExecutionDetailsSection>
    );
  }
}
