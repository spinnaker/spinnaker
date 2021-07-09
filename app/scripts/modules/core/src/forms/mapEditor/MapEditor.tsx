import { isEqual, isString } from 'lodash';
import React from 'react';

import { IMapPair, MapPair } from './MapPair';
import { IPipeline } from '../../domain';

import './MapEditor.less';

export interface IMapEditorProps {
  addButtonLabel?: string;
  allowEmpty?: boolean;
  hiddenKeys?: string[];
  keyLabel?: string;
  label?: string;
  labelsLeft?: boolean;
  model: string | { [key: string]: string };
  valueLabel?: string;
  onChange: (model: string | { [key: string]: string }, duplicateKeys: boolean) => void;
  valueCanContainSpel?: boolean;
  pipeline?: IPipeline;
}

export interface IMapEditorState {
  backingModel: IMapPair[];
}

export class MapEditor extends React.Component<IMapEditorProps, IMapEditorState> {
  public static defaultProps: Partial<IMapEditorProps> = {
    addButtonLabel: 'Add Field',
    allowEmpty: false,
    hiddenKeys: [],
    keyLabel: 'Key',
    labelsLeft: false,
    valueLabel: 'Value',
    valueCanContainSpel: false,
  };

  constructor(props: IMapEditorProps) {
    super(props);
    const isParameterized = isString(props.model);

    this.state = {
      backingModel: !isParameterized ? this.mapModel(props.model as { [key: string]: string }) : null,
    };
  }

  componentDidUpdate(prevProps: IMapEditorProps) {
    const isModelObj = !isString(this.props.model);
    if (isModelObj && !isEqual(prevProps.model, this.props.model)) {
      this.setState({ backingModel: this.mapModel(this.props.model as { [key: string]: string }) });
    }
  }

  private mapModel(model: { [key: string]: string }): IMapPair[] {
    return Object.keys(model).map((key) => ({ key: key, value: model[key] }));
  }

  private reduceModel(backingModel: IMapPair[]): { [key: string]: string } {
    return backingModel.reduce((acc, pair) => {
      if (this.props.allowEmpty || pair.value) {
        acc[pair.key] = pair.value;
      }
      return acc;
    }, {} as any);
  }

  private validateUnique(model: IMapPair[]): boolean {
    let error = false;

    const usedKeys = new Set();

    model.forEach((p) => {
      if (usedKeys.has(p.key)) {
        p.error = 'Duplicate key';
        error = true;
      } else {
        delete p.error;
      }
      usedKeys.add(p.key);
    });

    return error;
  }

  private handleChanged() {
    const error = this.validateUnique(this.state.backingModel);
    const newModel = this.reduceModel(this.state.backingModel);
    this.props.onChange(newModel, error);
  }

  private onChange = (newPair: IMapPair, index: number) => {
    this.state.backingModel[index] = newPair;
    this.handleChanged();
  };

  private onDelete = (index: number) => {
    this.state.backingModel.splice(index, 1);
    this.handleChanged();
  };

  private onAdd = () => {
    this.state.backingModel.push({ key: '', value: '' });
    this.handleChanged();
  };

  public render() {
    const {
      addButtonLabel,
      hiddenKeys,
      keyLabel,
      label,
      labelsLeft,
      model,
      valueLabel,
      valueCanContainSpel,
      pipeline,
    } = this.props;
    const { backingModel } = this.state;

    const rowProps = { keyLabel, valueLabel, labelsLeft };

    const columnCount = this.props.labelsLeft ? 5 : 3;
    const tableClass = this.props.label ? '' : 'no-border-top';
    const isParameterized = isString(this.props.model);

    return (
      <div className="MapEditor">
        {label && (
          <div className="sm-label-left">
            <b>{label}</b>
          </div>
        )}

        {isParameterized && <input className="form-control input-sm" value={model as string} />}
        {!isParameterized && (
          <table className={`table table-condensed packed tags ${tableClass}`}>
            <thead>
              {!labelsLeft && (
                <tr>
                  <th>{keyLabel}</th>
                  <th>{valueLabel}</th>
                  <th />
                </tr>
              )}
            </thead>
            <tbody>
              {backingModel
                .filter((p) => !hiddenKeys.includes(p.key))
                .map((pair, index) => (
                  <MapPair
                    key={index}
                    {...rowProps}
                    onChange={(value) => this.onChange(value, index)}
                    onDelete={() => this.onDelete(index)}
                    pair={pair}
                    valueCanContainSpel={valueCanContainSpel}
                    pipeline={pipeline}
                  />
                ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={columnCount}>
                  <button type="button" className="btn btn-block btn-sm add-new" onClick={this.onAdd}>
                    <span className="glyphicon glyphicon-plus-sign" />
                    {addButtonLabel}
                  </button>
                </td>
              </tr>
            </tfoot>
          </table>
        )}
      </div>
    );
  }
}
