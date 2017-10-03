import * as React from 'react';

export interface IFormListProps {
  children: JSX.Element[] | JSX.Element;
}

/*
* Mostly exists to centralize styles for form components.
* */
export default function FormList({ children }: IFormListProps) {
  return (
    <ul className="list-group">
      {React.Children.map(children, c => c &&
        <li className="list-unstyled">
          <form role="form" className="form-horizontal container-fluid">
            <div className="col-md-12">{c}</div>
          </form>
        </li>
      )}
    </ul>
  );
}
