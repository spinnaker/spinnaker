import React from 'react';

import { HelpField } from '@spinnaker/core';

export interface IFormikConfigFieldProps {
  label: string;
  helpKey?: string;
  children?: React.ReactNode;
}

export class FormikConfigField extends React.Component<IFormikConfigFieldProps> {
  constructor(props: IFormikConfigFieldProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { label, helpKey, children } = this.props;
    return (
      <div className={'StandardFieldLayout flex-container-h baseline margin-between-lg'}>
        <label className={`sm-label-right`}>
          <span className="label-text">{label} </span>
          {helpKey && <HelpField id={helpKey} />}
        </label>
        <div className={`flex-grow`}>{children}</div>
      </div>
    );
  }
}
