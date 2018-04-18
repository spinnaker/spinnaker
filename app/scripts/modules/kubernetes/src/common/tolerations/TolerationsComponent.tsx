import * as React from 'react';
import { cloneDeep } from 'lodash';
import Select, { Option } from 'react-select';
import { Button } from 'react-bootstrap';
import { BindAll } from 'lodash-decorators';
import { HelpField } from '@spinnaker/core';

export type IOperator = 'Exists' | 'Equal';
export type IEffect = 'NoSchedule' | 'PreferNoSchedule' | 'NoExecute';

export interface IToleration {
  key: string;
  operator: IOperator;
  effect: IEffect;
  value: string;
  tolerationSeconds?: number;
}

export interface IKubernetesTolerationsProps {
  tolerations: IToleration[];
  onTolerationChange: (tolerations: IToleration[]) => void;
}

@BindAll()
export class KubernetesTolerations extends React.Component<IKubernetesTolerationsProps> {
  private static operators: Option[] = [{ value: 'Exists', label: 'Exists' }, { value: 'Equal', label: 'Equal' }];

  private static effects: Option[] = [
    { value: 'NoSchedule', label: 'NoSchedule' },
    { value: 'PreferNoSchedule', label: 'PreferNoSchedule' },
    { value: 'NoExecute', label: 'NoExecute' },
  ];

  public tolerations: IToleration[];

  constructor(props: IKubernetesTolerationsProps) {
    super(props);
  }

  private addTolerations(): void {
    const tolerations = cloneDeep(this.props.tolerations) || [];
    tolerations.push({ key: '', value: '', operator: 'Exists', effect: 'NoSchedule' });
    this.props.onTolerationChange(tolerations);
  }

  private removeToleration(index: number): () => void {
    return () => {
      const tolerations = cloneDeep(this.props.tolerations);
      tolerations.splice(index, 1);
      this.props.onTolerationChange(tolerations);
    };
  }

  private handleChange(index: number, tolerationProperty: keyof IToleration): (event: any) => void {
    return (event: any) => {
      const tolerations = cloneDeep(this.props.tolerations);
      tolerations[index][tolerationProperty] = event.target.value;
      this.props.onTolerationChange(tolerations);
    };
  }

  private handleOperatorSelect(index: number): (option: Option) => void {
    return (option: Option) => {
      const tolerations = cloneDeep(this.props.tolerations);
      tolerations[index].operator = option.value as IOperator;
      this.props.onTolerationChange(tolerations);
    };
  }

  private handleEffectSelect(index: number): (option: Option) => void {
    return (option: Option) => {
      const tolerations = cloneDeep(this.props.tolerations);
      tolerations[index].effect = option.value as IEffect;
      this.props.onTolerationChange(tolerations);
    };
  }

  public render() {
    const tolerations = this.props.tolerations || [];
    return (
      <div>
        <div className="sm-label-left">Tolerations</div>
        <table className="table table-condensed">
          <tbody>
            {tolerations.map((row, i) => {
              return (
                <tr key={i}>
                  <td>
                    <div className="form-group">
                      <div className="col-md-3 sm-label-right">
                        <b>Key </b>
                        <HelpField id="kubernetes.serverGroup.tolerations.key" />
                      </div>
                      <div className="col-md-4">
                        <input
                          className="form-control input input-sm"
                          type="text"
                          value={row.key}
                          onChange={this.handleChange(i, 'key')}
                        />
                      </div>
                      <div className="col-md-2 col-md-offset-1">
                        <Button className="btn btn-sm btn-default" onClick={this.removeToleration(i)}>
                          <span className="glyphicon glyphicon-trash visible-lg-inline" /> Remove
                        </Button>
                      </div>
                    </div>
                    <div className="form-group">
                      <div className="col-md-3 sm-label-right">
                        <b>Operator </b>
                        <HelpField id="kubernetes.serverGroup.tolerations.operator" />
                      </div>
                      <div className="col-md-4">
                        <Select
                          value={{ value: row.operator, label: row.operator }}
                          options={KubernetesTolerations.operators}
                          onChange={this.handleOperatorSelect(i)}
                          clearable={false}
                        />
                      </div>
                    </div>
                    <div className="form-group">
                      <div className="col-md-3 sm-label-right">
                        <b>Effect </b>
                        <HelpField id="kubernetes.serverGroup.tolerations.effect" />
                      </div>
                      <div className="col-md-4">
                        <Select
                          value={{ value: row.effect, label: row.effect }}
                          options={KubernetesTolerations.effects}
                          onChange={this.handleEffectSelect(i)}
                          clearable={false}
                        />
                      </div>
                    </div>
                    <div className="form-group">
                      <div className="col-md-3 sm-label-right">
                        <b>Value </b>
                        <HelpField id="kubernetes.serverGroup.tolerations.value" />
                      </div>
                      <div className="col-md-4">
                        <input
                          className="form-control input input-sm"
                          type="text"
                          value={row.value}
                          onChange={this.handleChange(i, 'value')}
                        />
                      </div>
                    </div>
                    <div className="form-group">
                      <div className="col-md-3 sm-label-right">
                        <b>Toleration Seconds </b>
                        <HelpField id="kubernetes.serverGroup.tolerations.tolerationSeconds" />
                      </div>
                      <div className="col-md-4">
                        <input
                          className="form-control input input-sm"
                          type="number"
                          value={row.tolerationSeconds || ''}
                          onChange={this.handleChange(i, 'tolerationSeconds')}
                        />
                      </div>
                    </div>
                  </td>
                </tr>
              );
            })}
            <tr>
              <td>
                <Button className="btn btn-block add-new btn-sm" onClick={this.addTolerations}>
                  <span className="glyphicon glyphicon-plus-sign" /> Add Toleration
                </Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    );
  }
}
