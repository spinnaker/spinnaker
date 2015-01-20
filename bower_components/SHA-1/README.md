SHA-1
===

This is a SHA-1 hash generator by JavaScript.

## Get started

You can use [bower](http://bower.io/) to install the component:

```
$ bower install SHA-1
```

```js
sha1('hello') // aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d
```
If you use RequireJS

```js
require(['./sha1'], function(sha1){
    sha1('hello'); // aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d
});
```

You can also use the package in Node

```
$ npm install sha-1
```

```js
$ node
> sha1 = require('./sha1')
{ [Function] sha1: [Circular] }
> sha1('hello')
'aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d'
```

## License

Licensed under MIT
