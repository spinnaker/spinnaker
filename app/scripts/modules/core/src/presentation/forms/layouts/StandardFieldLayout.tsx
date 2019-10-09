import * as React from 'react';
import { isUndefined } from 'lodash';
import { ValidationMessage } from '../validation';
import { ILayoutProps } from './interface';
import './StandardFieldLayout.css';

export class StandardFieldLayout extends React.Component<ILayoutProps> {
  public render() {
    const { label, help, input, actions, validation } = this.props;
    const { hidden, messageNode, category } = validation;
    const showLabel = !isUndefined(label) || !isUndefined(help);

    return (
      <div className="StandardFieldLayout flex-container-h baseline margin-between-lg">
        {showLabel && (
          <div className="StandardFieldLayout_Label sm-label-right">
            {label} {help}
          </div>
        )}

        <div className="flex-grow">
          <div className="flex-container-v margin-between-md">
            <div className="flex-container-h baseline margin-between-lg StandardFieldLayout_Contents">
              {input} {actions}
            </div>

            {!hidden && <ValidationMessage message={messageNode} type={category} />}
          </div>
        </div>
      </div>
    );
  }
}
