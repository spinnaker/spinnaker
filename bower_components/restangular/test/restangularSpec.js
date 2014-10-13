describe("Restangular", function() {
  // API
  var Restangular, $httpBackend;
  var accountsModel, restangularAccounts, restangularAccount0, restangularAccount1;
  var accountsHalModel;
  var messages, newAccount;

  // Load required modules
  beforeEach(angular.mock.module("restangular"));

  // Init HTTP mock backend and Restangular resources
  beforeEach(inject(function($injector) {
    // Model
    accountsModel = [
      {id: 0, user: "Martin ", amount: 42, transactions: []},
      {id: 1, user: "Paul", amount: 3.1416, transactions: [{from: "Martin", amount: 3, id: 0}, {from: "Anonymous", amount: 0.1416, id:1}]}
    ];

    // HAL model (http://stateless.co/hal_specification.html)
    accountsHalModel = [
      {id: 0, user: "Martin", amount: 42, transaction: [], _links: {self: "/accountsHAL/martin"}},
      {id: 1, user: "Paul", amount: 3.1416, transaction: [
        {from: "Martin", amount: 3, id: 0, _links: {self: "/accountsHAL/paul/transactions/0"}},
        {from: "Anonymous", amount: 0.1416, id: 1, _links: {self: "/accountsHAL/paul/transactions/1"}}
      ], _links: {self: "/accountsHAL/paul"}}
    ];

    infoModel = {
      id: 0, text: "Some additional account information"
    }

    newAccount = {id: 44, user: "First User", amount: 45, transactions: []};

    messages = [{id: 23, name: "Gonto"}, {id: 45, name: "John"}]


    $httpBackend = $injector.get("$httpBackend");

    $httpBackend.when("HEAD", "/accounts").respond();
    $httpBackend.when("TRACE", "/accounts").respond();
    $httpBackend.when("OPTIONS", "/accounts").respond();

    $httpBackend.whenGET("/accounts").respond(accountsModel);
    $httpBackend.whenJSONP("/accounts").respond(accountsModel);
    $httpBackend.whenGET("/accounts/0,1").respond(accountsModel);
    $httpBackend.whenGET("/accounts/messages").respond(messages);
    $httpBackend.whenGET("/accounts/1/message").respond(messages[0]);
    $httpBackend.whenGET("/accounts/1/messages").respond(messages);
    $httpBackend.whenGET("/accounts/0").respond(accountsModel[0]);
    $httpBackend.whenGET("/accounts/1").respond(accountsModel[1]);
    $httpBackend.whenJSONP("/accounts/1").respond(accountsModel[1]);
    $httpBackend.whenGET("/accounts/1/transactions").respond(accountsModel[1].transactions);
    $httpBackend.whenGET("/accounts/1/transactions/1").respond(accountsModel[1].transactions[1]);

    $httpBackend.whenGET("/info").respond(infoModel);
    $httpBackend.whenGET("/accounts/1/info").respond(infoModel);
    $httpBackend.whenPUT("/info").respond(function(method, url, data) {
      return [200, data, ""];
    });

    $httpBackend.whenGET("/accountsHAL").respond(accountsHalModel);
    $httpBackend.whenPUT("/accountsHAL/martin").respond(function(method, url, data) {
      accountsHalModel[0] = angular.fromJson(data);
      return [200, data, ""];
    });

    // Full URL
    $httpBackend.whenGET('http://accounts.com/all').respond(accountsModel);

    $httpBackend.whenPOST("/accounts").respond(function(method, url, data, headers) {
      var newData = angular.fromJson(data);
      newData.fromServer = true;
      return [201, JSON.stringify(newData), ""];
    });

    $httpBackend.whenPOST("/accounts/1/transactions").respond(function(method, url, data, headers) {
      return [201, "", ""];
    });

    $httpBackend.whenDELETE("/accounts/1/transactions/1").respond(function(method, url, data, headers) {
      return [200, "", ""];
    });

    $httpBackend.whenDELETE("/accounts/1").respond(function(method, url, data, headers) {
      return [200, "", ""];
    });

    $httpBackend.whenPOST("/accounts/1").respond(function(method, url, data, headers) {
      return [200, "", ""];
    });

    $httpBackend.whenPUT("/accounts/1").respond(function(method, url, data, headers) {
      accountsModel[1] = angular.fromJson(data);
      return [201, data, ""];
    });

    Restangular = $injector.get("Restangular");
    restangularAccounts = Restangular.all("accounts");
    restangularAccount0 = Restangular.one("accounts", 0);
    restangularAccount1 = Restangular.one("accounts", 1);
  }));

  afterEach(function() {
    $httpBackend.verifyNoOutstandingExpectation();
    $httpBackend.verifyNoOutstandingRequest();
  });

  describe("Interceptors", function() {
    it("Should add multiple request and response interceptors", function() {
      Restangular.addRequestInterceptor(function(elem) {
        var elemCopy = angular.copy(elem);
        elemCopy.firstRequestInterceptor = true;
        return elemCopy;
      });
      Restangular.addRequestInterceptor(function(elem) {
        expect(elem.firstRequestInterceptor).toBeDefined();
        var elemCopy = angular.copy(elem);
        elemCopy.secondRequestInterceptor = true;
        return elemCopy;
      });
      Restangular.addFullRequestInterceptor(function(elem) {
        expect(elem.firstRequestInterceptor).toBeDefined();
        expect(elem.secondRequestInterceptor).toBeDefined();
        var elemCopy = angular.copy(elem);
        elemCopy.thirdRequestInterceptor = true;
        return {
          element: elemCopy
        };
      });

      Restangular.addResponseInterceptor(function(elem) {
        var elemCopy = angular.copy(elem);
        elemCopy.firstResponseInterceptor = true;
        return elemCopy;
      });

      Restangular.addResponseInterceptor(function(elem) {
        expect(elem.firstResponseInterceptor).toBeDefined();
        var elemCopy = angular.copy(elem);
        elemCopy.secondResponseInterceptor = true;
        return elemCopy;
      });

      $httpBackend.whenPOST("/list").respond(function(method, url, data, headers) {
        var elem = angular.fromJson(data);
        expect(elem.firstRequestInterceptor).toBeDefined();
        expect(elem.secondRequestInterceptor).toBeDefined();
        expect(elem.thirdRequestInterceptor).toBeDefined();
        return [200, elem, ""];
      });

      $httpBackend.expectPOST('/list');

       Restangular.all('list').post({name: "Gonto"}).then(function(elem) {
        expect(elem.firstResponseInterceptor).toBeDefined();
        expect(elem.secondResponseInterceptor).toBeDefined();
       });

       $httpBackend.flush();
    });
  });

  describe("Transformers", function() {
    it("Should decorate element both on server and local by default", function() {

      Restangular.extendModel('accounts', function(account) {
        account.extended = function() {return true;}
        return account;
      });

      Restangular.one('accounts', 1).get().then(function(account) {
        expect(account.extended).toBeDefined();
      });

      var local = {};
      Restangular.restangularizeElement(null, local, 'accounts');
      expect(local.extended).toBeDefined();

      $httpBackend.flush();
    });

  });

  describe("With Url", function() {
    it("Shouldn't add suffix to URL", function() {
      var suffixRestangular = Restangular.withConfig(function(RestangularConfigurer) {
        RestangularConfigurer.setRequestSuffix('.json');
      });

      $httpBackend.expectGET('http://accounts.com/all');
      suffixRestangular.allUrl('accounts', 'http://accounts.com/all').getList();
      $httpBackend.flush();
    });
  });

  describe("JSONp", function() {
    it("should work for get", function() {
      Restangular.setJsonp(true);
      Restangular.one('accounts', 1).get();

      $httpBackend.expectJSONP('/accounts/1');
      $httpBackend.flush();
    });

    it("should work for getList", function() {
      Restangular.setJsonp(true);
      Restangular.all('accounts').getList();

      $httpBackend.expectJSONP('/accounts');
      $httpBackend.flush();
    });

    it("shouldn't override post", function() {
      Restangular.setJsonp(true);
      restangularAccounts.post({id: 2, user: "Someone"});

      $httpBackend.expectPOST('/accounts').respond(201, '');
      $httpBackend.flush();
    });

    
  });

  describe("Local data", function() {
    it("Should restangularize a collection OK", function() {
      var collection = angular.copy(accountsModel);

      Restangular.restangularizeCollection(null, collection, 'accounts');

      expect(_.has(collection, 'get')).toBe(true);
      expect(_.has(collection[0], 'get')).toBe(true);

      expect(collection.getRestangularUrl()).toBe('/accounts');
      expect(collection[0].getRestangularUrl()).toBe('/accounts/0');

    });
  });

  describe("$object", function() {
    it("Should work for single get", function() {
      var promise = Restangular.one('accounts', 1).get();
      var obj = promise.$object;
      expect(obj).toBeDefined();
      expect(obj.amount).toBeUndefined();

      $httpBackend.flush();

      expect(obj.amount).toEqual(3.1416);      
    });

    it("Shouldn't be restangularized by default", function() {
      Restangular.extendModel('accounts', function(account) {
        account.extended = function() {return true;}
        return account;
      });

      var promise = Restangular.one('accounts', 1).get();
      var obj = promise.$object;
      expect(obj).toBeDefined();
      expect(obj.extended).toBeUndefined();

      $httpBackend.flush();
    });

    it("Should work for single get", function() {
      var promise = Restangular.all('accounts').getList();
      var list = promise.$object;
      expect(list).toBeDefined();
      expect(list.length).toEqual(0);

      $httpBackend.flush();

      expect(list.length).toEqual(2);
      expect(list[1].amount).toEqual(3.1416);
    });
  });

  describe("ALL", function() {
    it("getList() should return an array of items", function() {
      restangularAccounts.getList().then(function(accounts) {
        expect(Restangular.stripRestangular(accounts)).toEqual(Restangular.stripRestangular(accountsModel));
      });

      $httpBackend.flush();
    });

    it("several getList() should return an array of items", function() {
      $httpBackend.expectGET('/accounts/0,1');
      Restangular.several("accounts", 0, 1).getList().then(function(accounts) {
        expect(Restangular.stripRestangular(accounts)).toEqual(Restangular.stripRestangular(accountsModel));
      });

      $httpBackend.flush();
    });

    it("several remove() should work", function() {
      $httpBackend.expectDELETE('/accounts/0,1').respond([200, "", ""]);
      Restangular.several("accounts", 0, 1).remove();

      $httpBackend.flush();
    });

    it("get(id) should return the item with given id", function() {
      restangularAccounts.get(0).then(function(account) {
        expect(Restangular.stripRestangular(account)).toEqual(Restangular.stripRestangular(accountsModel[0]));
      });

      $httpBackend.flush();
    });

    it('uses all to get the list without parameters', function() {
      Restangular.one('accounts', 1).all('messages').getList();
      $httpBackend.expectGET('/accounts/1/messages');
      $httpBackend.flush();
    });

    it("Custom GET methods should work", function() {
      restangularAccounts.customGETLIST("messages").then(function(msgs) {
        expect(Restangular.stripRestangular(msgs)).toEqual(Restangular.stripRestangular(messages));
      });

      $httpBackend.flush();
    });

    it("post() should add a new item", function() {
      restangularAccounts.post({id: 2, user: "Someone"}).then(function() {
        expect(accountsModel.length).toEqual(2);
      });

      $httpBackend.expectPOST('/accounts').respond(201, '');
      $httpBackend.flush();
    });

    it("post() should work with arrays", function() {
     Restangular.all('places').post([{name: "Gonto"}, {name: 'John'}]).then(function(value) {
       expect(value.length).toEqual(2);
     });

    $httpBackend.expectPOST('/places').respond(function(method, url, data, headers) {
      return [201, angular.fromJson(data), ""];
    });

    $httpBackend.flush();
   });

    it("post() should add a new item with data and return the data from the server", function() {
     restangularAccounts.post(newAccount).then(function(added) {
       expect(added.fromServer).toEqual(true);
       expect(added.id).toEqual(newAccount.id);
     });

    $httpBackend.expectPOST('/accounts');
    $httpBackend.flush();
   });

    it("Doing a post and then other operation (delete) should call right URLs", function() {
      restangularAccounts.post(newAccount).then(function(added) {
        added.remove();
        $httpBackend.expectDELETE('/accounts/44').respond(201, '');   
      });      
      
      $httpBackend.flush();
    });

    it("Doing a post to a server that returns no element will return undefined", function() {
      restangularAccounts.getList().then(function(accounts) {
        var newTransaction = {id: 1, name: "Gonto"};
        accounts[1].post('transactions', newTransaction).then(function(transaction) {
          expect(transaction).toBeUndefined();
        });
      });

      $httpBackend.flush();
    });

    it("head() should safely return", function() {
      restangularAccounts.head().then(function() {
        expect(true).toBe(true);
      });
      $httpBackend.flush();
    });

    it("trace()  should safely return", function() {
      restangularAccounts.trace().then(function() {
        expect(true).toBe(true);
      });

      $httpBackend.flush();
    });

    it("customPUT should work", function() {
      $httpBackend.expectPUT('/accounts/hey').respond(accountsModel);
      restangularAccounts.customPUT({key: 'value'}, 'hey');

      $httpBackend.flush();
    });

    it("options()  should safely return", function() {
      restangularAccounts.options().then(function() {
        expect(true).toBe(true);
      });

      $httpBackend.flush();
    });

     it("getList() should correctly handle params after customDELETE", function() {
      $httpBackend.expectGET('/accounts?foo=1').respond(accountsModel);
      restangularAccounts.getList({foo: 1}).then(function(){
        $httpBackend.expectDELETE('/accounts?id=1').respond(201, '');
        return restangularAccounts.customDELETE('', {id: 1});
      }).then(function() {
          $httpBackend.expectGET('/accounts?foo=1').respond(accountsModel);
          return restangularAccounts.getList({foo: 1});
        }).then(function(accounts) {
          expect(Restangular.stripRestangular(accounts)).toEqual(Restangular.stripRestangular(accountsModel));
        });

      $httpBackend.flush();
    });
  });

  describe("Scoped Service", function() {

    it("should correctly work", function() {
      var Accounts = Restangular.service('accounts');
      Accounts.post(newAccount);
      Accounts.one(0).get();
      Accounts.getList();

      $httpBackend.expectPOST('/accounts');
      $httpBackend.expectGET('/accounts/0');
      $httpBackend.expectGET('/accounts');
      $httpBackend.flush();
     });

    it("should correctly work with children", function() {
      var Transactions = Restangular.service('transactions', restangularAccount1);
      Transactions.post(newAccount);
      Transactions.one(1).get();
      Transactions.getList();

      $httpBackend.expectPOST('/accounts/1/transactions');
      $httpBackend.expectGET('/accounts/1/transactions/1');
      $httpBackend.expectGET('/accounts/1/transactions');
      $httpBackend.flush();
     });

  });

  describe("ONE", function() {
    it("get() should return a JSON item", function() {
      restangularAccount1.get().then(function(account) {
        expect(Restangular.stripRestangular(account))
          .toEqual(Restangular.stripRestangular(accountsModel[1]));
      });

      $httpBackend.flush();
    });

    it("Should save as put correctly", function() {
      restangularAccount1.get().then(function(account) {
        $httpBackend.expectPUT('/accounts/1');
        account.put();
      });

      $httpBackend.flush();
    });

    it("Should save as post correctly", function() {
      var account1 = angular.copy(restangularAccount1);
      $httpBackend.expectPOST('/accounts/1');
      account1.name = "Hey";
      account1.save();
      
      $httpBackend.flush();
    });

    it("Should make RequestLess connections with one", function() {
      restangularAccount1.one("transactions", 1).get().then(function(transaction) {
        expect(Restangular.stripRestangular(transaction))
          .toEqual(Restangular.stripRestangular(accountsModel[1].transactions[1]));
      });

      $httpBackend.flush();
    });

    it("Should make RequestLess connections with all", function() {
      restangularAccount1.all("transactions").getList().then(function(transactions) {
        expect(Restangular.stripRestangular(transactions))
          .toEqual(Restangular.stripRestangular(accountsModel[1].transactions));
      });

      $httpBackend.flush();
    });


    it("Custom GET methods should work", function() {
      restangularAccount1.customGET("message").then(function(msg) {
        expect(Restangular.stripRestangular(msg)).toEqual(Restangular.stripRestangular(messages[0]));
      });

      $httpBackend.flush();
    });

    it("put() should update the value", function() {
      restangularAccount1.get().then(function(account) {
        account.amount = 1.618;
        account.put().then(function(newAc) {
          expect(accountsModel[1].amount).toEqual(1.618);
          newAc.remove();
          $httpBackend.expectDELETE("/accounts/1");
        });
        $httpBackend.expectPUT("/accounts/1");        


      });

      $httpBackend.flush();
    });

    it("should return an array when accessing a subvalue", function() {
      restangularAccount1.get().then(function(account) {
        account.getList("transactions").then(function(transactions) {
          expect(Restangular.stripRestangular(transactions))
            .toEqual(Restangular.stripRestangular(accountsModel[1].transactions));
        });
      });

      $httpBackend.flush();
    });
  });

  describe("COPY", function() {
    it("should copy an object and 'this' should reference the copied object", function() {
      var copiedAccount = Restangular.copy(accountsModel[0]);
      var that;

      copiedAccount.user = "Copied string";
      expect(copiedAccount).not.toBe(accountsModel[0]);

      // create a spy for one of the methods to capture the value of 'this'
      spyOn(copiedAccount, 'getRestangularUrl').andCallFake(function() {
        that = this;
      });

      copiedAccount.getRestangularUrl(); // invoke the method we are spying on
      expect(that).toBe(copiedAccount);
    });
  });

  describe("getRestangularUrl", function() {
    it("should return the generated URL when you chain Restangular methods together", function() {
      var restangularSpaces = Restangular.one("accounts",123).one("buildings", 456).all("spaces");
      expect(restangularSpaces.getRestangularUrl()).toEqual("/accounts/123/buildings/456/spaces");
    });
  });

  describe("getRestangularUrl with useCannonicalId set to true", function() {
    it("should return the generated URL when you chain Restangular methods together", function() {
      var R = Restangular.withConfig(function(config) {
        config.setUseCannonicalId(true);
      });
      var restangularSpaces = R.one("accounts",123).one("buildings", 456).all("spaces");
      expect(restangularSpaces.getRestangularUrl()).toEqual("/accounts/123/buildings/456/spaces");
    });
  });


  describe("addElementTransformer", function() {
    it("should allow for a custom method to be placed at the collection level", function() {
      var accountsPromise;

      Restangular.addElementTransformer('accounts', true, function(collection) {
         collection.totalAmount = function() {};
         return collection;
      });

      accountsPromise = Restangular.all('accounts').getList();
      
      accountsPromise.then(function(accounts) {
        expect(typeof accounts.totalAmount).toEqual("function");
      });

      $httpBackend.flush();
    });

    it("should allow for a custom method to be placed at the model level when one model is requested", function() {
      var accountPromise;
      
      Restangular.addElementTransformer('accounts', false, function(model) {
         model.prettifyAmount = function() {};
         return model;
      });

      accountPromise = Restangular.one('accounts', 1).get();
      
      accountPromise.then(function(account) {
        expect(typeof account.prettifyAmount).toEqual("function");
      });

      $httpBackend.flush();
    });

    it("should allow for a custom method to be placed at the model level when several models are requested", function() {
      var accountPromise;
      
      Restangular.addElementTransformer('accounts', false, function(model) {
         model.prettifyAmount = function() {};
         return model;
      });

      accountsPromise = Restangular.all('accounts', 1).getList();
      
      accountsPromise.then(function(accounts) {
        accounts.forEach(function(account, index) {
          expect(typeof account.prettifyAmount).toEqual("function");
        });
      });

      $httpBackend.flush();
    });
  });

  describe("extendCollection", function() {
    it("should be an alias for a specific invocation of addElementTransformer", function() {
      var spy = spyOn(Restangular, 'addElementTransformer');

      var fn = function(collection) {
        collection.totalAmount = function() {};
        return collection;
      };

      Restangular.extendCollection('accounts', fn);

      expect(spy).toHaveBeenCalledWith('accounts', true, fn);
    });
  });

  describe("extendModel", function() {
    it("should be an alias for a specific invocation of addElementTransformer", function() {
      var spy = spyOn(Restangular, 'addElementTransformer');

      var fn = function(model) {
        model.prettifyAmount = function() {};
        return model;
      };

      Restangular.extendModel('accounts', fn);

      expect(spy).toHaveBeenCalledWith('accounts', false, fn);
    });
  });
  
  describe("defaultHeaders", function() {
    it("should return defaultHeaders", function() {
      var defaultHeaders = {testheader:'header value'};
      
      Restangular.setDefaultHeaders(defaultHeaders);
      
      expect(Restangular.defaultHeaders).toEqual(defaultHeaders);
    });
  });
  
  describe("defaultRequestParams", function() {
    it("should return defaultRequestParams", function() {
      var defaultRequestParams = {param:'value'};
      
      Restangular.setDefaultRequestParams(defaultRequestParams);
      
      expect(Restangular.requestParams.common).toEqual(defaultRequestParams);
    });
    
    it("should be able to set default params for get, post, put.. methods separately", function() {
      var postParams = {post:'value'},
          putParams = {put:'value'};
      
      Restangular.setDefaultRequestParams('post', postParams);
      expect(Restangular.requestParams.post).toEqual(postParams);
      
      Restangular.setDefaultRequestParams('put', putParams);
      expect(Restangular.requestParams.put).toEqual(putParams);
      
      expect(Restangular.requestParams.common).not.toEqual(putParams);
    });
    
    it("should be able to set default params for multiple methods with array", function() {
      var defaultParams = {param:'value'};
      
      Restangular.setDefaultRequestParams(['post', 'put'], defaultParams);
      
      expect(Restangular.requestParams.post).toEqual(defaultParams);
      expect(Restangular.requestParams.put).toEqual(defaultParams);
      
      expect(Restangular.requestParams.common).not.toEqual(defaultParams);
    });
  });

  describe("withConfig", function() {
    it("should create new service with scoped configuration", function() {
      var childRestangular = Restangular.withConfig(function(RestangularConfigurer){
        RestangularConfigurer.setBaseUrl('/api/v1');
      });

      expect(Restangular.configuration.baseUrl).toEqual('');
      expect(childRestangular.configuration.baseUrl).toEqual('/api/v1');
      
    });

    it("should allow nested configurations", function() {
      var childRestangular = Restangular.withConfig(function(RestangularConfigurer){
        RestangularConfigurer.setBaseUrl('/api/v1');
      });
    
      var grandchildRestangular = childRestangular.withConfig(function(RestangularConfigurer){
        RestangularConfigurer.setRequestSuffix('.json');
      });

      expect(Restangular.configuration.baseUrl).toEqual('');
      expect(Restangular.configuration.suffix).toEqual(null);

      expect(childRestangular.configuration.baseUrl).toEqual('/api/v1');
      expect(childRestangular.configuration.suffix).toEqual(null);

      expect(grandchildRestangular.configuration.baseUrl).toEqual('/api/v1');
      expect(grandchildRestangular.configuration.suffix).toEqual('.json');
    });
  });

  describe("Self linking", function() {
    it("Should request the link in HAL format", function() {
      var linkRestangular = Restangular.withConfig(function(RestangularConfigurer) {
        RestangularConfigurer.setRestangularFields({
          selfLink: "_links.self"
        });
      });

      var arr = linkRestangular.all('accountsHAL').getList().$object;
      $httpBackend.flush();

      var account = arr[0];
      $httpBackend.expectPUT("/accountsHAL/martin");
      account.name = "Updated";
      account.put();

      $httpBackend.flush();
    });
  });

  describe("Singe one (endpoint not expecting an id)", function() {
    it('does not use the id for single resource GET', function() {
      Restangular.one('info', 0, true).get();
      $httpBackend.expectGET('/info');
      $httpBackend.flush();
    });

    it('getRestangularUrl() returns still the url without id after GET', function() {
      record = Restangular.one('info', 0, true);
      record.get().then(function(data){
        expect(data.getRestangularUrl()).toEqual("/info")
      });
      $httpBackend.expectGET('/info');
      $httpBackend.flush();
    });

    it('does not use the id for single nested resource GET', function() {
      Restangular.one('accounts', 1).one('info', 0, true).get()
      $httpBackend.expectGET('/accounts/1/info');
      $httpBackend.flush();
    });

    it('does not use the id for single resource PUT', function() {
      Restangular.one('info', 0, true).put();
      $httpBackend.expectPUT('/info');
      $httpBackend.flush();
    });
  });
  describe("setSelfLinkAbsoluteUrl", function() {
    it("works", function() {
      var childRestangular = Restangular.withConfig(function(RestangularConfigurer){
        RestangularConfigurer.setSelfLinkAbsoluteUrl(false);
      });

      expect(Restangular.configuration.absoluteUrl).toEqual(true);
      expect(childRestangular.configuration.absoluteUrl).toEqual(false);
    })
  })
});
