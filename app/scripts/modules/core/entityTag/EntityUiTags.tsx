import { Application } from 'core/application/application.model';
import { ITaggedEntity } from 'core/domain';

export interface IEntityUiTagsProps {
  component: ITaggedEntity,
  application: Application,
  pageLocation: string;
  onUpdate: () => void;
  entityType: string;
}
