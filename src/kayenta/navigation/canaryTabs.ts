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
    sref: 'home.applications.application.canary.canaryConfig.configDefault',
    activeStates: ['**.configDefault', '**.configDetail'],
  },
  {
    title: 'report',
    sref: 'home.applications.application.canary.report.reportDefault',
    activeStates: ['**.report.**'],
    hide: !CanarySettings.reportsEnabled,
  },
];
