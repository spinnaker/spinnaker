import { ITemplateInheritable } from './IPipeline';

export interface INotification extends ITemplateInheritable {
  level?: string;
  type: string;
  when: string[];
  [key: string]: any;
}
