package com.netflix.front50

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import com.amazonaws.services.simpledb.model.*
import spock.lang.Ignore
import spock.lang.Specification

/**
 * Created by aglover on 4/8/14.
 */
class LocalSimpleDBExploreTest extends Specification {
    static AmazonSimpleDB client
    static String domain = "RESOURCE_REGISTRY"


    def setup() {
        client = new AmazonSimpleDBClient(new BasicAWSCredentials(
                System.properties["aws.key"], System.properties["aws.secret"]
        ))
        client.setEndpoint("http://localhost:8080")
        this.createLocalTable()
    }


    def cleanup() {
        client.deleteDomain(new DeleteDomainRequest(domain))
    }

    void createLocalTable() {
        client.createDomain(new CreateDomainRequest(domain));

        def testData = [
                ["name": "SAMPLEAPP", "attrs":
                        ["group"      : "tst-group", "type": "test type",
                         "description": "test", "owner": "Kevin McEntee",
                         "email"      : "web@netflix.com", "monitorBucketType": null,
                         "updatedTs"  : "1265752693581", "tags": "[1,ok, test]"]
                ],
                ["name": "Asgard", attrs:
                        ["group"      : "tst-group-2", "type": "test type",
                         "description": "test", "owner": "Andy McEntee",
                         "email"      : "web@netflix.com", "monitorBucketType": null,
                         "updatedTs"  : "1265752693581"]
                ]
        ]

        testData.each { imap ->
            Collection<ReplaceableAttribute> attributes = []
            imap["attrs"].each { key, value ->
                attributes << new ReplaceableAttribute(key, value, false)
            }
            client.putAttributes(new PutAttributesRequest().withDomainName(domain).
                    withItemName(imap["name"]).withAttributes(attributes))
        }


    }

    @Ignore
    void 'should list items in a table via name'() {
        String qry = "select * from `" + domain + "` where itemName()='SAMPLEAPP'";
        SelectRequest selectRequest = new SelectRequest(qry);

        def itms = client.select(selectRequest).getItems()
        expect:
        itms != null
        itms.size() > 0
    }

    @Ignore
    void 'should list items in a table'() {
        String qry = "select * from `" + domain + "`";
        SelectRequest selectRequest = new SelectRequest(qry);
        def itms = client.select(selectRequest).getItems()
        expect:
        itms != null
    }

    @Ignore
    void 'should count items in a table'() {
        String qry = "select count(*) from `" + domain + "`";
        SelectRequest selectRequest = new SelectRequest(qry);

        List<Item> items = client.select(selectRequest).getItems()

        expect:
        items != null
        items.size() == 1

        def item = items[0]
        item.attributes != null
        item.attributes.size() == 1

        def attribute = item.attributes[0]
        attribute.name == "Count"
        attribute.value.toInteger() >= 1
    }
}
