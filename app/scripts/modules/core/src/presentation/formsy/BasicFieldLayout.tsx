import * as React from 'react';
import { IFormFieldLayoutProps } from './formFieldLayout';

/**
 * A Form Field Layout component for Formsy Form Field components
 *
 * Accepts four react elements as props (_label, _input, _help, _error) and lays them out using bootstrap grid.
 *
 * +----------------div.form-group----------------------------+
 * |                 +-------------div.col-md-9--------------+|
 * |<label.col-md-3> |+-------------------------------------+||
 * |                 ||input element                        |||
 * |                 |+-------------------------------------+||
 * |                 |                                       ||
 * |                 |<help element>                         ||
 * |                 |                                       ||
 * |                 |<validation element>                   ||
 * |                 |                                       ||
 * |                 +---------------------------------------+|
 * +----------------------------------------------------------+
 */
export class BasicFieldLayout extends React.Component<IFormFieldLayoutProps, {}> {
  constructor(props: IFormFieldLayoutProps) {
    super(props);
  }

  public render() {
    const { Label, Input, Help, Error, showRequired, showError } = this.props;

    const LabelDiv = Label && <div className="col-md-3 sm-label-right"> {Label} </div>;
    const HelpDiv = Help && <div className="small text-right"> {Help} </div>;
    const ErrorDiv = Error && <div className="ng-invalid"> {Error} </div>;

    const InputGroup = Input && (
      <div className="col-md-9">
        {Input}
        {HelpDiv}
        {ErrorDiv}
      </div>
    );

    const className = `form-group ${showRequired || showError ? 'ng-invalid' : ''}`;
    return (
      <div className={className}>
        {LabelDiv}
        {InputGroup}
      </div>
    );
  }
}
