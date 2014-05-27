package com.netflix.spinnaker.orca.test

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j

import static java.util.concurrent.TimeUnit.SECONDS

@CompileStatic
@Slf4j
class Network {

    @Memoized
    static boolean isReachable(String url, int timeoutMillis = SECONDS.toMillis(1)) {
        try {
            def connection = url.toURL().openConnection()
            connection.connectTimeout = timeoutMillis
            connection.connect()
            true
        } catch (IOException ex) {
            log.warn "${ex.getClass().simpleName}: $ex.message"
            false
        }
    }
}
