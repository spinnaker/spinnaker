package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.FastPropertyService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import retrofit.RetrofitError

import javax.servlet.http.HttpServletResponse

/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@CompileStatic
@RestController
@RequestMapping("/fastproperties")
class FastPropertyController {

  @Autowired
  FastPropertyService fastPropertyService

  @RequestMapping(value = "/application/{appName}", method = RequestMethod.GET)
  Map getByAppName(@PathVariable("appName") String appName) {
    fastPropertyService.getByAppName(appName)
  }

  @RequestMapping(value = "/all", method = RequestMethod.GET)
  Map getAll() {
    fastPropertyService.getAll()
  }

  @RequestMapping(value = "/impact", method = RequestMethod.POST)
  Map getImpact(@RequestBody Map scope) {
    fastPropertyService.getImpact(scope)
  }

  @RequestMapping(value = "/scopeQuery", method = RequestMethod.GET)
  Map queryByScope(@RequestBody Map scope) {
    fastPropertyService.queryByScope(scope)
  }

  @RequestMapping(value = "/create", method = RequestMethod.POST)
  Map createFastProperty(@RequestBody Map fastProperty ) {
    fastPropertyService.create(fastProperty)
  }

  @RequestMapping(value = "/promote", method = RequestMethod.POST)
  String promoteFastProperty(@RequestBody Map fastProperty ) {
    fastPropertyService.promote(fastProperty)
  }

  @RequestMapping(value = "/promote/{promotionId}", method = RequestMethod.GET)
  Map propmotionStatus(@PathVariable String promotionId) {
    fastPropertyService.promotionStatus(promotionId)
  }


  @RequestMapping(value = "/promote/{promotionId}", method = RequestMethod.PUT)
  Map propmotionStatus(@PathVariable String promotionId, @RequestParam Boolean pass) {
    fastPropertyService.passPromotion(promotionId, pass)
  }

  @RequestMapping(value = "/promotions", method = RequestMethod.GET)
  List promotions(){
    fastPropertyService.promotions()
  }

  @RequestMapping(value = "/promotions/{appId}", method = RequestMethod.GET)
  List promotionsByApp(@PathVariable String appId){
    fastPropertyService.promotionsByApp(appId)
  }

  @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
  Map deleteFastProperty(@RequestParam String propId, @RequestParam String cmcTicket, @RequestParam String env ) {
    fastPropertyService.delete(propId, cmcTicket, env)
  }

  @ExceptionHandler(RetrofitError.class)
  @ResponseBody
  public Map errorResponse(Exception ex, HttpServletResponse response) {
    response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
    [message: ex.message]
  }
}

