import { IModalService } from 'angular-ui-bootstrap';
import { ReactInjector } from 'core/react.module';

export let modalService: IModalService = undefined;
ReactInjector.give(($injector: any) => modalService = $injector.get('$uibModal') as any);
