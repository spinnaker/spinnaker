import { IModalProvider, ITooltipProvider } from 'angular-ui-bootstrap';

import { bootstrapModule } from './bootstrap.module';

bootstrapModule.config([
  '$uibTooltipProvider',
  ($uibTooltipProvider: ITooltipProvider) => {
    $uibTooltipProvider.options({
      appendToBody: true,
    });

    $uibTooltipProvider.setTriggers({
      'mouseenter focus': 'mouseleave blur',
    });
  },
]);

bootstrapModule.config([
  '$uibModalProvider',
  ($uibModalProvider: IModalProvider) => {
    $uibModalProvider.options.backdrop = 'static';
    $uibModalProvider.options.keyboard = false;
  },
]);
