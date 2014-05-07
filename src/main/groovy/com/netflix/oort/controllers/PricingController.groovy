package com.netflix.oort.controllers

import javax.annotation.PostConstruct
import javax.servlet.http.HttpServletResponse
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate

@RestController
@RequestMapping("/pricing")
class PricingController {

  @RequestMapping(method = RequestMethod.GET)
  def list() {
    Cacher.get()
  }

  @RequestMapping(value = "/{type:.+}", method = RequestMethod.GET)
  def get(@PathVariable("type") String type, @RequestParam(value = "region", required=false) List<String> regions, HttpServletResponse response) {
    def map = Cacher.get()
    if (map.containsKey(type) && !regions) {
      map[type]
    } else if (map.containsKey(type) && regions) {
      def priceMap = map[type]
      priceMap.findAll { k, v -> regions.contains(k) }
    } else {
      response.sendError 404
    }
  }

  @Component
  static class Cacher {
    private static Map vals = [:]
    private RestTemplate rt = new RestTemplate()

    static def get() {
      vals
    }

    def regionMap = ["us-east": ["us-east-1"], "us-west": ["us-west-1", "us-west-2"], "apac-sin": ["ap-southeast-1"],
                     "apac-syd": ["ap-northeast-1"], "apac-tokyo": ["ap-northeast-1"], "eu-ireland": ["eu-west-1"]]

    @PostConstruct
    @Scheduled(fixedRate = 3600000l)
    void cachePrices() {
      def prices = rt.getForObject("https://a0.awsstatic.com/pricing/1/deprecated/ec2/pricing-on-demand-instances.json", Map)
      prices.config.regions.each { Map m ->
        String region = m.region
        def needsConverting = false
        if (!(region ==~ /.*\d+$/)) needsConverting = true
        m.instanceTypes.each {
          it.sizes.each { Map details ->
            Map values = (Map)details.valueColumns.find { it.name == "linux" }
            if (values) {
              if (!vals.containsKey(details.size)) {
                vals[details.size] = [:]
              }
              if (needsConverting) {
                for (String newRegion : regionMap[region]) {
                  appendToMap details, vals, newRegion, values
                }
              } else {
                appendToMap details, vals, region, values
              }
            }
          }
        }
      }
    }

    static void appendToMap(Map m, Map vals, String region, Map a) {
      if (!vals[m.size].containsKey(region)) {
        vals[m.size][region] = []
      }
      vals[m.size][region] << a.prices*.value.first()
    }
  }

}
