import React from 'react';
import Select, { Option } from 'react-select';

import { noop } from '@spinnaker/core';

import { IManifestLabelSelector, LABEL_KINDS } from '../IManifestLabelSelector';

import './labelEditor.less';

export interface ILabelEditorProps {
  labelSelectors: IManifestLabelSelector[];
  onLabelSelectorsChange: (labelSelectors: IManifestLabelSelector[]) => void;
}

const LabelKeyOptions: Array<Option<string>> = LABEL_KINDS.map((kind) => ({ label: kind, value: kind }));

export default class LabelEditor extends React.Component<ILabelEditorProps> {
  public static defaultProps: Partial<ILabelEditorProps> = {
    labelSelectors: [],
    onLabelSelectorsChange: noop,
  };

  private static convertValueStringToArray = (value: string): string[] => {
    return value.split(',').map((v: string) => v.trim());
  };

  private handleChange = (idx: number, property: keyof IManifestLabelSelector, value: string | string[]): void => {
    this.props.onLabelSelectorsChange(
      this.props.labelSelectors.map((ls, i) => {
        if (idx !== i) {
          return ls;
        }
        return {
          ...ls,
          [property]: value,
        };
      }),
    );
  };

  private removeField = (idx: number): void => {
    this.props.onLabelSelectorsChange(
      this.props.labelSelectors.filter((_ls, i) => {
        return idx !== i;
      }),
    );
  };

  private addField = (): void => {
    return this.props.onLabelSelectorsChange(
      this.props.labelSelectors.concat([
        {
          key: '',
          kind: 'EQUALS',
          values: [],
        },
      ]),
    );
  };

  public render() {
    return (
      <table className="table table-condensed packed tags">
        <thead>
          <tr>
            <th>Key</th>
            <th>Kind</th>
            <th>Value(s)</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {this.props.labelSelectors.map((selector, idx) => (
            <tr className="label-editor-selector-row" key={idx}>
              <td>
                <input
                  className="form-control input input-sm label-editor-key-input"
                  type="text"
                  value={selector.key}
                  onChange={(e: any) => this.handleChange(idx, 'key', e.target.value)}
                />
              </td>
              <td>
                <Select
                  className="label-editor-kind-select"
                  clearable={false}
                  onChange={(option: Option<string>) => this.handleChange(idx, 'kind', option.value)}
                  options={LabelKeyOptions}
                  value={selector.kind}
                />
              </td>
              <td>
                <input
                  className="form-control input input-sm label-editor-values-input"
                  onChange={(e: any) =>
                    this.handleChange(idx, 'values', LabelEditor.convertValueStringToArray(e.target.value))
                  }
                  placeholder="Comma-separated values"
                  value={selector.values.join(', ')}
                  type="text"
                />
              </td>
              <td>
                <button className="link label-editor-remove" onClick={() => this.removeField(idx)}>
                  <span className="glyphicon glyphicon-trash" />
                  <span className="sr-only">Remove field</span>
                </button>
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={4}>
              <button className="btn btn-block btn-sm add-new" onClick={this.addField}>
                <span className="glyphicon glyphicon-plus-sign" /> Add Label
              </button>
            </td>
          </tr>
        </tfoot>
      </table>
    );
  }
}
