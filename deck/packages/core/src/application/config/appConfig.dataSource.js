import { ApplicationDataSourceRegistry } from '../service/ApplicationDataSourceRegistry';

export const CORE_APPLICATION_CONFIG_APPCONFIG_DATASOURCE = 'spinnaker.core.application.config.dataSource';
export const name = CORE_APPLICATION_CONFIG_APPCONFIG_DATASOURCE; // for backwards compatibility

export function registerApplicationConfigDataSource() {
  ApplicationDataSourceRegistry.registerDataSource({
    key: 'config',
    label: 'Config',
    sref: '.config',
    active: '**.config.**',
    defaultData: [],
    iconName: 'spMenuConfig',
  });
}

registerApplicationConfigDataSource();
