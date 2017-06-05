import { bootstrapModule } from './bootstrap.module';

bootstrapModule.config(require('core/forms/uiSelect.decorator'));

bootstrapModule.config((uiSelectConfig: any) => {
  'ngInject';
  uiSelectConfig.theme = 'select2';
  uiSelectConfig.appendToBody = true;
});
