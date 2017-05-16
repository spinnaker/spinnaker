import * as React from 'react';
import { angular2react } from 'angular2react';

import { Application } from 'core/application/application.model';
import { EntityUiTagsWrapperComponent } from './entityUiTags.component';
import { ITaggedEntity } from 'core/domain';
import { ReactInjector } from 'core/react';

interface IProps {
  component: ITaggedEntity,
  application: Application,
  pageLocation: string;
  onUpdate: () => void;
  entityType: string;
}

export let EntityUiTags: React.ComponentClass<IProps>;
ReactInjector.give(($injector: any) => EntityUiTags = angular2react('entityUiTagsWrapper', new EntityUiTagsWrapperComponent(), $injector) as any);
