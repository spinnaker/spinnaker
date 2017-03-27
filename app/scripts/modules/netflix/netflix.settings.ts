import { IFeatures, ISpinnakerSettings, SETTINGS } from 'core/config/settings';

interface INetflixFeatures extends IFeatures {
  blesk?: boolean;
  fastProperty?: boolean;
  netflixMode?: boolean;
  tableau?: boolean;
  vpcMigrator?: boolean;
}

export interface INetflixSettings extends ISpinnakerSettings {
  chap?: {
    canaryReportBaseUrl: string;
    chapBaseUrl: string;
    fitBaseUrl: string;
  };
  feedback?: {
    slack?: {
      team: string;
      teamName: string;
      helpChannel: string;
      helpChannelName: string;
    }
  };
  feedbackUrl?: string;
  tableau?: {
    appSourceUrl: string;
    summarySourceUrl: string;
  };
  whatsNew?: {
    gistId: string;
    fileName: string;
    accessToken?: string;
  };
  feature: INetflixFeatures;
}

export const NetflixSettings: INetflixSettings = <INetflixSettings>SETTINGS;
