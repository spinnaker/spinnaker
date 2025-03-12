import React from 'react';

import './executionParameters.less';
import './executionStatus.less';

export interface IExecutionParametersProps {
  shouldShowAllParams: boolean;
  displayableParameters: IDisplayableParameter[];
  pinnedDisplayableParameters: IDisplayableParameter[];
}

export interface IDisplayableParameter {
  key: string;
  value: string;
  showTruncatedValue?: boolean;
  valueTruncated?: string;
}

export class ExecutionParameters extends React.Component<IExecutionParametersProps> {
  constructor(props: IExecutionParametersProps) {
    super(props);
  }

  private toggleParameterTruncation(parameter: IDisplayableParameter) {
    if (parameter.valueTruncated) {
      return () => {
        parameter.showTruncatedValue = !parameter.showTruncatedValue;
        this.forceUpdate();
      };
    }
    return null;
  }

  public render() {
    const { shouldShowAllParams, displayableParameters, pinnedDisplayableParameters } = this.props;

    let parameters = pinnedDisplayableParameters;
    if (shouldShowAllParams) {
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
              {c.map((p) => (
                <div key={p.key} className="an-execution-parameter">
                  <div className="parameter-key">{p.key}:</div>
                  <div className="parameter-value">
                    <div className="vertical">
                      <span>{p.showTruncatedValue ? p.valueTruncated : p.value}</span>
                      {p.valueTruncated && (
                        <button className="link truncate-toggle" onClick={this.toggleParameterTruncation(p)}>
                          {p.showTruncatedValue ? ' Show more' : ' Show Less'}
                        </button>
                      )}
                    </div>
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
