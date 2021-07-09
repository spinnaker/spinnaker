import { isString } from 'lodash';
import React from 'react';

import { Tooltip } from '../../presentation';

export interface INumberInputProps {
  value: number | string;
  onChange: (value: number | string) => void;
  min?: number;
  max?: number;
  required?: boolean;
  error?: string;
  disabled?: boolean;
}

export interface INumberInputState {
  expressionActive: boolean;
  glowing: boolean;
}

export class SpelNumberInput extends React.Component<INumberInputProps, INumberInputState> {
  constructor(props: INumberInputProps) {
    super(props);
    this.state = {
      expressionActive: isString(props.value),
      glowing: false,
    };
  }

  public componentWillReceiveProps(nextProps: INumberInputProps) {
    const expressionActive = isString(nextProps.value);
    if (this.state.expressionActive !== expressionActive) {
      this.setState({ expressionActive });
    }
  }

  private setExpressionActive(expressionActive: boolean): void {
    this.setState({ expressionActive });
  }

  private setGlow(glowing: boolean): void {
    this.setState({ glowing });
  }

  private valueChanged = ({ target: { value } }: React.ChangeEvent<HTMLInputElement>): void => {
    const num = parseInt(value, 10);
    this.props.onChange(isNaN(num) ? value : num);
  };

  public render() {
    const { expressionActive, glowing } = this.state;
    const { value, min, max, required = false, error, disabled } = this.props;
    return (
      <div className="navbar-form" style={{ padding: 0, margin: 0, display: 'inline-block' }}>
        <Tooltip value={error} placement="right">
          <div className={`button-input${glowing && !error ? ' focus' : ''}${error ? ' invalid' : ''}`}>
            <span className="btn-group btn-group-xs" role="group">
              <button
                type="button"
                disabled={disabled}
                className={`btn btn-default ${expressionActive ? '' : 'active'}`}
                onClick={() => this.setExpressionActive(false)}
                onFocus={() => this.setGlow(true)}
                onBlur={() => this.setGlow(false)}
              >
                <Tooltip value="Toggle to enter number">
                  <span>Num</span>
                </Tooltip>
              </button>
              <button
                type="button"
                disabled={disabled}
                className={`btn btn-default ${expressionActive ? 'active' : ''}`}
                onClick={() => this.setExpressionActive(true)}
                onFocus={() => this.setGlow(true)}
                onBlur={() => this.setGlow(false)}
              >
                <Tooltip value="Toggle to enter expression">
                  <span>
                    {'${'}&hellip;{'}'}
                  </span>
                </Tooltip>
              </button>
            </span>
            <input
              type={expressionActive ? 'text' : 'number'}
              disabled={disabled}
              className={`form-control borderless inline-${expressionActive ? 'text' : 'number'}`}
              value={value}
              min={min}
              max={max}
              onChange={this.valueChanged}
              onFocus={() => this.setGlow(true)}
              onBlur={() => this.setGlow(false)}
              required={required}
            />
          </div>
        </Tooltip>
      </div>
    );
  }
}
