package com.netflix.kato.controllers

import com.netflix.kato.security.NamedAccountCredentialsHolder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/credentials")
class CredentialsController {

  @Autowired
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  @RequestMapping(method = RequestMethod.GET)
  List<String> list() {
    namedAccountCredentialsHolder.accountNames
  }
}
