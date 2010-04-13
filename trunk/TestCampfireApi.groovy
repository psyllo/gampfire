
class TestCampfireApi {

    def api
    def passed = 0
    def failed = 0
    def accountName
    def apiKey

    def run(testName = null) {
        def runList = []
        this.metaClass.methods*.each {
            if(it.name.startsWith('test')) {
                if(testName && it.name == testName) {
                    runList.add it.name
                }else if(!testName) {
                    runList.add it.name
                }
            }
        }
        runList.each { name ->
            before()
            try {
                this."$name"()
                ++passed
            }catch(e){
                ++failed
                println "Test '$name' failed. Message: '${e.message}'"
            }
            after()
        }
        if(!failed) {
            println "Tests PASSED!"
        }else{
            println "Tests FAILED!"
        }
        println "Passed: $passed,  Failed: $failed"
    }

    def before() {
        api = new CampfireApi(accountName, apiKey)
    }

    def after() {
        api.shutdown()
    }

    def assertTrue(msg, bool) {
        if(!bool){
            throw new Exception(msg)
        }
    }

    def assertTrue(bool) {
        assertTrue "Should not fail", bool
    }

    ///// Unit & Integration Tests ////////////////////////////////////////////

    def testPrintRoomList() {
        api.printRoomList()
    }

    def testRoomAvailability() {
        def firstRoom = api.rooms().find { true }
        assertTrue "Should be able to find at least one room. Maybe you have no permissions to any rooms.", firstRoom
    }

    def testRoom() {
        def firstRoom = api.rooms().find { true }
        assertTrue "Room should have a name.", firstRoom.value.name.text()
    }
    
    def xtestJoinRoom() {
        def roomName = api.rooms().find { true }.value.name.text()
        def result = api.join(roomName)
        assertTrue "Result should not be null.", result
        assertTrue "Result should have a successful code.", result.code == 200
    }

    def xtestLeaveRoom() {
        def roomName = api.rooms().find { true }.value.name.text()
        def result = api.leave(roomName)
        assertTrue "Result should not be null.", result
        assertTrue "Result should have a successful code.", result.code == 200
    }

    def testShow() {
        def roomName = api.rooms().find { true }.value.name.text()
        def room = api.show(roomName)
        assertTrue "Result should not be null.", room
        assertTrue "Result should be a room with a valid id.", room.id.text() as int
    }

    def xtestTranscript() {
        def roomName = api.rooms().find { true }.value.name.text()
        def result = api.transcript(roomName)
        assertTrue "Transcript result should have a success code.", result.code == 200
        def tscript = new XmlSlurper().parseText(result.body)
    }

    def xtestSpeak() {
        def roomName = api.rooms().find { true }.value.name.text()
        api.join(roomName)
        def result = api.speak(roomName, "Testing a message to '${roomName}' from '${this.class.name}'")
        assertTrue "Speak result should have a success code.", result.code == 201
        api.leave(roomName)
    }

    def testNewMessagesListener() {
        def roomName = api.rooms().find { true }.value.name.text()
        def listener = {
            println 'Wah!'
        }
        api.addMessageListener(roomName, listener)
        api.startup()
        println "Waiting 5 seconds"
        sleep 5000
        println "testNewMessagesListener done"
    }
}
