import React from 'react';

import { IReactSelectInputProps, ReactSelectInput, Tooltip } from '../../../presentation';

interface IRefreshableReactSelectInputProps extends IReactSelectInputProps {
  onRefreshClicked: () => void;
  refreshButtonTooltipText: string;
}

/**
 * A react-select input with a refresh button on the right.
 * When refreshing, the select is disabled and the refresh icon spins
 */
export function RefreshableReactSelectInput(props: IRefreshableReactSelectInputProps) {
  const { disabled, isLoading, onRefreshClicked, refreshButtonTooltipText, ...rest } = props;
  const spinnerClassName = 'sp-margin-m fa fa-sync-alt ' + (isLoading ? 'fa-spin' : '');

  return (
    <div className="flex-container-h middle">
      <ReactSelectInput
        {...(rest as any)}
        isLoading={isLoading}
        disabled={isLoading || disabled}
        className="flex-grow"
      />
      <Tooltip placement="right" value={refreshButtonTooltipText}>
        <span className={spinnerClassName} onClick={onRefreshClicked} style={{ cursor: 'pointer' }} />
      </Tooltip>
    </div>
  );
}
