export interface IDelegate {
  name: string;
}

export const buildDelegateService = <T extends IDelegate>() => {
  class DelegateService {
    private delegates: T[] = [];

    public register(delegate: T): void {
      this.delegates.push(delegate);
    }

    public getDelegate(name: string): T {
      return this.delegates.find(d => d.name === name);
    }
  }

  return new DelegateService();
};
