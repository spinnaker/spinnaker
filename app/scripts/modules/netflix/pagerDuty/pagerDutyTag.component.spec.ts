import {Observable} from 'rxjs';
import {IComponentControllerService, mock} from 'angular';
import {IPagerDutyService, PagerDutyReader} from './pagerDuty.read.service';
import {PAGER_DUTY_TAG_COMPONENT, PagerDutyTagComponentController} from './pagerDutyTag.component';

describe('PagerDutyTagComponent', () => {

  let $componentController: IComponentControllerService,
      $ctrl: PagerDutyTagComponentController,
      pagerDutyReader: PagerDutyReader;

  const services: IPagerDutyService[] = [
    {name: 'name1', integration_key: 'integrationKey1'},
    {name: 'name2', integration_key: 'integrationKey2'},
    {name: 'name3', integration_key: 'integrationKey3'}
  ];

  const initialize = (apiKey: string) => {
    $ctrl = <PagerDutyTagComponentController> $componentController(
      'pagerDutyTag',
      { $scope: null, pagerDutyReader },
      { apiKey: apiKey}
    );
    $ctrl.$onInit();
  };

  beforeEach(mock.module(PAGER_DUTY_TAG_COMPONENT));

  beforeEach(mock.inject((_$componentController_: IComponentControllerService,
                          _pagerDutyReader_: PagerDutyReader) => {
    $componentController = _$componentController_;
    pagerDutyReader = _pagerDutyReader_;
    spyOn(pagerDutyReader, 'listServices').and.returnValue(Observable.of(services));
  }));

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
