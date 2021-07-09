import { get, isEmpty } from 'lodash';
import React from 'react';

import { DeploymentMonitorReader, IDeploymentMonitorDefinition } from './DeploymentMonitorReader';
import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageFailureMessage } from '../../../details';
import { IExecutionStage } from '../../../../domain';

interface IAdditionalData {
  link: string;
  text: string;
  key: string;
}

function getDeploymentMonitorSummary(stage: IExecutionStage) {
  if (stage.hasNotStarted) {
    return "Not available because the stage hasn't run yet";
  }

  const { context } = stage;
  return get(context, 'deploymentMonitorReasons.summary', '<NOT PROVIDED>');
}

function getDeploymentMonitorName(stage: IExecutionStage, monitors: IDeploymentMonitorDefinition[]) {
  return monitors.find((x) => x.id === stage.context.deploymentMonitor.id).name;
}

function getDeploymentMonitorSupportUrl(stage: IExecutionStage, monitors: IDeploymentMonitorDefinition[]) {
  return monitors.find((x) => x.id === stage.context.deploymentMonitor.id).supportContact;
}

function getDeploymentMonitorDetails(stage: IExecutionStage) {
  const { context } = stage;

  return get(context, 'deploymentMonitorReasons.reason.message', null);
}

function getDeploymentMonitorLog(stage: IExecutionStage) {
  const { context } = stage;
  const logArray = get(context, 'deploymentMonitorReasons.reason.logSummary', null);

  if (isEmpty(logArray)) {
    return null;
  }

  return logArray.map((logLine: string, i: number) => (
    <React.Fragment key={i}>
      <span>{logLine}</span>
      <br />
    </React.Fragment>
  ));
}

function getDeploymentMonitorAdditionalDetails(stage: IExecutionStage) {
  const { context } = stage;
  const additionalData = get(context, 'deploymentMonitorReasons.reason.additionalData', []) as IAdditionalData[];

  if (!isEmpty(additionalData)) {
    return additionalData.map(({ key, link, text }) => (
      <React.Fragment key={key + link + text}>
        <dt>{key}</dt>
        <dd>
          <a href={link} target="_blank">
            {text}
          </a>
        </dd>
      </React.Fragment>
    ));
  }

  return null;
}

interface IDeploymentMonitorExecutionDetailsSectionState {
  deploymentMonitors: IDeploymentMonitorDefinition[];
}

export class DeploymentMonitorExecutionDetails extends React.Component<
  IExecutionDetailsSectionProps,
  IDeploymentMonitorExecutionDetailsSectionState
> {
  public static title = 'evaluateDeploymentHealth';
  public state: IDeploymentMonitorExecutionDetailsSectionState = { deploymentMonitors: null };

  public componentDidMount(): void {
    DeploymentMonitorReader.getDeploymentMonitors().then((deploymentMonitors) => {
      this.setState({ deploymentMonitors });
    });
  }

  public render() {
    const { stage, current, name } = this.props;
    const { deploymentMonitors } = this.state;

    const additionalDetails = getDeploymentMonitorAdditionalDetails(stage);
    const log = getDeploymentMonitorLog(stage);
    const details = getDeploymentMonitorDetails(stage);

    const notProvidedFragment = (
      <React.Fragment>
        <i>&lt;NOT PROVIDED&gt;</i>
      </React.Fragment>
    );

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-12">
            <dl className="dl-narrow dl-horizontal">
              <dt>Summary</dt>
              <dd>{getDeploymentMonitorSummary(stage)}</dd>
              {stage.hasNotStarted ? null : (
                <>
                  <dt className="sp-margin-l-bottom">Deploy %</dt>
                  <dd className="sp-margin-l-bottom">
                    <span>{stage.context.currentProgress}%</span>
                  </dd>
                  <dt>Details</dt>
                  <dd>{details || notProvidedFragment}</dd>
                  <dt className="sp-margin-l-bottom">Log</dt>
                  <dd className="sp-margin-l-bottom">{log ? <pre>{log}</pre> : notProvidedFragment}</dd>
                  <dt>More info</dt>
                  <dd>{additionalDetails ? null : notProvidedFragment}</dd>
                  {additionalDetails}
                </>
              )}
            </dl>
            {deploymentMonitors && (
              <div className="alert alert-info">
                <strong>NOTE:</strong> This information is provided by the&nbsp;
                <strong>{getDeploymentMonitorName(stage, deploymentMonitors)}</strong> deployment monitor.
                <br />
                If you are experiencing issues with the analysis, please reach out to{' '}
                <a href={getDeploymentMonitorSupportUrl(stage, deploymentMonitors)} target="_blank">
                  support for {getDeploymentMonitorName(stage, deploymentMonitors)}
                </a>
              </div>
            )}
          </div>
        </div>

        <StageFailureMessage stage={stage} message={stage.failureMessage} />
      </ExecutionDetailsSection>
    );
  }
}
