import React from 'react';

export interface ISpanDropdownTriggerProps {
  bsRole: 'toggle';
  children: React.ReactNode;
  className?: string;

  // react-bootstrap passes these somehow.
  bsClass?: string;
  onClick?: (evt: React.MouseEvent<any>) => void;
}

/**
 * A react-bootstrap dropdown trigger that doesn't render as a button.
 * Use in place of <Dropdown.Trigger/>
 */
export class SpanDropdownTrigger extends React.Component<ISpanDropdownTriggerProps> {
  public render() {
    const { className, onClick, children } = this.props;
    return (
      <span className={className} onClick={onClick}>
        {' '}
        {children}{' '}
      </span>
    );
  }
}
