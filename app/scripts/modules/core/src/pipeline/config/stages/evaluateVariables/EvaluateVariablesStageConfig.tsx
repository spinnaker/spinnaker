import * as React from 'react';
import { map, set } from 'lodash';

import { IStageConfigProps, StageConfigField } from 'core/pipeline';
import { MapEditor } from 'core/forms';

export interface IEvaluatedVariable {
  key: string;
  value: string;
}

export class EvaluateVariablesStageConfig extends React.Component<IStageConfigProps> {
  private expand(variables: { [key: string]: string }): IEvaluatedVariable[] {
    return map(variables, (value, key): IEvaluatedVariable => ({ key, value }));
  }

  private stageFieldChanged = (fieldIndex: string, value: { [key: string]: string }) => {
    set(this.props.stage, fieldIndex, this.expand(value));
    this.props.stageFieldUpdated();
    this.forceUpdate();
  };

  private mapChanged = (key: string, values: { [key: string]: string }) => {
    this.stageFieldChanged(key, values);
  };

  public render() {
    const {
      stage: { variables = [] },
    } = this.props;

    // Flattens an array of objects {key, value} into a single object with the respective keys/values
    const variablesObject = variables.reduce(
      // tslint:disable-next-line:prefer-object-spread
      (acc: any, { key, value }: IEvaluatedVariable) => Object.assign(acc, { [key]: value }),
      {},
    );

    return (
      <div className="form-horizontal">
        <StageConfigField label="Variables to evaluate">
          <MapEditor model={variablesObject} allowEmpty={true} onChange={(v: any) => this.mapChanged('variables', v)} />
        </StageConfigField>
      </div>
    );
  }
}
