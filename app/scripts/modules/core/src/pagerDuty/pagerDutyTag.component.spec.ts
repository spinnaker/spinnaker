import { of as observableOf } from 'rxjs';
import { IComponentControllerService, mock } from 'angular';
import { IPagerDutyService, PagerDutyReader } from './pagerDuty.read.service';
import { PAGER_DUTY_TAG_COMPONENT, PagerDutyTagComponentController } from './pagerDutyTag.component';

describe('PagerDutyTagComponent', () => {
  let $componentController: IComponentControllerService, $ctrl: PagerDutyTagComponentController;

  const services: IPagerDutyService[] = [
    {
      name: 'name1',
      integration_key: 'integrationKey1',
      id: '1',
      policy: 'ABCDEF',
      lastIncidentTimestamp: '1970',
      status: 'active',
    },
    {
      name: 'name2',
      integration_key: 'integrationKey2',
      id: '2',
      policy: 'ABCDEG',
      lastIncidentTimestamp: '1970',
      status: 'active',
    },
    {
      name: 'name3',
      integration_key: 'integrationKey3',
      id: '3',
      policy: 'ABCDEH',
      lastIncidentTimestamp: '1970',
      status: 'active',
    },
  ];

  const initialize = (apiKey: string) => {
    $ctrl = $componentController('pagerDutyTag', { $scope: null }, { apiKey }) as PagerDutyTagComponentController;
    $ctrl.$onInit();
  };

  beforeEach(mock.module(PAGER_DUTY_TAG_COMPONENT));

  beforeEach(
    mock.inject((_$componentController_: IComponentControllerService) => {
      $componentController = _$componentController_;
      spyOn(PagerDutyReader, 'listServices').and.returnValue(observableOf(services));
    }),
  );

  it('should set notFound flag when service is not found for api key', () => {
    initialize('invalidKey');
    expect($ctrl.servicesLoaded).toBe(true);
    expect($ctrl.currentService).toBe(undefined);
  });

  it('should set the current service', () => {
    initialize('integrationKey2');
    expect($ctrl.currentService).toBe(services[1]);
  });

  it('should update the current service when key changes', () => {
    initialize('integrationKey2');
    expect($ctrl.currentService).toBe(services[1]);
    $ctrl.apiKey = 'integrationKey3';
    $ctrl.$onChanges();
    expect($ctrl.currentService).toBe(services[2]);
  });
});
