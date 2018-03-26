import { navigationCategoryRegistry } from './navigationCategory.registry';

export const INFRASTRUCTURE_KEY = 'infrastructure';
export const DELIVERY_KEY = 'delivery';

navigationCategoryRegistry.register({
  key: DELIVERY_KEY,
  label: 'Delivery',
  icon: 'fa fa-fw fa-xs fa-tasks',
  primary: true,
  order: 100,
});

navigationCategoryRegistry.register({
  key: INFRASTRUCTURE_KEY,
  label: 'Infrastructure',
  icon: 'fa fa-fw fa-xs fa-cloud',
  primary: true,
  order: 200,
});
