package com.netflix.spinnaker.gate.security.ldap

import org.springframework.security.config.annotation.web.builders.HttpSecurity

interface LdapSsoConfigurer {
    void configure(HttpSecurity http) throws Exception
}