package com.netflix.front50

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import com.amazonaws.services.simpledb.model.*
import com.jayway.awaitility.groovy.AwaitilitySupport
import spock.lang.Specification

import static java.util.concurrent.TimeUnit.SECONDS

/**
 * Created by aglover on 4/24/14.
 */
@Mixin(AwaitilitySupport)
class CreateUpdateSimpleDBTest extends Specification {
    static AmazonSimpleDB client
    final static String DOMAIN = "TEST_RESOURCE_REGISTRY_ONLY"

    void setupSpec() {
        client = new AmazonSimpleDBClient(new BasicAWSCredentials(
                System.properties["aws.key"], System.properties["aws.secret"]
        ))
        client.createDomain(new CreateDomainRequest(DOMAIN))
    }

    def cleanupSpec() {
        client.deleteDomain(new DeleteDomainRequest(DOMAIN))
    }
    /**
     * puts 1 row into database before each test
     */
    void setup() {
        def input = [
                "group"      : "tst-group",
                "type"       : "test type",
                "description": "test",
                "owner"      : "Kevin McEntee",
                "email"      : "web@netflix.com",
                "updatedTs"  : "1265752693581",
                "tags"       : "[1,ok, test]"]

        Collection<ReplaceableAttribute> attributes = []
        input.each { key, value ->
            attributes << new ReplaceableAttribute(key, value, false)
        }
        client.putAttributes(new PutAttributesRequest().withDomainName(DOMAIN).
                withItemName("TEST_APP").withAttributes(attributes))
    }

    void 'updates should work by setting true to replaceable attribute'() {
        Collection<ReplaceableAttribute> attributes = []
        attributes << new ReplaceableAttribute('owner', 'aglover@netflix.com', true)
        when:
        client.putAttributes(new PutAttributesRequest().withDomainName(DOMAIN).
                withItemName("TEST_APP").withAttributes(attributes))

        then:
        notThrown(Exception)
        await().atMost(5, SECONDS).until {
            def itms = client.select(new SelectRequest("select * from `${DOMAIN}` where itemName()='TEST_APP'")).getItems()
            Item item = itms[0]
            itms != null
            def attr = item.attributes.find { it.name == 'owner' }
            attr.value == 'aglover@netflix.com'
        }
    }

    void 'create should result in a new row'() {
        def testData = [
                ["name": "SAMPLEAPP", "attrs":
                        ["group"      : "tst-group", "type": "test type",
                         "description": "test", "owner": "Kevin McEntee",
                         "email"      : "web@netflix.com",
                         "updatedTs"  : "1265752693581", "tags": "[1,ok, test]"]
                ],
                ["name": "Asgard", attrs:
                        ["group"      : "tst-group-2", "type": "test type",
                         "description": "test", "owner": "Andy McEntee",
                         "email"      : "web@netflix.com", "monitorBucketType": "blah",
                         "updatedTs"  : "1265752693581"]
                ]
        ]

        when:
        testData.each { imap ->
            Collection<ReplaceableAttribute> attributes = []
            imap["attrs"].each { key, value ->
                attributes << new ReplaceableAttribute(key, value, false)
            }
            client.putAttributes(new PutAttributesRequest().withDomainName(DOMAIN).
                    withItemName(imap["name"]).withAttributes(attributes))
        }

        then:
        notThrown(Exception)
    }

    void 'create throw an error if an attribute is null and not result in a new row'() {
        def testData = [
                ["name": "SAMPLEAPP", "attrs":
                        ["group"      : "tst-group",
                         "type"       : "test type",
                         "description": "test",
                         "owner"      : "Kevin McEntee",
                         "email"      : "web@netflix.com",
                         "updatedTs"  : "1265752693581",
                         "tags"       : null]
                ]
        ]

        when:
        testData.each { imap ->
            Collection<ReplaceableAttribute> attributes = []
            imap["attrs"].each { key, value ->
                attributes << new ReplaceableAttribute(key, value, false)
            }
            client.putAttributes(new PutAttributesRequest().withDomainName(DOMAIN).
                    withItemName(imap["name"]).withAttributes(attributes))
        }

        then:
        thrown(Exception)
    }

}
