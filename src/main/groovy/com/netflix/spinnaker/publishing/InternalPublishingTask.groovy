package com.netflix.spinnaker.publishing

import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPut
import org.apache.http.entity.FileEntity
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class InternalPublishingTask extends DefaultTask {

    @TaskAction
    void internalUpload() {
        String folder = "${project.group.replace('.', '/')}/${project.name}/${project.version}"
        println "upload destination = " + folder
        HttpClient cli = new DefaultHttpClient()
        HttpHost host = new HttpHost("artifacts.netflix.com", 80)
        def creds = new BasicCredentialsProvider()
        creds.setCredentials(new AuthScope(host), new UsernamePasswordCredentials(project.artifactoryUsername, project.artifactoryPassword))
        cli.setCredentialsProvider(creds)
        String repo = 'maven-central-local'

        for (File artifact in project.configurations.archives.artifacts.files.files) {
            String name = artifact.name
            if (name.equals('mavenNebula.pom')) {
                name = project.name + '-' + project.version + '.pom'
            }
            HttpPut upload = new HttpPut("/$repo/$folder/$name")
            println "PUT ${upload.getURI()}"
            upload.setEntity(new FileEntity(artifact, "application/octet-stream"))
            def response = cli.execute(host, upload)
            if (response.statusLine.statusCode < 200 || response.statusLine.statusCode >= 300) {
                println "upload failed: $response.statusLine.statusCode"
                println response.entity.content.text
                EntityUtils.consume(response.entity)
                throw new Exception("internalPublish failed")
            }
            EntityUtils.consume(response.entity)
        }
    }
}
