export interface ITableColumn<T> {
  label?: string;
  labelClassName?: string;
  hide?: boolean;
  width: number;
  getContent: (data: T) => JSX.Element;
}
