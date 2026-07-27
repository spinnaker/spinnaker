import { ApplicationDataSourceRegistry } from '../service/ApplicationDataSourceRegistry';

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
