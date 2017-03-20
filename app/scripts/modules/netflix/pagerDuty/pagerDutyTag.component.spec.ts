import {Observable} from 'rxjs';
import {DebugElement, SimpleChange} from '@angular/core';
import {By} from '@angular/platform-browser';
import {async, ComponentFixture, TestBed} from '@angular/core/testing';

import {IPagerDutyService, PagerDutyReader} from './pagerDuty.read.service';
import {PagerDutyTagComponent} from './pagerDutyTag.component';

describe('PagerDutyTagComponent', () => {

  let component: PagerDutyTagComponent;
  let fixture: ComponentFixture<PagerDutyTagComponent>;
  let debugElement: DebugElement;
  let htmlElement: HTMLElement;

  const services: IPagerDutyService[] = [
    {name: 'name1', integration_key: 'integrationKey1'},
    {name: 'name2', integration_key: 'integrationKey2'},
    {name: 'name3', integration_key: 'integrationKey3'}
  ];
  const pagerDutyReaderStub = {
    listServices: () => {
      return Observable.of(services);
    }
  };

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [PagerDutyTagComponent]
    }).overrideComponent(PagerDutyTagComponent, {
      set: {
        providers: [
          {provide: PagerDutyReader, useValue: pagerDutyReaderStub}
        ]
      }
    });

    fixture = TestBed.createComponent(PagerDutyTagComponent);
    component = fixture.componentInstance;
    debugElement = fixture.debugElement.query(By.css('span'));
    htmlElement = debugElement.nativeElement;
  }));

  it('should display a not-found message services are loaded but a match not found', () => {

    component.apiKey = 'invalidKey';
    fixture.detectChanges();
    const span: Node = Array.from(htmlElement.childNodes).find(node => node.nodeName === 'SPAN');
    const expected = `Unable to locate PagerDuty key (${component.apiKey})`;
    expect(span.textContent.trim()).toBe(expected);
  });

  it('should display the pager duty text and key when the services have been loaded', () => {

    component.apiKey = 'integrationKey2';
    fixture.detectChanges();

    const span: Node = Array.from(htmlElement.childNodes).find(node => node.nodeName === 'SPAN');
    const expected = `${component.currentService.name} (${component.currentService.integration_key})`;
    expect(span.textContent.trim()).toBe(expected);
  });

  it('should update the pagerduty text and key when the api key has been changed', () => {

    component.apiKey = 'integrationKey2';
    fixture.detectChanges();
    expect(component.currentService.name).toBe('name2');

    component.ngOnChanges({
      apiKey: new SimpleChange(null, 'integrationKey3', false)
    });
    fixture.detectChanges();
    expect(component.currentService.name).toBe('name3');
    expect(component.currentService.integration_key).toBe('integrationKey3');

    const span: Node = Array.from(htmlElement.childNodes).find(node => node.nodeName === 'SPAN');
    const expected = `${component.currentService.name} (${component.currentService.integration_key})`;
    expect(span.textContent.trim()).toBe(expected);
  });
});
