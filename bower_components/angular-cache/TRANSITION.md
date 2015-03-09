### 2.x.x. ---> 3.x.x - xx April 2013
TODO

### 1.x.x. ---> 2.0.0 - 30 October 2013

#### Breaking API changes
##### Swapped `aggressiveDelete` option for `deleteOnExpire` option.

###### 1.x.x
Aggressively delete expiring items.
```javascript
$angularCacheFactory('myNewCache', {
    maxAge: 90000, // Items added to this cache expire after 15 minutes
    aggressiveDelete: true // Items will be actively deleted when they expire
});
```

Passively delete items when they are requested after they have expired.
```javascript
$angularCacheFactory('myNewCache', {
    maxAge: 90000, // Items added to this cache expire after 15 minutes
    aggressiveDelete: false // Items will be actively deleted when they expire
});
```

###### 2.0.0
Aggressively delete expiring items.
```javascript
$angularCacheFactory('myNewCache', {
    maxAge: 90000, // Items added to this cache expire after 15 minutes
    deleteOnExpire: 'aggressive' // Items will be actively deleted when they expire
});
```

Passively delete items when they are requested after they have expired.
```javascript
$angularCacheFactory('myNewCache', {
    maxAge: 90000, // Items added to this cache expire after 15 minutes
    deleteOnExpire: 'passive' // Items will be passively deleted when requested after expiration
});
```

Do nothing with expired items (not in 1.x.x).
```javascript
$angularCacheFactory('myNewCache', {
    maxAge: 90000, // Items added to this cache expire after 15 minutes
    deleteOnExpire: 'none' // Items will expire but not be removed
});
```

##### Substituted `localStorageImpl` and `sessionStorageImpl` options for just `storageImpl` option.

###### 1.x.x
```javascript
$angularCacheFactory('myNewCache', {
    storageMode: 'localStorage',
    localStorageImpl: myLocalStoragePolyfill // Use custom localStorage implementation
});

$angularCacheFactory('myNewCache2', {
    storageMode: 'sessionStorage',
    sessionStorageImpl: mySessionStoragePolyfill // Use custom sessionStorage implementation
});
```

###### 2.0.0
```javascript
$angularCacheFactory('myNewCache', {
    storageMode: 'localStorage',
    storageImpl: myLocalStoragePolyfill // Use custom localStorage implementation
});

$angularCacheFactory('myNewCache2', {
    storageMode: 'sessionStorage',
    storageImpl: mySessionStoragePolyfill // Use custom sessionStorage implementation
});
```

##### Installation
The Bower package now contains only `dist/angular-cache.js` and `dist/angular-cache.min.js`.

##### onExpire

###### 1.x.x
```javascript
cache.get('someKey', function (key, value) {
    // do something with expired item
});
```

###### 2.0.0
```javascript
cache.get('someKey', {
    onExpire: function (key, value) {
        // do something with expired item
    }
});
```
