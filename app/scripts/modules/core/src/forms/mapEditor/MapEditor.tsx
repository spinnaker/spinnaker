import * as React from 'react';
import { isString } from 'lodash';

export interface IMapPair {
  key: string;
  value: string;
  error?: string;
}

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
}

export interface IMapEditorState {
  backingModel: IMapPair[];
  columnCount: number;
  isParameterized: boolean;
  tableClass: string;
}

export class MapEditor extends React.Component<IMapEditorProps, IMapEditorState> {
  public static defaultProps: Partial<IMapEditorProps> = {
    addButtonLabel: 'Add Field',
    allowEmpty: false,
    hiddenKeys: [],
    keyLabel: 'Key',
    labelsLeft: false,
    valueLabel: 'Value',
  };

  constructor(props: IMapEditorProps) {
    super(props);

    const isParameterized = isString(props.model);

    this.state = {
      backingModel: !isParameterized ? this.mapModel(props.model as { [key: string]: string }) : null,
      columnCount: props.labelsLeft ? 5 : 3,
      isParameterized,
      tableClass: props.label ? '' : 'no-border-top',
    };
  }

  private mapModel(model: { [key: string]: string }): IMapPair[] {
    return Object.keys(model).map(key => ({ key: key, value: model[key] }));
  }

  private reduceModel(backingModel: IMapPair[]): { [key: string]: string } {
    return backingModel.reduce(
      (acc, pair) => {
        if (this.props.allowEmpty || pair.value) {
          acc[pair.key] = pair.value;
        }
        return acc;
      },
      {} as any,
    );
  }

  private validateUnique(model: IMapPair[]): boolean {
    let error = false;

    const usedKeys = new Set();

    model.forEach(p => {
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

  public componentWillReceiveProps(_nextProps: IMapEditorProps) {
    // const isParameterized = isString(nextProps.model);
    // this.setState({
    //   backingModel: !isParameterized ? this.mapModel(nextProps.model as {[key: string]: string}) : null,
    //   isParameterized,
    // });
  }

  public render() {
    const { addButtonLabel, hiddenKeys, keyLabel, label, labelsLeft, model, valueLabel } = this.props;
    const { backingModel, columnCount, isParameterized, tableClass } = this.state;

    const rowProps = { keyLabel, valueLabel, labelsLeft };

    return (
      <div>
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
                .filter(p => !hiddenKeys.includes(p.key))
                .map((pair, index) => (
                  <MapPair
                    key={index}
                    {...rowProps}
                    onChange={value => this.onChange(value, index)}
                    onDelete={() => this.onDelete(index)}
                    pair={pair}
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

const MapPair = (props: {
  keyLabel: string;
  valueLabel: string;
  labelsLeft: boolean;
  pair: IMapPair;
  onChange: (pair: IMapPair) => void;
  onDelete: () => void;
}) => {
  const { keyLabel, labelsLeft, pair, onChange, onDelete, valueLabel } = props;

  return (
    <tr>
      {labelsLeft && (
        <td className="table-label">
          <b>{keyLabel}</b>
        </td>
      )}
      <td>
        <input
          className="form-control input input-sm"
          type="text"
          value={pair.key}
          onChange={e => onChange({ key: e.target.value, value: pair.value })}
        />
        {pair.error && <div className="error-message">{pair.error}</div>}
      </td>
      {labelsLeft && (
        <td className="table-label">
          <b>{valueLabel}</b>
        </td>
      )}
      <td>
        <input
          className="form-control input input-sm"
          type="text"
          value={pair.value}
          onChange={e => onChange({ key: pair.key, value: e.target.value })}
        />
      </td>
      <td>
        <div className="form-control-static">
          <a className="clickable" onClick={onDelete}>
            <span className="glyphicon glyphicon-trash" />
            <span className="sr-only">Remove field</span>
          </a>
        </div>
      </td>
    </tr>
  );
};
