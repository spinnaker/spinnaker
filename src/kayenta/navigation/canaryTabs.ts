import { CanarySettings } from 'kayenta/canary.settings';

export interface ICanaryHeaderTabConfig {
  title: string;
  sref: string;
  activeStates: string[];
  hide?: boolean;
}

export const canaryTabs: ICanaryHeaderTabConfig[] = [
  {
    title: 'configurations',
    sref: 'home.applications.application.canary.configDefault',
    activeStates: ['**.configDefault', '**.configDetail'],
  },
  {
    title: 'reports',
    sref: 'home.applications.application.canary.reports',
    activeStates: [],
    hide: !CanarySettings.reportsEnabled,
  },
];
