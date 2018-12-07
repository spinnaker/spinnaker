import * as React from 'react';
import { set } from 'lodash';

import { IStageConfigProps, StageConfigField } from 'core/pipeline';
import { IPreconfiguredJobParameter } from './preconfiguredJobStage';

export class PreconfiguredJobStageConfig extends React.Component<IStageConfigProps> {
  private parameterFieldChanged = (fieldIndex: string, value: any) => {
    set(this.props.stage, `parameters.${fieldIndex}`, value);
    this.props.stageFieldUpdated();
    this.forceUpdate();
  };

  public render() {
    const {
      stage: { parameters = {} },
      configuration,
    } = this.props;

    return (
      <div className="form-horizontal">
        {configuration.parameters.map((parameter: IPreconfiguredJobParameter) => (
          <StageConfigField label={parameter.label}>
            <input
              type="text"
              className="form-control input-sm"
              value={parameters[parameter.name]}
              onChange={e => this.parameterFieldChanged(parameter.name, e.target.value)}
            />
          </StageConfigField>
        ))}
      </div>
    );
  }
}
