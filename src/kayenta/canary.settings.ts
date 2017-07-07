import { SETTINGS } from '@spinnaker/core';

export interface ICanarySettings {
  liveCalls: boolean;
}

export const CanarySettings = <ICanarySettings>SETTINGS.canary;
