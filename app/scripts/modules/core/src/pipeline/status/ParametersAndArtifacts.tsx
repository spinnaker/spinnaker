import { keyBy, truncate } from 'lodash';
import memoizeOne from 'memoize-one';
import React from 'react';

import { ExecutionParameters, IDisplayableParameter } from './ExecutionParameters';
import { ResolvedArtifactList } from './ResolvedArtifactList';
import { IExecution, IPipeline } from '../../domain';

export interface IParametersAndArtifactsProps {
  execution: IExecution;
  pipelineConfig: IPipeline;
  expandParamsOnInit: boolean;
}

export interface IParametersAndArtifactsState {
  showingParams: boolean;
}

export class ParametersAndArtifacts extends React.Component<
  IParametersAndArtifactsProps,
  IParametersAndArtifactsState
> {
  constructor(props: IParametersAndArtifactsProps) {
    super(props);
    this.state = {
      showingParams: props.expandParamsOnInit,
    };
  }

  private toggleParams = (): void => {
    const { showingParams } = this.state;
    this.setState({ showingParams: !showingParams });
  };

  private getDisplayableParameters = memoizeOne((execution: IExecution, pipelineConfig: IPipeline): {
    displayableParameters: IDisplayableParameter[];
    pinnedDisplayableParameters: IDisplayableParameter[];
  } => {
    // these are internal parameters that are not useful to end users
    const strategyExclusions = ['parentPipelineId', 'strategy', 'parentStageId', 'deploymentDetails', 'cloudProvider'];

    const truncateLength = 200;

    const isParamDisplayable = (paramKey: string) =>
      execution.isStrategy ? !strategyExclusions.includes(paramKey) : true;

    const displayableParameters: IDisplayableParameter[] = Object.keys(
      (execution.trigger && execution.trigger.parameters) || {},
    )
      .filter(isParamDisplayable)
      .sort()
      .map((key: string) => {
        const value = JSON.stringify(execution.trigger.parameters[key]);
        const showTruncatedValue = value.length > truncateLength;
        let valueTruncated;
        if (showTruncatedValue) {
          valueTruncated = truncate(value, { length: truncateLength });
        }
        return { key, value, valueTruncated, showTruncatedValue };
      });

    let pinnedDisplayableParameters: IDisplayableParameter[] = [];

    if (pipelineConfig) {
      const paramConfigIndexByName = keyBy(pipelineConfig.parameterConfig, 'name');
      const isParamPinned = (param: IDisplayableParameter): boolean =>
        paramConfigIndexByName[param.key] && paramConfigIndexByName[param.key].pinned; // an older execution's parameter might be missing from a newer pipelineConfig.parameterConfig

      pinnedDisplayableParameters = displayableParameters.filter(isParamPinned);
    }

    return { displayableParameters, pinnedDisplayableParameters };
  });

  private getLabel(displayableParameters: IDisplayableParameter[]): string {
    const { execution } = this.props;
    const { trigger } = execution;
    const { resolvedExpectedArtifacts } = trigger;

    const showParameters = displayableParameters.length > 0;
    const showArtifacts = resolvedExpectedArtifacts.length > 0;

    if (showParameters && showArtifacts) {
      return `Parameters/Artifacts (${displayableParameters.length}/${resolvedExpectedArtifacts.length})`;
    }
    if (showParameters) {
      return `Parameters (${displayableParameters.length})`;
    }
    return `Artifacts (${resolvedExpectedArtifacts.length})`;
  }

  public render() {
    const { execution, pipelineConfig } = this.props;
    const { showingParams } = this.state;

    const { displayableParameters, pinnedDisplayableParameters } = this.getDisplayableParameters(
      execution,
      pipelineConfig,
    );

    const { trigger } = execution;
    const { artifacts, resolvedExpectedArtifacts } = trigger;

    const parametersAndArtifactsExpanded =
      showingParams ||
      (displayableParameters.length === pinnedDisplayableParameters.length && !resolvedExpectedArtifacts.length);

    if (!displayableParameters.length && !resolvedExpectedArtifacts.length) {
      return null;
    }

    const label = this.getLabel(displayableParameters);

    return (
      <>
        <div className="execution-parameters-button">
          <a className="clickable" onClick={this.toggleParams}>
            <span
              className={`small glyphicon ${
                parametersAndArtifactsExpanded ? 'glyphicon-chevron-down' : 'glyphicon-chevron-right'
              }`}
            />
            {parametersAndArtifactsExpanded ? '' : 'View All '}
            {label}
          </a>
        </div>
        <ExecutionParameters
          shouldShowAllParams={showingParams}
          displayableParameters={displayableParameters}
          pinnedDisplayableParameters={pinnedDisplayableParameters}
        />
        <ResolvedArtifactList
          artifacts={artifacts}
          resolvedExpectedArtifacts={resolvedExpectedArtifacts}
          showingExpandedArtifacts={showingParams}
        />
      </>
    );
  }
}
