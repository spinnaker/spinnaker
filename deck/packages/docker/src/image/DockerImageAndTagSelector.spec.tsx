import { DockerChartAndTagSelector } from './DockerChartAndTagSelector';
import { DockerImageAndTagSelector } from './DockerImageAndTagSelector';
import { DockerChartImageReader, DockerImageReader } from './DockerImageReader';
import { AccountService } from '@spinnaker/core';

interface IDeferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => (resolve = promiseResolve));
  return { promise, resolve };
}

describe('Docker image selectors', () => {
  const props = {
    specifyTagByRegex: false,
    imageId: '',
    organization: '',
    registry: '',
    repository: '',
    tag: '',
    digest: '',
    account: '',
    onChange: () => undefined,
  };

  it('ignores image records without repositories when grouping account organizations', () => {
    const images = [
      { account: 'test', registry: 'registry.example', repository: undefined },
      { account: 'test', registry: 'registry.example', repository: 'spinnaker/deck', tag: 'latest' },
    ];

    expect((new DockerImageAndTagSelector(props) as any).getAccountMap(images)).toEqual({ test: ['spinnaker'] });
    expect((new DockerChartAndTagSelector(props) as any).getAccountMap(images)).toEqual({ test: ['spinnaker'] });
  });

  it('ignores non-Docker image records when no repositories are present', () => {
    const images = [
      { account: 'gce', imageName: 'ubuntu-1804-bionic-v20181003' },
      { account: 'compute-engine', imageName: 'ubuntu-1804-bionic-v20181003' },
    ];

    expect((new DockerImageAndTagSelector(props) as any).getAccountMap(images)).toEqual({});
    expect((new DockerChartAndTagSelector(props) as any).getAccountMap(images)).toEqual({});
  });

  [
    { name: 'image', Selector: DockerImageAndTagSelector, Reader: DockerImageReader },
    { name: 'chart', Selector: DockerChartAndTagSelector, Reader: DockerChartImageReader },
  ].forEach(({ name, Selector, Reader }) => {
    it(`aborts replaced and unmounted ${name} loads without applying late state`, async () => {
      const firstRequest = deferred<any[]>();
      const secondRequest = deferred<any[]>();
      const findImages = spyOn(Reader, 'findImages').and.returnValues(firstRequest.promise, secondRequest.promise);
      const component = new Selector(props);
      const setState = spyOn(component, 'setState');

      component.refreshImages(props);
      component.refreshImages({ ...props, account: 'replacement' });
      const firstSignal = findImages.calls.argsFor(0)[1] as AbortSignal;
      const secondSignal = findImages.calls.argsFor(1)[1] as AbortSignal;

      expect(firstSignal.aborted).toBe(true);
      expect(secondSignal.aborted).toBe(false);
      component.componentWillUnmount();
      expect(secondSignal.aborted).toBe(true);

      firstRequest.resolve([]);
      secondRequest.resolve([]);
      await Promise.all([firstRequest.promise, secondRequest.promise]);
      await Promise.resolve();

      expect(setState.calls.allArgs()).toEqual([[{ imagesLoading: true }], [{ imagesLoading: true }]]);
    });

    it(`preserves a pending ${name} load across unrelated prop updates`, async () => {
      const request = deferred<any[]>();
      const findImages = spyOn(Reader, 'findImages').and.returnValue(request.promise);
      const component = new Selector(props);
      spyOn(component, 'setState');
      const updateThings = spyOn(component as any, 'updateThings').and.callThrough();
      const replacementProps = { ...props, repository: 'replacement' };

      component.refreshImages(props);
      component.componentWillReceiveProps(replacementProps);
      (component as any).props = replacementProps;
      const signal = findImages.calls.argsFor(0)[1] as AbortSignal;

      expect(findImages).toHaveBeenCalledTimes(1);
      expect(signal.aborted).toBe(false);

      updateThings.calls.reset();
      request.resolve([]);
      await request.promise;
      await Promise.resolve();

      expect(updateThings).toHaveBeenCalledOnceWith(replacementProps, true);
      component.componentWillUnmount();
    });

    it(`replaces a pending ${name} load when its effective registry account changes`, async () => {
      const firstRequest = deferred<any[]>();
      const secondRequest = deferred<any[]>();
      const findImages = spyOn(Reader, 'findImages').and.returnValues(firstRequest.promise, secondRequest.promise);
      const initialProps = { ...props, registry: 'initial', showRegistry: false };
      const replacementProps = { ...initialProps, registry: 'replacement' };
      const component = new Selector(initialProps);
      spyOn(component, 'setState');

      component.refreshImages(initialProps);
      component.componentWillReceiveProps(replacementProps);
      (component as any).props = replacementProps;
      const firstSignal = findImages.calls.argsFor(0)[1] as AbortSignal;
      const secondSignal = findImages.calls.argsFor(1)[1] as AbortSignal;

      expect(findImages.calls.argsFor(1)[0].account).toBe('replacement');
      expect(firstSignal.aborted).toBe(true);
      expect(secondSignal.aborted).toBe(false);

      component.componentWillUnmount();
      firstRequest.resolve([]);
      secondRequest.resolve([]);
      await Promise.all([firstRequest.promise, secondRequest.promise]);
    });

    it(`uses current props when delayed account initialization refreshes ${name} loads`, async () => {
      const accountsRequest = deferred<any[]>();
      const firstImageRequest = deferred<any[]>();
      const secondImageRequest = deferred<any[]>();
      spyOn(AccountService, 'listAccounts').and.returnValue(accountsRequest.promise);
      const findImages = spyOn(Reader, 'findImages').and.returnValues(
        firstImageRequest.promise,
        secondImageRequest.promise,
      );
      const initialProps = { ...props, account: 'initial', showRegistry: true };
      const replacementProps = { ...initialProps, account: 'replacement' };
      const component = new Selector(initialProps);
      spyOn(component, 'setState');

      (component as any).initializeAccounts(initialProps);
      (component as any).props = replacementProps;
      component.refreshImages(replacementProps);
      accountsRequest.resolve([{ name: 'default' }]);
      await accountsRequest.promise;
      await Promise.resolve();

      expect(findImages.calls.argsFor(0)[0].account).toBe('replacement');
      expect(findImages.calls.argsFor(1)[0].account).toBe('replacement');

      component.componentWillUnmount();
      firstImageRequest.resolve([]);
      secondImageRequest.resolve([]);
      await Promise.all([firstImageRequest.promise, secondImageRequest.promise]);
    });
  });
});
