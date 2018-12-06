import { Application } from 'core/application';
import { IOwnerOption } from 'core/entityTag';

export interface IAddEntityTagLinksProps {
  application: Application;
  component: any;
  entityType: string;
  onUpdate?: () => any;
  ownerOptions?: IOwnerOption[];
  tagType?: string;
}
