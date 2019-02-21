import { bootstrapModule } from './bootstrap.module';
import { ITooltipProvider, IModalProvider } from 'angular-ui-bootstrap';

bootstrapModule.config(['$uibTooltipProvider', ($uibTooltipProvider: ITooltipProvider) => {
  'ngInject';
  $uibTooltipProvider.options({
    appendToBody: true,
  });

  $uibTooltipProvider.setTriggers({
    'mouseenter focus': 'mouseleave blur',
  });
}]);

bootstrapModule.config(['$uibModalProvider', ($uibModalProvider: IModalProvider) => {
  'ngInject';
  $uibModalProvider.options.backdrop = 'static';
  $uibModalProvider.options.keyboard = false;
}]);
