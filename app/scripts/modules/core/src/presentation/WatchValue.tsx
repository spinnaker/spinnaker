import React from 'react';

interface IWatchValueProps<T> {
  value: T;
  onChange: (newValue: T, oldValue: T) => void;
  isEqual?: (newValue: T, oldValue: T) => boolean;
}

interface IWatchValueState<T> {
  value: T;
}

export class WatchValue<T = any> extends React.Component<IWatchValueProps<T>, IWatchValueState<T>> {
  public static defaultProps: Partial<IWatchValueProps<any>> = {
    isEqual: (newValue: any, oldValue: any) => newValue === oldValue,
  };

  constructor(props: IWatchValueProps<T>) {
    super(props);
    const { value } = props;
    this.state = { value };
  }

  public componentDidUpdate(): void {
    if (!this.props.onChange) {
      return;
    }

    const { value, isEqual } = this.props;
    const { value: prevValue } = this.state;

    if (!isEqual(value, prevValue)) {
      this.setState({ value });
      this.props.onChange(value, prevValue);
    }
  }

  public render() {
    return this.props.children || null;
  }
}
