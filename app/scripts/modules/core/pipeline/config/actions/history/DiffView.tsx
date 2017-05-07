import * as React from 'react';
import { angular2react } from 'angular2react';
import { IJsonDiff } from 'core/utils/json/json.utility.service';
import { ReactInjector } from 'core/react.module';
import { diffViewComponent } from './diffView.component';

interface IProps {
  diff: IJsonDiff;
}

export let DiffView: React.ComponentClass<IProps> = undefined;
ReactInjector.give(($injector: any) => DiffView = angular2react<IProps>('diffView', diffViewComponent, $injector));
