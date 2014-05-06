package com.netflix.kato.controllers

import com.netflix.kato.security.NamedAccountCredentialsHolder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

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
