import { UISref } from '@uirouter/react';
import React from 'react';

import { Spinner } from '../../widgets/spinners/Spinner';

interface IDetailsProps {
  loading: boolean;
}

interface IDetailsHeaderProps {
  icon: React.ReactNode;
  name: string;
  notifications?: React.ReactNode;
  actions?: React.ReactNode;
}

interface IDetailsContentProps {
  loading: boolean;
  children: React.ReactNode;
}

interface IDetailsSFCWithExtras extends React.SFC<IDetailsProps> {
  Header: React.SFC<IDetailsHeaderProps>;
  Content: React.FunctionComponent<IDetailsContentProps>;
}

const CloseButton = (
  <div className="close-button">
    <UISref to="^">
      <a className="btn btn-link">
        <span className="glyphicon glyphicon-remove" />
      </a>
    </UISref>
  </div>
);

const DetailsHeader: React.SFC<IDetailsHeaderProps> = (props) => (
  <div className="header">
    {CloseButton}
    <div className="header-text horizontal middle">
      {props.icon}
      <h3 className="horizontal middle space-between flex-1">
        {props.name}
        {props.notifications && props.notifications}
      </h3>
    </div>
    {props.actions && <div className="actions">{props.actions}</div>}
    <div>{props.children}</div>
  </div>
);

const loading = (
  <div className="header">
    <div className="horizontal center middle spinner-container">
      <Spinner />
    </div>
  </div>
);

const Details: IDetailsSFCWithExtras = (props) => (
  <div className="details-panel">{props.loading ? loading : props.children}</div>
);

const DetailsContent = ({ loading, children }: IDetailsContentProps) => (
  <div className="content">{loading ? loading : children}</div>
);

Details.Header = DetailsHeader;
Details.Content = DetailsContent;

export { Details };
