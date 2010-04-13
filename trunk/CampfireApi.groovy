import java.net.*
import groovy.xml.MarkupBuilder

// TODO:
//  - addMessageListener(roomName, callback)
//  - removeMessageListener(roomName, callback)

class CampfireApi {
    def apiKey
    def accountName
    def baseUrl
    def roomsMap
    def streamList = [:]
    def transcriptChangeListeners = [:]
    def messageListeners = [:]
    def commandLoopRunning = false

    CampfireApi(accountName, apiKey) {
        Authenticator.setDefault(new TheAuthenticator(user: apiKey, pwd: 'X'))
        HttpURLConnection.setFollowRedirects(true)
        this.apiKey = apiKey
        this.accountName = accountName
        this.baseUrl = "https://${accountName}.campfirenow.com"
    }

    def reloadRooms() {
        def result = Http.get("$baseUrl/rooms.xml")
        def xml = new XmlSlurper().parseText(result.body)
        roomsMap = [:]
        xml.children().each {
            roomsMap[it.name.text()] = it
        }
        roomsMap
    }

    def rooms(name = null) {
        if(!roomsMap || !roomsMap.size()) {
            reloadRooms()
        }
        if(name) {
            def room = roomsMap[name]
            if(!room) {
                throw new Exception("Room '$roomName' not found.")
            }
            room
        }else{
            roomsMap
        }
    }

    def printRoomList() {
        rooms().each { key, room ->
            println 'Room: ' + room.name.text() + ' ID: ' + room.id.text() + ' Topic: ' + room.topic.text()
        }
    }

    def roomCmd(roomName, cmd, content = null) {
        def id = rooms(roomName).id.text()
        if(!content && (cmd == 'show')) {
            Http.get("$baseUrl/room/${id}.xml")
        }else if(!content && (cmd == 'uploads' || cmd == 'transcript')) {
            Http.get("$baseUrl/room/${id}/${cmd}.xml")
        }else if(!content && (cmd == 'join' || cmd == 'leave' || cmd == 'lock' || cmd == 'unlock')) {
            Http.post("$baseUrl/room/${id}/${cmd}.xml")
        }else if(content && (cmd == 'speak')){
            Http.postXml("$baseUrl/room/${id}/${cmd}.xml", content)
        }else{
            throw new Exception("Unsupported campfire API command '$cmd'")
        }
    }

    def prettyPrintRoom(roomName) {
        def room = show(roomName)
        println "${room.name.text()} (${room.id.text()}) - '${room.topic.text()}'"
        println "Updated: ${room.'updated-at'.text()}"
        println "Full: ${room.full.text()}"
        println "Users:"
        room.users.children().each {
            println "${it.name.text()} (${it.'email-address'.text()})"
        }
    }

    def show(roomName) {
        new XmlSlurper().parseText(roomCmd(roomName, 'show').body)
    }

    def join(roomName) {
        roomCmd(roomName, 'join')
    }

    def leave(roomName) {
        roomCmd(roomName, 'leave')
    }

    def transcript(roomName) {
        roomCmd(roomName, 'transcript')
    }

    // Speak
    // The valid types are:
    //  * TextMessage (regular chat message)
    //  * PasteMassage (pre-formatted message, rendered in a fixed-width font)
    //  * TweetMessage (a Twitter status URL to be fetched and inserted into the chat)
    //  * SoundMessage (plays a sound as determined by the message, either "rimshot", "crickets", or "trombone")
    def speak(roomName, msg, msgType = 'TextMessage') {
        def writer = new StringWriter()
        def b = new MarkupBuilder(writer)
        b.message {
            type {
                mkp.yield(msgType)
            }
            body {
                mkp.yield(msg)
            }
        }
        roomCmd(roomName, 'speak', writer.toString())
    }

    def addMessageListener(roomName, Closure c) {
        if(!messageListeners[roomName]) {
            messageListeners[roomName] = []
        }
        messageListeners[roomName] << c
        addTranscriptChangeListener(roomName) {
            // TODO: Get messages
            c()
        }
    }

    def removeMessageListener(roomName, Closure c) {
        if(messageListeners[roomName]) {
            messageListeners[roomName].remove c
        }
    }

    def addTranscriptChangeListener(roomName, Closure c) {
        if(!transcriptChangeListeners[roomName]) {
            transcriptChangeListeners[roomName] = []
        }
        transcriptChangeListeners[roomName] << c
    }

    def removeTranscriptChangeListener(roomName, Closure c) {
        if(transcriptChangeListeners[roomName]) {
            transcriptChangeListeners[roomName].remove c
        }
    }

    def getNewTranscriptMessages(prevTranscript, currTranscript) {
        def newMessages = []
        if(messageListeners.size()) {
            def prev = new XmlSlurper().parseText(prevTranscript.body)
            def curr = new XmlSlurper().parseText(currTranscript.body)
            def sizeDiff = curr.messages.size() - prev.messages.size()
            if(sizeDiff > 0) {
                curr.messages.eachWithIndex { message, i ->
                    if(prev.messages.size() - i <= 0) {
                        newMessages.add message
                    }
                }
            }
        }
        newMessages
    }

    def onTranscriptChange(room, prevTranscript, currTranscript, url) {
        def newMessages = getNewTranscriptMessages(prevTranscript, currTranscript)
        messageListeners.each { listener ->
            listener(room, url, newMessages)
        }
    }

    def commandLoop() {
        transcriptChangeListeners.each { roomName, yield ->
            def room = rooms(roomName)
            def roomId = room.id.text()
            def prevTranscript = transcript(roomName)
            if(room && roomId && prevTranscript) {
                def currTranscript = transcript(roomName)
                if(currTranscript != prevTranscript) {
                    onTranscriptChange room, prevTranscript, currTranscript, "$baseUrl/room/$roomId"
                    yield room, prevTranscript, currTranscript, "$baseUrl/room/$roomId"
                }
            }
        }
    }

    def startup() {
        if(!commandLoopRunning) {
            commandLoopRunning = true
            Thread.start {
                def delay = 9000
                def waited = delay
                while(commandLoopRunning) {
                    sleep 100
                    waited += 100
                    print '.'
                    if(waited >= delay && commandLoopRunning) {
                        print '|'
                        waited = 0
                        commandLoop()
                    }
                }
            }
        }
    }

    def streamRoom(roomName) {
        def id = rooms()[roomName]?.id?.text()
        if(!id) {
            throw new Exception("Room '$roomName' not found.")
        }
        if(!streamList[roomName]) {
            streamList[roomName] = true
            Thread.start {
                def tscript = ''
                while(streamList[roomName]) {
                    try {
                        def url = new URL("https://streaming.campfirenow.com/room/$id/live.json")
                        def conn = url.openConnection()
                        conn.setRequestMethod('POST')
                        def rd = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                        while (streamList[roomName]) {
                            println rd.readLine()
                            sleep 2000
                        }
                    }catch(e) {
                        sleep 3000
                        e.printStackTrace()
                    }
                }
                println "Stopped streaming room '$roomName'"
            }
        }else{
            println "Already streaming room '$roomName'"
        }
    }

    def stopStream(roomName) {
        streamList[roomName] = false
        null
    }

    def shutdown() {
        commandLoopRunning = false
        streamList = [:]
        transcriptChangeListeners = [:]
        messageListeners = [:]
    }

}
