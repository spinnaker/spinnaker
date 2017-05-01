import * as React from 'react';
import {IVariableMetadata} from './pipelineTemplate.service';
import {variableInputService, IVariable, IVariableInput} from './variableInput.service';
import autoBindMethods from 'class-autobind-decorator';

interface IState { }

interface IProps {
  variableMetadata: IVariableMetadata;
  variable: IVariable;
  onChange: (variable: IVariable) => void;
}

@autoBindMethods
export class Variable extends React.Component<IProps, IState> {

  private getVariableInput(): JSX.Element {
    const input: IVariableInput = variableInputService.getInputForType(this.props.variableMetadata.type);
    return input ? input.getInput(this.props.variable, this.props.onChange) : null;
  }

  public render() {
    return (
      <div>
        <div className="form-group clearfix">
          <div className="col-md-3 sm-label-right">
            {this.props.variable.name}
          </div>
          <div className="col-md-7">
            {this.getVariableInput()}
          </div>
        </div>
      </div>
    );
  }
}
