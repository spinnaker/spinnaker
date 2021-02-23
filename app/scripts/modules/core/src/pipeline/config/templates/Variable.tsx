import React from 'react';

import { IVariableMetadata } from './PipelineTemplateReader';
import { VariableMetadataHelpField } from './VariableMetadataHelpField';
import { IVariable, IVariableInputBuilder, VariableInputService } from './inputs/variableInput.service';

import './Variable.less';

export interface IVariableProps {
  variableMetadata: IVariableMetadata;
  variable: IVariable;
  onChange: (variable: IVariable) => void;
}

export class Variable extends React.Component<IVariableProps> {
  private getVariableInput(): JSX.Element {
    const input: IVariableInputBuilder = VariableInputService.getInputForType(this.props.variableMetadata.type);
    return input ? input.getInput(this.props.variable, this.props.onChange) : null;
  }

  public render() {
    return (
      <div>
        <div className="form-group clearfix pipeline-template-variable">
          <div className="col-md-4">
            <div className="pull-right">
              <code>{this.props.variable.name}</code>
              <VariableMetadataHelpField metadata={this.props.variableMetadata} />
            </div>
          </div>
          <div className="col-md-7">{this.getVariableInput()}</div>
        </div>
      </div>
    );
  }
}
