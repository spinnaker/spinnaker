import { IModalService } from 'angular-ui-bootstrap';

export let modalService: IModalService = undefined;
export const ModalServiceInject = ($injector: any) => {
  modalService = <IModalService>$injector.get('$uibModal');
};
