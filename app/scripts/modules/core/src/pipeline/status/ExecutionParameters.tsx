import * as React from 'react';

import { IDisplayableParameter } from 'core/pipeline';
import { IPipeline } from 'core/domain';

import './executionStatus.less';
import './executionParameters.less';

export interface IExecutionParametersProps {
  shouldShowAllParams: boolean;
  displayableParameters: IDisplayableParameter[];
  pinnedDisplayableParameters: IDisplayableParameter[];
  pipelineConfig: IPipeline;
}

interface IExecutionParametersState {
  toggle: boolean;
}

export class ExecutionParameters extends React.Component<IExecutionParametersProps, IExecutionParametersState> {
  constructor(props: IExecutionParametersProps) {
    super(props);
  }

  private toggleParameterTruncation(parameter: IDisplayableParameter) {
    if (parameter.valueTruncated) {
      return () => {
        parameter.showTruncatedValue = !parameter.showTruncatedValue;
        this.setState({ toggle: parameter.showTruncatedValue });
      };
    }
    return null;
  }

  public render() {
    const { shouldShowAllParams, displayableParameters, pinnedDisplayableParameters, pipelineConfig } = this.props;

    let parameters = pinnedDisplayableParameters;
    if (shouldShowAllParams || (pipelineConfig && pipelineConfig.pinAllParameters)) {
      parameters = displayableParameters;
    }

    if (!parameters.length) {
      return null;
    }

    const halfWay = Math.ceil(parameters.length / 2);
    const paramsSplitIntoColumns = [parameters.slice(0, halfWay), parameters.slice(halfWay)];

    return (
      <div className="execution-parameters">
        <h6 className="params-title">{shouldShowAllParams && 'Parameters'}</h6>

        <div className="execution-parameters-container">
          {paramsSplitIntoColumns.map((c, i) => (
            <div key={`execution-params-column-${i}`} className="execution-parameters-column">
              {c.map(p => (
                <div key={p.key} className="an-execution-parameter">
                  <div className="parameter-key">{p.key}:</div>
                  <div className="parameter-value" onClick={this.toggleParameterTruncation(p)}>
                    {p.showTruncatedValue ? p.valueTruncated : p.value}
                    {p.showTruncatedValue ? (
                      <a>
                        <span> View Full</span>
                      </a>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>
    );
  }
}
