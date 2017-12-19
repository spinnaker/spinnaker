import * as React from 'react';
import * as classNames from 'classnames';

interface IAddNewButtonProps {
  onClick: () => void;
  className?: string;
  label?: string;
}

export default ({ onClick, className, label }: IAddNewButtonProps) => (
  <button
    onClick={onClick}
    className={classNames('add-new', 'btn', 'btn-block', 'btn-sm', className)}
  >
    <span className="glyphicon glyphicon-plus-sign"/> {label || 'Add new'}
  </button>
);
