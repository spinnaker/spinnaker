import classNames from 'classnames';
import React from 'react';
import { SortableHandle } from 'react-sortable-hoc';

import { IParameter } from '../../../domain';
import { HelpField } from '../../../help';
import { Tooltip } from '../../../presentation';

import { StageConfigField } from '../stages/common';

export interface IParameterOption {
  value: string;
}

export interface IParameterProps extends IParameter {
  isMultiple?: boolean;
  removeParameter: () => void;
  setPinAllParametersState: () => void;
  updateParameterField: (changes: { [key: string]: any }) => void;
}

export class Parameter extends React.Component<IParameterProps> {
  constructor(props: IParameterProps) {
    super(props);
  }

  private addOption = (): void => {
    const { options, updateParameterField } = this.props;
    const newOptions = options.splice(0);
    newOptions.push({ value: '' });
    updateParameterField({ options: newOptions });
  };

  private removeOption = (index: number): void => {
    const { options, updateParameterField } = this.props;
    updateParameterField({ options: options.filter((o) => o !== options[index]) });
  };

  private handleNameChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const name = event.target.value;
    this.props.updateParameterField({ name });
  };

  private handleLabelChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const label = event.target.value;
    this.props.updateParameterField({ label });
  };

  private handleRequiredChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const required = event.target.checked;
    this.props.updateParameterField({ required });
  };

  private handleDescriptionChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const description = event.target.value;
    this.props.updateParameterField({ description });
  };

  private handleDefaultChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const defaultValue = event.target.value;
    this.props.updateParameterField({ default: defaultValue });
  };

  private handleOptionChange = (index: number, option: string): void => {
    const { options, updateParameterField } = this.props;
    const newOptions = options.splice(0);
    newOptions.splice(index, 1, { value: option });
    updateParameterField({ options: newOptions });
  };

  private handlePinnedChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.updateParameterField({ pinned: event.target.checked });
  };

  private handleHasOptionChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const { options, updateParameterField } = this.props;
    updateParameterField({ hasOptions: event.target.checked });
    if (!options) {
      updateParameterField({ options: [{ value: '' }] });
    }
  };

  public render(): JSX.Element {
    const {
      name,
      label,
      required,
      pinned,
      description,
      hasOptions,
      options,
      isMultiple,
      removeParameter,
      inherited,
    } = this.props;

    const {
      addOption,
      handleLabelChange,
      handleNameChange,
      handleDefaultChange,
      handleDescriptionChange,
      handleHasOptionChange,
      handleOptionChange,
      handlePinnedChange,
      handleRequiredChange,
      removeOption,
    } = this;
    return (
      <div className="parameter-config">
        <div className="row">
          <div className="col-md-12">
            <fieldset disabled={inherited} className={classNames({ 'templated-pipeline-item': inherited })}>
              <div className="form-horizontal panel-pipeline-phase">
                <div className="row name-field">
                  <div className="col-md-1">{isMultiple && <DragHandler />}</div>
                  <label className="col-md-2 sm-label-right">
                    <span className="label-text">Name</span>
                  </label>
                  <div className="col-md-9">
                    <div className="row">
                      <div className="col-md-4">
                        <input
                          className="form-control input-sm"
                          type="text"
                          required={true}
                          value={name}
                          onChange={handleNameChange}
                        />
                      </div>
                      <div className="col-md-2 sm-label-right">
                        <span className="label-text">Label </span>
                        <HelpField id="pipeline.config.parameter.label" />
                      </div>
                      <div className="col-md-4">
                        <input
                          className="form-control input-sm"
                          type="text"
                          value={label}
                          onChange={handleLabelChange}
                        />
                      </div>
                      {!inherited && (
                        <div className="col-md-1 col-md-offset-1">
                          <Tooltip value="Remove parameter">
                            <button className="btn btn-link glyphicon glyphicon-trash" onClick={removeParameter} />
                          </Tooltip>
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                <StageConfigField label="Required">
                  <div className="checkbox">
                    <label>
                      <input type="checkbox" checked={required} onChange={handleRequiredChange} />
                    </label>
                  </div>
                </StageConfigField>
                <StageConfigField label="Pin Parameter" helpKey="pipeline.config.parameter.pinned">
                  <div className="checkbox">
                    <label>
                      <input type="checkbox" checked={pinned} onChange={handlePinnedChange} />
                    </label>
                  </div>
                </StageConfigField>

                <StageConfigField label="Description" helpKey="pipeline.config.parameter.description">
                  <input
                    className="form-control input-sm"
                    type="text"
                    value={description}
                    onChange={handleDescriptionChange}
                  />
                </StageConfigField>

                <StageConfigField label="Default Value">
                  <input
                    className="form-control input-sm"
                    type="text"
                    value={this.props.default}
                    onChange={handleDefaultChange}
                  />
                </StageConfigField>

                <StageConfigField label="Show Options">
                  <div className="checkbox">
                    <label>
                      <input type="checkbox" checked={hasOptions} onChange={handleHasOptionChange} />
                    </label>
                  </div>
                </StageConfigField>

                {hasOptions && (
                  <StageConfigField label="Options">
                    {options.map(function (option: IParameterOption, index: number) {
                      return (
                        <div key={index} style={{ marginBottom: '5px' }}>
                          <input
                            className="col-md-4 form-control input-sm"
                            style={{ width: '90%' }}
                            type="text"
                            value={option.value}
                            onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                              handleOptionChange(index, event.target.value)
                            }
                          />
                          {!inherited && (
                            <Tooltip value="Remove parameter">
                              <button
                                className="btn btn-link glyphicon glyphicon-trash"
                                onClick={() => removeOption(index)}
                              />
                            </Tooltip>
                          )}
                        </div>
                      );
                    })}
                    <button
                      className="btn btn-sm btn-default add-new"
                      onClick={() => addOption()}
                      style={{ marginTop: '10px' }}
                    >
                      <span className="glyphicon glyphicon-plus-sign" /> Add New Option
                    </button>
                  </StageConfigField>
                )}
              </div>
            </fieldset>
          </div>
        </div>
      </div>
    );
  }
}

const DragHandler = SortableHandle(() => <span className="clickable glyphicon glyphicon-resize-vertical" />);
