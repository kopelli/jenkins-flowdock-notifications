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

def call(script, apiToken, tagInput = '') {
    def statusMap = [
        ABORTED: [
            avatarUrl: 'https://d2cxspbh1aoie1.cloudfront.net/avatars/bcde425262dbc01339a547192825ca20/120',
            color: 'white',
            emoji: ':no-entry-sign:',
            fromAddress: 'build+fail@flowdock.com'
        ],
        FAILURE: [
            avatarUrl: 'https://d2cxspbh1aoie1.cloudfront.net/avatars/bcde425262dbc01339a547192825ca20/120',
            color: 'red',
            emoji: ':x:',
            fromAddress: 'build+fail@flowdock.com'
        ],
        FIXED: [
            avatarUrl: 'https://d2cxspbh1aoie1.cloudfront.net/avatars/ac9a7ed457c803acfe8d29559dd9b911/120',
            color: 'green',
            emoji: ':white_check_mark:',
            fromAddress: 'build+ok@flowdock.com'
        ],
        NOT_BUILT: [
            avatarUrl: 'https://d2cxspbh1aoie1.cloudfront.net/avatars/bcde425262dbc01339a547192825ca20/120',
            color: 'white',
            emoji: ':o:',
            fromAddress: 'build+fail@flowdock.com'
        ],
        SUCCESS: [
            avatarUrl: 'https://d2cxspbh1aoie1.cloudfront.net/avatars/ac9a7ed457c803acfe8d29559dd9b911/120',
            color: 'green',
            emoji: ':white_check_mark:',
            fromAddress: 'build+ok@flowdock.com'
        ],
        UNSTABLE: [
            avatarUrl: 'https://d2cxspbh1aoie1.cloudfront.net/avatars/bcde425262dbc01339a547192825ca20/120',
            color: 'yellow',
            emoji: ':heavy_exclamation_mark:',
            fromAddress: 'build+fail@flowdock.com'
        ]
    ]

    // Assume that the user has passed in a space-separate list of tags, with the appropriate hashtag preceding each.
    def tags = tagInput.split()
    tags += '#build-status'
    
    // a `null` build status is actually successful
    def buildStatus = script.currentBuild.result ? script.currentBuild.result : 'SUCCESS'
    def subject = "${script.env.JOB_BASE_NAME} build ${script.currentBuild.displayName.replaceAll('#', '')}"
    switch (buildStatus) {
        case 'SUCCESS':
            def prevResult = script.currentBuild.getPreviousBuild() != null ? script.currentBuild.getPreviousBuild().getResult() : null
            if ("FAILURE".equals(prevResult) || "UNSTABLE".equals(prevResult)) {
                subject += ' was fixed'
                break
            }
            subject += ' was successful'
            break
        case 'FAILURE':
            subject += ' failed'
            break
        case 'UNSTABLE':
            subject += ' was unstable'
            break
        case 'ABORTED':
            subject += ' was aborted'
            break
        case 'NOT_BUILT':
            subject += ' was not built'
            break
        case 'FIXED':
            subject += ' was fixed'
            break
    }
    
    def content = "<h3>${script.env.JOB_BASE_NAME}</h3>"
    content += "Build: ${script.currentBuild.displayName}<br />"
    content += "Result: <strong>${buildStatus}</strong><br />"
    content += "URL: <a href=\"${script.env.BUILD_URL}\">${script.currentBuild.fullDisplayName}</a><br />"
    content += "Branch: ${script.env.BRANCH_NAME}<br />"
    

    def payload = JsonOutput.toJson([
        flow_token: apiToken,
        event: "activity",
        external_thread_id: "jenkins:${script.env.JOB_BASE_NAME}:${script.currentBuild.id}",
        title: "${script.env.JOB_BASE_NAME}",
        tags: tags,
        author: [
            name: "CI",
            email: statusMap[buildStatus].fromAddress,
            avatar: statusMap[buildStatus].avatarUrl
        ],
        thread: [
            title: subject,
            body: content,
            external_url: script.env.BUILD_URL,
            status: [
                color: statusMap[buildStatus].color,
                value: buildStatus
            ]
        ]
    ])
    def response = postToFlowdock payload
    
    def result = new JsonSlurperClassic().parseText(response.content)
    
    def discussionPayload = JsonOutput.toJson([
        flow_token: apiToken,
        event: "message",
        content: "${statusMap[buildStatus].emoji} [${subject}](${script.env.BUILD_URL})",
        thread_id: result.thread_id,
        author: [
            name: "CI",
            email: statusMap[buildStatus].fromAddress,
            avatar: statusMap[buildStatus].avatarUrl
        ]
    ])
    
    postToFlowdock discussionPayload
}
