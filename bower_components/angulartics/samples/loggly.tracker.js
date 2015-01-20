(function(window, document) {
    var LOGGLY_INPUT_PREFIX = 'http' + ( ('https:' === document.location.protocol ? 's' : '') ) + '://',
        LOGGLY_COLLECTOR_DOMAIN = 'logs-01.loggly.com',
        LOGGLY_INPUT_SUFFIX = '.gif?',
        LOGGLY_SESSION_KEY = 'logglytrackingsession',
        LOGGLY_SESSION_KEY_LENGTH = LOGGLY_SESSION_KEY.length + 1;

    function uuid() {
        // lifted from here -> http://stackoverflow.com/questions/105034/how-to-create-a-guid-uuid-in-javascript/2117523#2117523
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            var r = Math.random()*16|0, v = c == 'x' ? r : (r&0x3|0x8);
            return v.toString(16);
        });  
    }
    
    function LogglyTracker() {
        this.key = false;
    }
    
    function setKey(tracker, key) {
        tracker.key = key;
        tracker.setSession();
        setInputUrl(tracker);
    }
    
    function setInputUrl(tracker) {
        tracker.inputUrl = LOGGLY_INPUT_PREFIX 
            + (tracker.logglyCollectorDomain || LOGGLY_COLLECTOR_DOMAIN)
            + '/inputs/'
            + tracker.key 
            + LOGGLY_INPUT_SUFFIX;
    }
    
    LogglyTracker.prototype = {
        setSession: function(session_id) {
            if(session_id) {
                this.session_id = session_id;
                this.setCookie(this.session_id);
            } else if(!this.session_id) {
                this.session_id = this.readCookie();
                if(!this.session_id) {
                    this.session_id = uuid();
                    this.setCookie(this.session_id);
                }
            }
        },
        push: function(data) {
            var type = typeof data;
            
            if( !data || !(type === 'object' || type === 'string') ) {
                return;
            }

            var self = this;

            setTimeout(function() {
                if(type === 'string') {
                    data = {
                        'text': data
                    };
                } else {
                    if(data.logglyCollectorDomain) {
                        self.logglyCollectorDomain = data.logglyCollectorDomain;
                        return;
                    }
                
                    if(data.logglyKey) {
                        setKey(self, data.logglyKey);
                        return;
                    }
                
                    if(data.session_id) {
                        self.setSession(data.session_id);
                        return;
                    }
                }
                
                if(!self.key) {
                    return;
                }
            
                self.track(data);
            }, 0);
            
        },
        track: function(data) {
            // inject session id
            data.sessionId = this.session_id;
        
            try {
                var im = new Image(),
                    q = 'PLAINTEXT=' + encodeURIComponent(JSON.stringify(data));
                im.src = this.inputUrl + q;
            } catch (ex) {
                if (window && window.console && typeof window.console.log === 'function') {
                    console.log("Failed to log to loggly because of this exception:\n" + ex);
                    console.log("Failed log data:", data);
                }
            }
        },
        /**
         *  These cookie functions are not a global utilities.  It is for purpose of this tracker only
         */
        readCookie: function() {
            var cookie = document.cookie,
                i = cookie.indexOf(LOGGLY_SESSION_KEY);
            if(i < 0) {
                return false;
            } else {
                var end = cookie.indexOf(';', i + 1);
                end = end < 0 ? cookie.length : end;
                return cookie.slice(i + LOGGLY_SESSION_KEY_LENGTH, end);
            }
        },
        setCookie: function(value) {
            document.cookie = LOGGLY_SESSION_KEY + '=' + value;
        }
    };
    
    var existing = window._LTracker;
    
    var tracker = new LogglyTracker();
    
    if(existing && existing.length ) {
        var i = 0,
            eLength = existing.length;
        for(i = 0; i < eLength; i++) {
            tracker.push(existing[i]);
        }
    }
    
    window._LTracker = tracker; // default global tracker
    
    window.LogglyTracker = LogglyTracker;   // if others want to instantiate more than one tracker
    
})(window, document);