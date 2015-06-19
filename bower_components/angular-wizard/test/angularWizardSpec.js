describe( 'AngularWizard', function() {
    var $compile, $rootScope, WizardHandler;

    beforeEach(module('mgo-angular-wizard'));


    beforeEach(inject(function(_$compile_, _$rootScope_, _WizardHandler_) {
        $compile = _$compile_;
        $rootScope = _$rootScope_;
        WizardHandler = _WizardHandler_;
    }));

    /**
     * Create the view with wizard to test
     * @param  {Scope} scope         A scope to bind to
     * @return {[DOM element]}       A DOM element compiled
     */
    function createView(scope) {
        scope.referenceCurrentStep = null;
        var element = angular.element('<wizard on-finish="finishedWizard()" current-step="referenceCurrentStep" ng-init="msg = 14" >'
                + '    <wz-step title="Starting" canenter="enterValidation">'
                + '        <h1>This is the first step</h1>'
                + '        <p>Here you can use whatever you want. You can use other directives, binding, etc.</p>'
                + '        <input type="submit" wz-next value="Continue" />'
                + '    </wz-step>'
                + '    <wz-step title="Continuing" canexit="stepValidation">'
                + '        <h1>Continuing</h1>'
                + '        <p>You have continued here!</p>'
                + '        <input type="submit" wz-next value="Go on" />'
                + '    </wz-step>'
                + '    <wz-step title="More steps" canenter="enterValidation">'
                + '        <p>Even more steps!!</p>'
                + '        <input type="submit" wz-next value="Finish now" />'
                + '    </wz-step>'
                + '</wizard>');
        var elementCompiled = $compile(element)(scope);
        $rootScope.$digest();
        return elementCompiled;
    }

    it("should correctly create the wizard", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        expect(WizardHandler).toBeTruthy();
        expect(view.find('section').length).toEqual(3);
        // expect the correct step to be desirable one
        expect(scope.referenceCurrentStep).toEqual('Starting');
    });

    it( "should go to the next step", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
    });
    it( "should return to a previous step", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
        WizardHandler.wizard().previous();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Starting');
    });
    it( "should go to a step specified by name", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().goTo('More steps');
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('More steps');
    });
    it( "should go to a step specified by index", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().goTo(2);
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('More steps');
    });
    it( "should go to next step becasue callback is truthy", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().next(function(){
            return true
        });
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
    });
    it( "should NOT go to next step because callback is falsey", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().next(function(){
            return false
        });
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Starting');
    });
    it( "should go to next step because CANEXIT is UNDEFINED", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
    });
    it( "should go to next step because CANEXIT is TRUE", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        scope.stepValidation = function(){
            return true;
        };
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('More steps');
    });
    it( "should NOT go to next step because CANEXIT is FALSE", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        scope.stepValidation = function(){
            return false;
        };
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
    });
    it( "should go to next step because CANENTER is TRUE", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        scope.enterValidation = function(){
            return true;
        };
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('More steps');
    });
    it( "should NOT go to next step because CANENTER is FALSE", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        scope.enterValidation = function(){
            return false;
        };
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
    });
    it( "should NOT return to a previous step. Although CANEXIT is false and we are heading to a previous state, the can enter validation is false", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        scope.stepValidation = function(){
            return false;
        };
        scope.enterValidation = function(){
            return false;
        };
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
        WizardHandler.wizard().previous();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
    });
    it( "should return to a previous step even though CANEXIT is false", function() {
        var scope = $rootScope.$new();
        var view = createView(scope);
        scope.stepValidation = function(){
            return false;
        };
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().next();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Continuing');
        WizardHandler.wizard().previous();
        $rootScope.$digest();
        expect(scope.referenceCurrentStep).toEqual('Starting');
    });
    it( "should finish", function() {
        var scope = $rootScope.$new();
        var flag = false;
        scope.finishedWizard = function() { flag = true; };
        var view = createView(scope);
        expect(scope.referenceCurrentStep).toEqual('Starting');
        WizardHandler.wizard().finish();
        expect(flag).toBeTruthy();
        $rootScope.$digest();
    });
});
