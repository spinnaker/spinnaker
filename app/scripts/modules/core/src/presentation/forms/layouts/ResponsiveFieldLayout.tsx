import * as React from 'react';
import * as classNames from 'classnames';

import { HelpContextProvider } from 'core/help';

import { IFieldLayoutProps } from '../interface';

import '../forms.less';

export class ResponsiveFieldLayout extends React.Component<IFieldLayoutProps> {
  public render() {
    const { label, help, input, actions, validationMessage, validationStatus } = this.props;
    const showLabel = !!label || !!help;

    const helpUnder = false;

    return (
      <HelpContextProvider value={helpUnder}>
        <div className="sp-formItem">
          <div className="sp-formItem__left">
            {showLabel && (
              <div className="sp-formLabel">
                {label} {!helpUnder && help}
              </div>
            )}
          </div>
          <div className="sp-formItem__right">
            <div className="sp-form">
              <span className="field">
                {input} {actions}
              </span>
            </div>
            {helpUnder && help && <div className="description">{help}</div>}
            {validationMessage && (
              <div
                className={classNames('messageContainer', {
                  errorMessage: validationStatus === 'error',
                  warningMessage: validationStatus === 'warning',
                  previewMessage: validationStatus === 'message',
                })}
              >
                <i />
                <div className="message">{validationMessage}</div>
              </div>
            )}
          </div>
        </div>
      </HelpContextProvider>
    );
  }
}
