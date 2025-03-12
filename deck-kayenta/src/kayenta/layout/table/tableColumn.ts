export interface ITableColumn<T> {
  label?: string;
  labelClassName?: string;
  hide?: (data: T) => boolean;
  width?: number;
  getContent: (data: T) => JSX.Element;
}
