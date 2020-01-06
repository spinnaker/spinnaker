import React from 'react';
import { INotificationSettings } from 'core/config';

export interface INotificationTypeConfig {
  label: string;
  key: keyof INotificationSettings;
  config?: INotificationTypeCustomConfig;
  component?: React.ComponentType;
}

export interface INotificationTypeCustomConfig {
  fieldName?: string;
  botName?: string;
}
