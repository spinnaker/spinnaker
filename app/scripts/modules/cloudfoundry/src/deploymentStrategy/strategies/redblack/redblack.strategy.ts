import { IServerGroupCommand } from '@spinnaker/core';

export interface IRedBlackCommand extends IServerGroupCommand {
  maxRemainingAsgs: number;
  delayBeforeDisableSec: number;
  delayBeforeScaleDownSec: number;
  rollback: {
    onFailure: boolean;
  };
  scaleDown: boolean;
}
