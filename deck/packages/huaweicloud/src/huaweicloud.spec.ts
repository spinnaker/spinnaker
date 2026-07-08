describe('HuaweiCloud package entrypoint', () => {
  it('loads successfully', () => {
    expect(() => require('./index')).not.toThrow();
  });

  it('does not expose an Angular module token', () => {
    const huaweicloudPackage = require('./index');

    expect((huaweicloudPackage as any).HUAWEICLOUD_MODULE).toBeUndefined();
  });
});
