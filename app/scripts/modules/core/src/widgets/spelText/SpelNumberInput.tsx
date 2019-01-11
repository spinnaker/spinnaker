import * as React from 'react';
import { isString } from 'lodash';
import { Tooltip } from 'core/presentation';

export interface INumberInputProps {
  value: number | string;
  onChange: (value: number | string) => void;
  min?: number;
  max?: number;
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

  private valueChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    if (event.target.type === 'number') {
      this.props.onChange(parseInt(event.target.value, 10));
    } else {
      this.props.onChange(event.target.value);
    }
  };

  public render() {
    const { expressionActive, glowing } = this.state;
    const { value, min, max } = this.props;
    return (
      <div className="navbar-form" style={{ padding: 0 }}>
        <div className={`button-input ${expressionActive ? 'text' : 'number'}${glowing ? ' focus' : ''}`}>
          <span className="btn-group btn-group-xs" role="group">
            <button
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
              className={`btn btn-default ${expressionActive ? 'active' : ''}`}
              onClick={() => this.setExpressionActive(true)}
              onFocus={() => this.setGlow(true)}
              onBlur={() => this.setGlow(false)}
            >
              <Tooltip value="Toggle to enter expression">
                <span>
                  {'${'}
                  &hellip;}
                </span>
              </Tooltip>
            </button>
          </span>
          <input
            type={expressionActive ? 'text' : 'number'}
            className="form-control borderless"
            value={value}
            min={min}
            max={max}
            onChange={this.valueChanged}
            onFocus={() => this.setGlow(true)}
            onBlur={() => this.setGlow(false)}
          />
        </div>
      </div>
    );
  }
}
