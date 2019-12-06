package com.netflix.spinnaker.fiat.permissions

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import spock.lang.Specification
import spock.lang.Unroll

class DefaultFallbackPermissionsResolverSpec extends Specification {
    private static final Authorization R = Authorization.READ
    private static final Authorization W = Authorization.WRITE
    private static final Authorization E = Authorization.EXECUTE
    private static final Authorization C = Authorization.CREATE

    def makePerms(Map<Authorization, List<String>> auths) {
        return Permissions.Builder.factory(auths).build()
    }

    @Unroll
    def "should add fallback permissions based on fallbackTo value" () {
        setup:
        FallbackPermissionsResolver fallbackResolver = new DefaultFallbackPermissionsResolver(fallbackFrom, fallbackTo)

        when:
        def result = fallbackResolver.resolve(makePerms(givenPermissions))

        then:
        makePerms(expectedPermissions) == result

        where:
        fallbackFrom  ||  fallbackTo  || givenPermissions         || expectedPermissions
        E             ||  R           || [:]                      || [:]
        E             ||  R           || [(R): ['r']]             || [(R): ['r'], (E): ['r']]
        E             ||  W           || [(R): ['r'], (W): ['w']] || [(R): ['r'], (W): ['w'], (E): ['w']]
        C             ||  W           || [(R): ['r'], (W): ['w']] || [(R): ['r'], (W): ['w'], (C): ['w']]
    }
}
