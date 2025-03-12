import type { IDeckPlugin } from '@spinnaker/core';
import { widgetizeStage } from './WidgetizeStage';

export const plugin: IDeckPlugin = {
  stages: [widgetizeStage],
};
