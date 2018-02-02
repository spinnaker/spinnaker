import * as React from 'react';
import * as classNames from 'classnames';

export interface IListDetailProps {
  className?: string,
  list: JSX.Element,
  listClass?: string;
  listWidth?: number;
  detail: JSX.Element;
  detailWidth?: number;
  detailClass?: string;
}

/*
 * A composite view that combines a list view in one panel and a detail view in another.
 * The contents of the detail view are dependent on the user's selection in the list view.
 */
export default function ListDetail({ className, list, listWidth = 2, detail, detailWidth = 10, listClass, detailClass }: IListDetailProps) {
  return (
    <div className={classNames('list-detail', 'horizontal', className)}>
      <div className={classNames(`col-md-${listWidth}`, listClass)}>
        {list}
      </div>
      <div className={classNames(`col-md-${detailWidth}`, detailClass)}>
        {detail}
      </div>
    </div>
  );
}
