import { cloneDeep, merge } from 'lodash';

export interface IProviderSettings {
  defaults: any;
  resetToOriginal?: () => void;
}

export interface INotificationSettings {
  email: {
    enabled: boolean;
  };
  bearychat: {
    enabled: boolean;
  };
  googlechat: {
    enabled: boolean;
  };
  hipchat: {
    botName: string;
    enabled: boolean;
  };
  sms: {
    enabled: boolean;
  };
  slack: {
    botName: string;
    enabled: boolean;
  };
  githubstatus: {
    enabled: boolean;
  };
}

export interface IFeatures {
  [key: string]: any;
  canary?: boolean;
  chaosMonkey?: boolean;
  displayTimestampsInUserLocalTime?: boolean;
  dockerBake?: boolean;
  entityTags?: boolean;
  fiatEnabled?: boolean;
  iapRefresherEnabled?: boolean;
  // whether stages affecting infrastructure (like "Create Load Balancer") should be enabled or not
  infrastructureStages?: boolean;
  jobs?: boolean;
  managedPipelineTemplatesV2UI?: boolean;
  managedServiceAccounts?: boolean;
  notifications?: boolean;
  pagerDuty?: boolean;
  pipelines?: boolean;
  pipelineTemplates?: boolean;
  quietPeriod?: boolean;
  roscoMode?: boolean;
  snapshots?: boolean;
  travis?: boolean;
  triggerViaEcho?: boolean;
  versionedProviders?: boolean;
  wercker?: boolean;
}

export interface IDockerInsightSettings {
  enabled: boolean;
  url: string;
}

export interface ISpinnakerSettings {
  [key: string]: any;

  analytics: {
    customConfig?: {
      siteSpeedSampleRate?: number;
    };
    ga?: string;
  };
  authEnabled: boolean;
  authEndpoint: string;
  authTtl: number;
  bakeryDetailUrl: string;
  changelog?: {
    accessToken?: string;
    fileName: string;
    gistId: string;
  };
  checkForUpdates: boolean;
  debugEnabled: boolean;
  defaultCategory: string;
  defaultInstancePort: number;
  defaultProviders: string[];
  defaultTimeZone: string; // see http://momentjs.com/timezone/docs/#/data-utilities/
  dockerInsights: IDockerInsightSettings;
  entityTags?: {
    maxUrlLength?: number;
  };
  executionWindow?: {
    atlas?: {
      regions: Array<{ label: string; baseUrl: string }>;
      url: string;
    };
  };
  feature: IFeatures;
  feedback?: {
    icon?: string;
    text?: string;
    url: string;
  };
  gateUrl: string;
  gitSources: string[];
  maxPipelineAgeDays: number;
  notifications: INotificationSettings;
  onDemandClusterThreshold: number;
  pagerDuty?: {
    accountName?: string;
    defaultDetails?: string;
    defaultSubject?: string;
    required?: boolean;
  };
  pollSchedule: number;
  providers?: {
    [key: string]: IProviderSettings; // allows custom providers not typed in here (good for testing too)
  };
  pubsubProviders: string[];
  quietPeriod: [string | number, string | number];
  resetProvider: (provider: string) => () => void;
  resetToOriginal: () => void;
  searchVersion: 1 | 2;
  triggerTypes: string[];
  useClassicFirewallLabels: boolean;
}

export const SETTINGS: ISpinnakerSettings = (window as any).spinnakerSettings;

// Make sure to set up some reasonable default settings fields so we do not have to keep checking if they exist everywhere
SETTINGS.feature = SETTINGS.feature || {};
SETTINGS.analytics = SETTINGS.analytics || {};
SETTINGS.providers = SETTINGS.providers || {};
SETTINGS.defaultTimeZone = SETTINGS.defaultTimeZone || 'America/Los_Angeles';
SETTINGS.dockerInsights = SETTINGS.dockerInsights || { enabled: false, url: '' };

// A helper to make resetting settings to steady state after running tests easier
const originalSettings: ISpinnakerSettings = cloneDeep(SETTINGS);
SETTINGS.resetToOriginal = () => {
  Object.keys(SETTINGS)
    .filter(k => typeof SETTINGS[k] !== 'function') // maybe don't self-destruct
    .forEach(k => delete SETTINGS[k]);
  merge(SETTINGS, originalSettings);
};

SETTINGS.resetProvider = (provider: string) => {
  return () => {
    const providerSettings: IProviderSettings = SETTINGS.providers[provider];
    Object.keys(providerSettings)
      .filter(k => typeof (providerSettings as any)[k] !== 'function')
      .forEach(k => delete (providerSettings as any)[k]);
    merge(providerSettings, originalSettings.providers[provider]);
  };
};
