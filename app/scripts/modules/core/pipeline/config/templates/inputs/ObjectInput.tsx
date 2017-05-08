import {module} from 'angular';
import * as React from 'react';
import {IVariableInputBuilder, VariableInputService, IVariable, IVariableProps, IVariableState} from './variableInput.service';
import autoBindMethods from 'class-autobind-decorator';
import {VariableError} from '../VariableError';

export class ObjectInputBuilder implements IVariableInputBuilder {

  public handles(type: string): boolean {
    return type === 'object';
  }

  public getInput(variable: IVariable, onChange: (variable: IVariable) => void) {
    return <ObjectInput variable={variable} onChange={onChange}/>
  }
}

@autoBindMethods
class ObjectInput extends React.Component<IVariableProps, IVariableState> {

  public render() {
    return (
      <div>
        <textarea
          className="form-control input-sm"
          rows={5}
          value={this.props.variable.value || ''}
          onChange={this.extractValue}
          required={true}
        />
        {!this.props.variable.hideErrors && <VariableError errors={this.props.variable.errors}/>}
      </div>
    );
  }

  private extractValue(e: React.ChangeEvent<HTMLTextAreaElement>): void {
    this.props.onChange({value: e.target.value, type: this.props.variable.type, name: this.props.variable.name});
  }
}

export const OBJECT_INPUT = 'spinnaker.core.pipelineTemplate.objectInput';
module(OBJECT_INPUT, [])
  .run((variableInputService: VariableInputService) => variableInputService.addInput(new ObjectInputBuilder()));
