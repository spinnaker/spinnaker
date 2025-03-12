import React from 'react';

import { Tooltip } from './Tooltip';
import { HelpField } from '../help';

export enum ToggleSize {
  XSMALL = 'xs',
  SMALL = 'sm',
  MEDIUM = 'm',
  LARGE = 'lg',
}

export interface IToggleButtonGroupProps {
  toggleSize?: ToggleSize;
  propLabel: string;
  propHelpFieldId?: string;
  tooltipPropOnBtn?: string;
  displayTextPropOnBtn?: string;
  tooltipPropOffBtn?: string;
  displayTextPropOffBtn?: string;
  onClick: (value: boolean) => void;
  isPropertyActive?: boolean;
}

ToggleButtonGroup.defaultProps = {
  toggleSize: ToggleSize.XSMALL,
  displayTextPropOffBtn: 'Off',
  tooltipPropOffBtn: 'Toggle to turn OFF',
  displayTextPropOnBtn: 'On',
  tooltipPropOnBtn: 'Toggle to turn ON',
  isPropertyActive: false,
};

export function ToggleButtonGroup(props: IToggleButtonGroupProps) {
  return (
    <div>
      <div className="col-md-4 sm-label-left">
        <b>{props.propLabel}</b>
        {props.propHelpFieldId && <HelpField id={props.propHelpFieldId} />}
      </div>
      <div className="col-md-8 sm-label-left">
        <span className={'btn-group btn-group-' + props.toggleSize}>
          <button
            name="prop-off"
            type="button"
            className={`btn btn-default ${!props.isPropertyActive ? 'active btn-primary' : 'disabled'}`}
            onClick={() => props.onClick(false)}
          >
            <Tooltip value={props.tooltipPropOffBtn}>
              <span>{props.displayTextPropOffBtn}</span>
            </Tooltip>
          </button>
          <button
            name="prop-on"
            type="button"
            className={`btn btn-default ${props.isPropertyActive ? 'active btn-primary' : 'disabled'}`}
            onClick={() => props.onClick(true)}
          >
            <Tooltip value={props.tooltipPropOnBtn}>
              <span>{props.displayTextPropOnBtn}</span>
            </Tooltip>
          </button>
        </span>
      </div>
    </div>
  );
}
