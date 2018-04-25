import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

def postToFlowdock(content) {
    return httpRequest(url: "https://api.flowdock.com/messages",
        httpMode: "POST",
        contentType: "APPLICATION_JSON_UTF8",
        customHeaders: [[
            name: "X-flowdock-wait-for-message",
            value: "${true}"
        ]],
        requestBody: content)
}

def notifyFlowdock(apiToken, tags) {
    def statusMap = [
        SUCCESS: [
            color: 'green',
            emoji: ':white_check_mark:'
        ],
        UNSTABLE: [
            color: 'yellow',
            emoji: ':heavy_exclamation_mark:'
        ],
        FAILURE: [
            color: 'red',
            emoji: ':x:'
        ],
        ABORTED: [
            color: 'white',
            emoji: ':no-entry-sign:'
        ],
        NOT_BUILT: [
            color: 'white',
            emoji: ':o:'
        ]
    ]
    tags = tags.replaceAll("\\s", "")
    
    // a `null` build status is actually successful
    def buildStatus = currentBuild.result ? currentBuild.result : 'SUCCESS'
    def subject = "${env.JOB_BASE_NAME} build ${currentBuild.displayName.replaceAll('#', '')}"
    def fromAddress = ''
    switch (buildStatus) {
        case 'SUCCESS':
            subject += ' was successful'
            fromAddress = 'build+ok@flowdock.com'
            break
        case 'FAILURE':
            subject += ' failed'
            fromAddress = 'build+fail@flowdock.com'
            break
        case 'UNSTABLE':
            subject += ' was unstable'
            fromAddress = 'build+fail@flowdock.com'
            break
        case 'ABORTED':
            subject += ' was aborted'
            fromAddress = 'build+fail@flowdock.com'
            break
        case 'NOT_BUILT':
            subject += ' was not built'
            fromAddress = 'build+fail@flowdock.com'
            break
        case 'FIXED':
            subject += ' was fixed'
            fromAddress = 'build+ok@flowdock.com'
            break
    }
    
    def content = "<h3>${env.JOB_BASE_NAME}</h3>"
    content += "Build: ${currentBuild.displayName}<br />"
    content += "Result: <strong>${buildStatus}</strong><br />"
    content += "URL: <a href=\"${env.BUILD_URL}\">${currentBuild.fullDisplayName}</a><br />"
    content += "Branch: ${env.BRANCH_NAME}<br />"
    

    def payload = JsonOutput.toJson([
        flow_token: apiToken,
        event: "activity",
        external_thread_id: "jenkins:${env.JOB_BASE_NAME}:${currentBuild.id}",
        title: "${env.JOB_BASE_NAME}",
        tags: tags,
        author: [
            name: "CI",
            email: fromAddress
        ],
        thread: [
            title: subject,
            body: content,
            external_url: env.BUILD_URL
        ]
    ])
    def response = postToFlowdock payload
    
    def result = new JsonSlurperClassic().parseText(response.content)
    
    def discussionPayload = JsonOutput.toJson([
        flow_token: apiToken,
        event: "message",
        content: "${statusMap[buildStatus].emoji} [${subject}](${env.BUILD_URL})",
        thread_id: result.thread_id,
        author: [
            name: "CI",
            email: fromAddress
        ],
        tags: tags
    ])
    
    postToFlowdock discussionPayload
}
