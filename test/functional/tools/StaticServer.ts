/// <reference path="wait-on.d.ts" />

const waitOn = require('wait-on');
const configure = require('../../../webpack.config.js');
const webpack = require('webpack');
const middleware = require('webpack-dev-middleware');
const express = require('express');
const app = express();

const WAIT_INTERVAL_MS = 500;
const WAIT_TIMEOUT_MS = 300000;

export class StaticServer {
  private server: any;

  constructor(private repoRoot: string) {}

  public launch(): Promise<void | Error> {
    const webpackConfig = configure(
      {},
      {
        context: this.repoRoot,
      },
    );
    const compiler = webpack(webpackConfig);
    app.use(
      middleware(compiler, {
        publicPath: '/',
      }),
    );

    this.server = app.listen(9000, () => console.log('webpack-dev-middleware listening on port 9000'));
    return waitOn({
      interval: WAIT_INTERVAL_MS,
      timeout: WAIT_TIMEOUT_MS,
      resources: ['http-get://localhost:9000'],
    }).catch((err: any) => {
      this.kill();
      throw new Error(`failed to launch webpack-dev-server: ${err}`);
    });
  }

  public kill(): void {
    this.server && this.server.close();
    this.server = null;
  }
}
