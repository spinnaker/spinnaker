import { IViewChangesConfig } from './viewChangesLink.component';

export interface IViewChangesLinkProps {
    changeConfig: IViewChangesConfig;
    viewType?: string;
    linkText?: string;
    nameItem: { name: string };
}
