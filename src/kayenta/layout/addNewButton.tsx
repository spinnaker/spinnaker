import * as React from 'react';
import * as classNames from 'classnames';
import { DISABLE_EDIT_CONFIG, DisableableButton } from './disableable';

interface IAddNewButtonProps {
  onClick: () => void;
  className?: string;
  label?: string;
}

export default ({ onClick, className, label }: IAddNewButtonProps) => (
  <DisableableButton
    onClick={onClick}
    disabledStateKeys={[DISABLE_EDIT_CONFIG]}
    className={classNames('add-new', 'btn', 'btn-block', 'btn-sm', className)}
  >
    <span className="glyphicon glyphicon-plus-sign"/> {label || 'Add new'}
  </DisableableButton>
);
