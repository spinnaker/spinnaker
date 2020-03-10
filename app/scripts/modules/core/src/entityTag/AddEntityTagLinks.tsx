import { Application } from 'core/application';
import { IOwnerOption } from './EntityTagEditor';

export interface IAddEntityTagLinksProps {
  application: Application;
  component: any;
  entityType: string;
  onUpdate?: () => any;
  ownerOptions?: IOwnerOption[];
  tagType?: string;
}
