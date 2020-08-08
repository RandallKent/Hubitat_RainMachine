/**
 * -----------------------
 * ------ SMART APP ------
 * -----------------------

 *	RainMachine Service Manager SmartApp
 *
 *  Author: Jason Mok/Brian Beaird
 *      Ported to Hubitat 2020 Brad Sileo
 *  Last Updated: 2020-08-04
 *
 ***************************
 *
 *  Copyright 2019 Brian Beaird

 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * USAGE
 * 1) Put this in SmartApp. Don't install until you have all other device types scripts added
 * 2) Configure the first page which collects your Rainmachine's local IP address, port, and password to log in to RainMachine
 * 3) For each items you pick on the Programs/Zones page, it will create a device
 * 4) Enjoy!
 */

definition(
	name: "RainMachine",
	namespace: "bsileo",
	author: "Brad Sileo",
	description: "Connect your RainMachine to control your irrigation - credit to Jason Mok for original code",
	category: "SmartThings Labs",
	iconUrl:   "https://raw.githubusercontent.com/brbeaird/SmartThings_RainMachine/master/icons/rainmachine.1x.png",
	iconX2Url: "https://raw.githubusercontent.com/brbeaird/SmartThings_RainMachine/master/icons/rainmachine.2x.png",
	iconX3Url: "https://raw.githubusercontent.com/brbeaird/SmartThings_RainMachine/master/icons/rainmachine.3x.png"
)

preferences {
    page(name: "prefLogIn", title: "RainMachine")
    page(name: "prefLogInWait", title: "RainMachine")
    page(name: "prefListProgramsZones", title: "RainMachine")
    page(name: "summary", title: "RainMachine")
    page(name: "prefUninstall", title: "RainMachine")

}

/* Preferences */
def prefLogIn() {
	
    //RESET ALL THE THINGS
    atomicState.initialLogin = false
    atomicState.loginResponse = null
    atomicState.zonesResponse = null
    atomicState.programsResponse = null
    atomicState.programsResponseCount = 0
    atomicState.ProgramList = [:]

    def showUninstall = true
	return dynamicPage(name: "prefLogIn", title: "Connect to RainMachine", nextPage:"prefLogInWait", uninstall:showUninstall, install: false) {
		section("Server Information"){
			input("ip_address", "text", title: "Local IP Address of RainMachine", description: "Local IP Address of RainMachine", defaultValue: "192.168.1.100")
            input("port", "text", title: "Port # - typically 8080 or 8081 (for newer models)", description: "Port. Older models use 80. Newer models like the Mini use 8080", defaultValue: "8080")
            input("password", "password", title: "Password", description: "RainMachine password", defaultValue: "admin")
		}

        section("Server Polling"){
			input("polling", "int", title: "Polling Interval (in minutes)", description: "in minutes", defaultValue: 5)
		}
        section("Push Notifications") {
        	input "prefSendPushPrograms", "bool", required: false, title: "Push notifications when programs finish?"
            input "prefSendPush", "bool", required: false, title: "Push notifications when zones finish?"
    	}
        section("Uninstall", hideable: true, hidden:true) {
            paragraph "Tap below to completely uninstall this SmartApp and devices (doors and lamp control devices will be force-removed from automations and SmartApps)"
            href(name: "href", title: "Uninstall", required: false, page: "prefUninstall")
        }
        section("Advanced (optional)", hideable: true, hidden:true){
            // paragraph "This app has to 'scan' for programs. By default, it scans from ID 1-30. If you have deleted/created more than 30 programs, you may need to increase this number to include all your programs."
            // No longer needed - using /program endpoint
            // input("prefProgramMaxID", "number", title: "Maximum program ID number", description: "Max program ID. Increase if you have newer programs not being detected.", defaultValue: 30)
            input (
            	name: "configLoggingLevelIDE",
            	title: "IDE Live Logging Level:\nMessages with this level and higher will be logged to the IDE.",
            	type: "enum",
            	options: [
            	    "None",
            	    "Error",
            	    "Warning",
            	    "Info",
            	    "Debug",
            	    "Trace"
            	],
            	required: false
            )
        }
	}
}

def prefUninstall() {
	//unschedule()
    log.debug "Removing Rainmachine Devices..."
    def msg = ""
    getAllChildDevices().each {
		try{
			log.debug "Removing " + it.deviceNetworkId
            deleteChildDevice(it.deviceNetworkId, true)
            msg = "Devices have been removed. Tap remove to complete the process."

		}
		catch (e) {
			log.debug "Error deleting ${it.deviceNetworkId}: ${e}"
            msg = "There was a problem removing your device(s). Check the IDE logs for details."
		}
	}

    return dynamicPage(name: "prefUninstall",  title: "Uninstall", install:false, uninstall:true) {
        section("Uninstallation"){
			paragraph msg
		}
    }
}

def prefLogInWait() {
    logger("Logging in...waiting..." + "Current login response: " + atomicState.loginResponse, "debug")

    doLogin()

    //Wait up to 20 seconds for login response
    def i  = 0
    while (i < 5){
    	pause(2000)
        if (atomicState.loginResponse != null){
        	logger("Got a login response! Let's go!", "debug")
            i = 5
        }
        i++
    }

    logger("Done waiting." + "Current login response: " + atomicState.loginResponse, "debug")

    //Connection issue
    if (atomicState.loginResponse == null){
    	logger("Unable to connect", "error")
		return dynamicPage(name: "prefLogInWait", title: "Log In", uninstall:false, install: false) {
            section() {
                paragraph "Unable to connect to Rainmachine. Check your local IP and try again"
            }
        }
    }

    //Bad login credentials
    if (atomicState.loginResponse == "Bad Login"){
    	logger("Bad Login credentials", "error")
		return dynamicPage(name: "prefLogInWait", title: "Log In", uninstall:false, install: false) {
            section() {
                paragraph "Bad username/password. Click back and try again."
            }
        }
    }

    //Login Success!
    if (atomicState.loginResponse == "Success"){
		atomicState.ProgramData = [:]
        getZonesAndPrograms()
        return dynamicPage(name: "prefListProgramsZones",  title: "Programs/Zones", nextPage:"summary", install:false, uninstall:true) {
            section("Select which programs to use"){
                input(name: "programs", type: "enum", required:false, multiple:true, options: atomicState.ProgramList)
            }
            section("Select which zones to use"){                
                input(name: "zones", type: "enum", required:false, multiple:true, options: atomicState.ZoneList)
            }
            section("Name Re-Sync") {
        		input "prefResyncNames", "bool", required: false, title: "Re-sync names with RainMachine?"
    		}
    	}
    }

    else{
    	return dynamicPage(name: "prefListProgramsZones", title: "Programs/Zones", uninstall:true, install: false) {
            section() {
                paragraph "Problem getting zone/program data. Click back and try again."
            }
        }

    }

}

def summary() {
	state.installMsg = ""
    initialize()
    return dynamicPage(name: "summary",  title: "Summary", install:true, uninstall:true) {
        section("Installation Details:"){
			paragraph state.installMsg
		}
    }
}


// Note Parse is not used by calls here we use direct callbacks from HTTP requests on Hubitat
// leaving it in place for now in case this is used to return to some ST compatibility in the future.
def parse(evt) {

    def description = evt.description
    def hub = evt?.hubId

    logger("PARSE-desc: " + evt.description, "trace")
    def msg
    try{
            msg = parseLanMessage(evt.description)
    }
    catch (e){
        	//log.debug "Not able to parse lan message: " + e
            return 1
    }


    //def msg = parseLanMessage(evt.description)
    //log.debug "serverheader" + msg.headers

    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)

    if (!headerMap){
    	return 0
    }

    //Ignore Sense device data
    if (headerMap.source == "STSense"){
        return 0
    }

    //log.debug headerMap.server
    if (!headerMap.server)
    	return 0

    if (headerMap && headerMap.Path != "/api/4" && headerMap.server.indexOf("lighttpd") == -1){
    	log.debug "not a rainmachine header path - " + headerMap.Path
        return 0;
    }

    //log.debug headerMap.Path
    //if (headerMap.path

    def result
    if ((status == 200 && body != "OK") || status == 404) {
        try{
            def slurper = new groovy.json.JsonSlurper()
            result = slurper.parseText(body)
        }
        catch (e){
        	//log.debug "FYI - got a response, but it's apparently not JSON. Error: " + e + ". Body: " + body
            return 1
        }

         //Program response
        if (result.uid || (result.statusCode == 5 && result.message == "Not found !")){
        	//log.debug "Program response detected!"
            getProgram(result)
            //log.debug "program result: " + result
        	//getProgramList(result.programs)
        }

        //Figure out the other response types
        if (result.statusCode == 0){
            log.debug "status code found"
            log.debug "Got raw response: " + body

            //Login response
            if (result.access_token != null && result.access_token != "" && result.access_token != []){
                log.debug "Login response detected!"
                log.debug "Login response result: " + result
                parseLoginResponse(result)
            }

            //Generic error from one of the command methods
            else if (result.statusCode != 0) {
            	log.debug "Error status detected! One of the last calls just failed!"
            }
            else{
            	log.debug "Remote command successfully processed by Rainmachine controller."
            }
        }

    }
    else if (status == 401){
        log.debug "401 - bad login detected! result: " + body
        atomicState.expires_in =  now() - 500
		atomicState.access_token = ""
        atomicState.loginResponse = 'Bad Login'
    }
    else if (status != 411 && body != null){
    	log.debug "Unexpected response! " + status + " " + body + "evt " + description
    }


}


def doLogin(){
	atomicState.loginResponse = null
    def urlPath = "/api/4/auth/login"    
    def calloutBody = ["pwd":password, "remember": 1 ]
    def params = [
        uri: "https://" + ip_address + ":" + port,
        path: urlPath,
        requestContentType: 'application/json',            
        contentType: 'application/json',            
        ignoreSSLIssues: true,
        body: calloutBody
    ]        
    logger("send Post - " + params, "debug")
    httpPostJson(params) { response ->
        logger("loginHandler called", "debug")
        logger("loginHandler Status-" + response.status, "debug")
        if (response.getStatus() == 200) {
            def result = response.getData()    
            if (result.access_token != null && result.access_token != "" && result.access_token != []){
                log.debug "Login response detected!"
                log.debug "Login response result: " + result
                return parseLoginResponse(result)
            }
        } else {
            logger("loginHandler Error(" + response.status + ") " + response.getData(), "error")
            return false
        }
    }  
}

def parseLoginResponse(result){

    logger("Parsing login response: " + result, 'debug')
    logger("Reset login info!", 'trace')
    atomicState.access_token = ""
    atomicState.expires_in = ""

    atomicState.loginResponse = 'Received'

    if (result.statusCode == 2) {
    	atomicState.loginResponse = 'Bad Login'
    }
    
    else {
        logger("new token found: "  + result.access_token, "debug")
        if (result.access_token != null) {
            log.debug "Saving token"
            atomicState.access_token = result.access_token
            logger("Login token newly set to: " + atomicState.access_token, "debug")
            if (result.expires_in != null && result.expires_in != [] && result.expires_in != "")
            atomicState.expires_in = now() + result.expires_in
        }
        atomicState.loginResponse = 'Success'
        logger("Login response set to: " + atomicState.loginResponse, "debug")
        logger("Login token was set to: " + atomicState.access_token, "debug")
        return true
    }
}


def getZonesAndPrograms(){
	atomicState.zonesResponse = null
    atomicState.programsResponse = null
    atomicState.programsResponseCount = 0
    logger("Getting zones and programs using token: " + atomicState.access_token, "debug")
    httpGet([uri: "https://" + ip_address + ":" + port,path: "/api/4/zone", query: ["access_token":atomicState.access_token],
            requestContentType: 'application/json',contentType: 'application/json',ignoreSSLIssues: true]) { response -> 
         if (response.status == 200) {
            //log.debug "Zone response detected!"
            logger("zone result: " + response.data , "debug")
        	getZoneList(response.data.zones)
        } else {
            logger("Failed to get /Zone : " + response.data, "error")
        }
    }
    getPrograms()
}

// Process all the zones you have in RainMachine
def getZoneList(zones) {
	atomicState.ZoneData = [:]
    def tempList = [:]
    def zonesList = [:]
    zones.each { zone ->
        def dni = [ app.id, "zone", zone.uid ].join('|')
        def endTime = now() + ((zone.remaining?:0) * 1000)
        zonesList[dni] = zone.name
        tempList[dni] = [
            status: zone.state,
            endTime: endTime,
            lastRefresh: now()
        ]
        //log.debug "Zone: " + dni + "   Status : " + tempList[dni]
    }
	atomicState.ZoneList = zonesList
    atomicState.ZoneData = tempList
    logger("Temp zone list: " + zonesList, "trace")
    logger("State zone list: " + atomicState.ZoneList, "trace")
    atomicState.zonesResponse = "Success"
}

def getPrograms() {
   httpGet([uri: "https://" + ip_address + ":" + port,path: "/api/4/program", query: ["access_token":atomicState.access_token],
            requestContentType: 'application/json',contentType: 'application/json',ignoreSSLIssues: true]) { response -> 
         if (response.status == 200) {
            logger("Program response received","info")
            logger("Program result: " + response.data , "trace")
             response.data.programs.each { program ->
                 getProgram(program)
             }
             atomicState.programsResponse = "Success"
        } else {
            logger("Failed to get /Program : " + response.data, "error")
        }
   }
}

       // Process each programs you have in RainMachine
def getProgram(program) {
    //log.debug ("Processing pgm" + program)

    //If no UID, this basically means an "empty" program slot where one was deleted from the RM device. Increment count and continue
    if (!program.uid){
    	atomicState.programsResponseCount = atomicState.programsResponseCount + 1
    	//log.debug("new program response count: " + atomicState.programsResponseCount)
        return
    }

    //log.debug ("PgmUID " + program.uid)


    def dni = [ app.id, "prog", program.uid ].join('|')

    def programsList = atomicState.ProgramList
	programsList[dni] = program.name
    atomicState.ProgramList = programsList


    def endTime = 0 //TODO: calculate time left for the program

    def myObj =
     [
                uid: program.uid,
                status: program.status,
                endTime: endTime,
                lastRefresh: now(),
                program: program
	]

    def programData = atomicState.ProgramData
    programData[dni] = myObj
    atomicState.ProgramData = programData

    atomicState.programsResponseCount = atomicState.programsResponseCount + 1

}


    
    
/* Initialization */
def installed() {
	log.info  "installed()"
	log.debug "Installed with settings: " + settings
    getHubPlatform()
    state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE : 'Debug'
}

def updated() {
	log.info  "updated()"
	log.debug "Updated with settings: " + settings
    atomicState.polling = [
		last: now(),
		runNow: true
	]
    state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE : 'Debug'    
}

def uninstalled() {
	def delete = getAllChildDevices()
	delete.each { deleteChildDevice(it.deviceNetworkId) }
}


def updateMapData(){
	def combinedMap = [:]
    combinedMap << atomicState.ProgramData
    combinedMap << atomicState.ZoneData
    atomicState.data = combinedMap
    //log.debug "new data list: " + atomicState.data
}

def initialize() {
	log.info  "initialize()"
    unsubscribe()

	//Merge Zone and Program data into single map
    //atomicState.data = [:]

    def combinedMap = [:]
    combinedMap << atomicState.ProgramData
    combinedMap << atomicState.ZoneData
    atomicState.data = combinedMap

	def selectedItems = []
	def programList = [:]
	def zoneList = [:]
	def delete

	// Collect programs and zones
	if (settings.programs) {
		if (settings.programs[0].size() > 1) {
			selectedItems = settings.programs
		} else {
			selectedItems.add(settings.programs)
		}
		programList = atomicState.ProgramList
	}
	if (settings.zones) {
		if (settings.zones[0].size() > 1) {
			settings.zones.each { dni -> selectedItems.add(dni)}
		} else {
			selectedItems.add(settings.zones)
		}
		zoneList = atomicState.ZoneList
	}

	// Create device if selected and doesn't exist
	selectedItems.each { dni ->
    	def deviceType = ""
        def deviceName = ""
        def deviceClass = ""
        if (dni.contains("prog")) {
        	log.debug "Program found - " + dni
            deviceType = "Pgm"
            deviceName = programList[dni]
            deviceClass = "RainMachine Program"
        } else if (dni.contains("zone")) {
        	log.debug "Zone found - " + dni
            deviceType = "Zone"
            deviceName = zoneList[dni]
            deviceClass = "RainMachine Zone"
        }
        log.debug "devType: " + deviceType

		def childDevice = getChildDevice(dni)
		def childDeviceAttrib = [:]
		if (!childDevice){
			def fullName = deviceName
            logger("name will be: " + fullName, "debug")
            childDeviceAttrib = ["name": fullName, "completedSetup": true]

            try{
                childDevice = addChildDevice("bsileo", deviceClass, dni, null, childDeviceAttrib)
                state.installMsg = state.installMsg + deviceName + ": device created. \r\n\r\n"
            }
            catch(e)
            {
                log.debug "Error! " + e
                state.installMsg = state.installMsg + deviceName + ": problem creating RM device. Check your IDE to make sure the brbeaird : RainMachine device handler is installed and published. \r\n\r\n"
            }

		}

        //For existing devices, sync back with the RainMachine name if desired.
        else{
        	state.installMsg = state.installMsg + deviceName + ": device already exists. \r\n\r\n"
            if (prefResyncNames){
                log.debug "Name from RM: " + deviceName + " name in ST: " + childDevice.name
                if (childDevice.name != deviceName || childDevice.label != deviceName){
                	state.installMsg = state.installMsg + deviceName + ": updating device name (old name was " + childDevice.label + ") \r\n\r\n"
                }
                childDevice.name = deviceName
                childDevice.label = deviceName
            }
        }
        //log.debug "setting dev type: " + deviceType
        //childDevice.setDeviceType(deviceType)

        if (childDevice){
        	childDevice.updateDeviceType()
        }

	}




	// Delete child devices that are not selected in the settings
	if (!selectedItems) {
		delete = getAllChildDevices()
	} else {
		delete = getChildDevices().findAll {
			!selectedItems.contains(it.deviceNetworkId)
		}
	}
	delete.each { deleteChildDevice(it.deviceNetworkId) }

    //Update data for child devices
    pollAllChild()

    // Schedule polling
	schedulePoll()
}


/* Access Management */
public loginTokenExists(){
	try {
        logger("Checking for token: ", "debug")
        logger("Current token: " + atomicState.access_token, "debug")
        logger("Current expires_in: " + atomicState.expires_in, "debug")

        if (atomicState.expires_in == null || atomicState.expires_in == ""){
            logger("No expires_in found - skip to getting a new token.", "debug")
            return false
        }
        else
            return (atomicState.access_token != null && atomicState.expires_in != null && atomicState.expires_in > now())
    }
    catch (e)
    {
      logger("Warning: unable to compare old expires_in - forcing new token instead. Error: " + e, "debug")
      return false
    }
}


// Updates devices
def updateDeviceData() {
	logger("updateDeviceData()", "trace")
	// automatically checks if the token has expired, if so login again
    if (login()) {
        // Next polling time, defined in settings
        def next = (atomicState.polling.last?:0) + ( (settings.polling.toInteger() > 0 ? settings.polling.toInteger() : 1)  * 60 * 1000)
        logger("last: " + atomicState.polling.last, "debug")
        logger("now: " + new Date( now() * 1000 ), "debug")
        logger("next: " + next, "debug")
        logger("RunNow: " + atomicState.polling.runNow, "debug")
        if ((now() > next) || (atomicState.polling.runNow)) {

            // set polling states
            atomicState.polling = [
            	last: now(),
                runNow: false
            ]

            // Get all the program information
            getProgramList()

            // Get all the program information
            getZoneList()

        }
	}
}

def pollAllChild() {
    // get all the children and send updates
    def childDevice = getAllChildDevices()
    childDevice.each {
    	//log.debug "Updating children " + it.deviceNetworkId
        //sendAlert("Trying to set last refresh to: " + atomicState.data[it.deviceNetworkId].lastRefresh)
        if (atomicState.data[it.deviceNetworkId] == null){
        	log.debug "Refresh problem on ID: " + it.deviceNetworkId
            //sendAlert("Refresh problem on ID: " + it.deviceNetworkId)
            //sendAlert("data list: " + atomicState.data)
        }
        it.updateDeviceStatus(atomicState.data[it.deviceNetworkId].status)
        it.updateDeviceLastRefresh(atomicState.data[it.deviceNetworkId].lastRefresh)
        //it.poll()
    }
}

// Returns UID of a Zone or Program
private getChildUID(child) {
	return child.device.deviceNetworkId.split("\\|")[2]
}

// Returns Type of a Zone or Program
private getChildType(child) {
	def childType = child.device.deviceNetworkId.split("\\|")[1]
	if (childType == "prog") { return "program" }
	if (childType == "zone") { return "zone" }
}



/* for SmartDevice to call */
// Refresh data
def refresh() {
    log.info "refresh()"

    //For programs, we'll only be refreshing programs with matching child devices. Get the count of those so we know when the refresh is done.
    def refreshProgramCount = 0
    atomicState.ProgramData.each { dni, program ->
    	if (getChildDevice(dni)){
        	refreshProgramCount++
		}
    }

    logger("Processing " + refreshProgramCount + " programs","debug")

	atomicState.polling = [
		last: now(),
		runNow: true
	]
	//atomicState.data = [:]



    //If login token exists and is valid, reuse it and callout to refresh zone and program data
    if (loginTokenExists()){
		logger("Existing token detected", "debug")
        getZonesAndPrograms()

        //Wait up to 10 seconds before cascading results to child devices
        def i = 0
        while (i < 5){
            pause(2000)
            if (atomicState.zonesResponse == "Success" && atomicState.programsResponseCount >= refreshProgramCount ){
                logger("Got a good RainMachine response! Let's go!", "debug")
                updateMapData()
                pollAllChild()
                //atomicState.expires_in = "" //TEMPORARY FOR TESTING TO FORCE RELOGIN
                return true
            }
            logger("Current zone response: " + atomicState.zonesResponse + "Current pgm response count: " + atomicState.programsResponseCount + " of " +  refreshProgramCount, "debug")
            i++
        }

        if (atomicState.zonesResponse == null){
    		sendAlert("Unable to get zone data while trying to refresh")
            logger("Unable to get zone data while trying to refresh", "error")
            return false
    	}

        if (atomicState.programsResponse == null){
    		sendAlert("Unable to get program data while trying to refresh")
            logger("Unable to get program data while trying to refresh", "error")
            return false
    	}

    }

    //If not, get a new token then refresh
    else{
    	log.debug "Need new token"
    	doLogin()

        //Wait up to 20 seconds for successful login
        def i  = 0
        while (i < 5){
            pause(2000)
            if (atomicState.loginResponse != null){
                log.debug "Got a response! Let's go!"
                i = 5
            }
            i++
        }
        log.debug "Done waiting." + "Current login response: " + atomicState.loginResponse


        if (atomicState.loginResponse == null){
    		log.debug "Unable to connect while trying to refresh zone/program data"
            return false
    	}


        if (atomicState.loginResponse == "Bad Login"){
            log.debug "Bad Login while trying to refresh zone/program data"
            return false
        }


        if (atomicState.loginResponse == "Success"){
            log.debug "Got a login response for refreshing! Let's go!"
            refresh()
    	}

    }

}

// Get single device status
def getDeviceStatus(child) {
	log.info "getDeviceStatus()"
	//tries to get latest data if polling limitation allows
	//updateDeviceData()
	return atomicState.data[child.device.deviceNetworkId].status
}

// Get single device refresh timestamp
def getDeviceLastRefresh(child) {
	log.info "getDeviceStatus()"
	//tries to get latest data if polling limitation allows
	//updateDeviceData()
	return atomicState.data[child.device.deviceNetworkId].lastRefresh
}


// Get single device ending time
def getDeviceEndTime(child) {
	//tries to get latest data if polling limitation allows
	updateDeviceData()
	if (atomicState.data[child.device.deviceNetworkId]) {
		return atomicState.data[child.device.deviceNetworkId].endTime
	}
}

def sendCommand2(child, apiCommand, apiTime) {
	atomicState.lastCommandSent = now()
    //If login token exists and is valid, reuse it and callout to refresh zone and program data
    if (loginTokenExists()){
		log.debug "Existing token detected for sending command"

        def childUID = getChildUID(child)
		def childType = getChildType(child)
        def apiPath = "/api/4/" + childType + "/" + childUID + "/" + apiCommand
        def params = [
                uri: "https://" + ip_address + ":" + port,
                query: ["access_token":atomicState.access_token],
                path: apiPath,
                requestContentType: 'application/json',            
                contentType: 'application/json',            
                ignoreSSLIssues: true,
                body: []
            ]        

        //Stop Everything
        if (apiCommand == "stopall") {
        	params.path = "/api/4/watering/stopall"
        }
        //Zones will require time
        else if (childType == "zone") {
            params.body = [ time: apiTime ]
        }

        //Programs will require pid
        else if (childType == "program") {
            params.body = [pid : childUID ]
        }
        else {
            logger("Unexpected condition in sendCommand2 - " + childType + " -- " + apiCommand, "error")
        }
     
        
        logger("SendCommand2 - " + params, "debug")
        try {
            httpPostJson(params) { response ->
                if (response.getStatus() == 200) {
                    logger("Completed command - " + apiCommand, "info")
                } 
                else {
                    logger("Error in command - " + apiCommand,"error")
                    logger("Error detail " + response.data,"error")
                }
            }
        } 
        catch (e) {
            logger("Command failed to execute - " + e, "error")
        }
        //Forcefully get the latest data after waiting for 15 seconds
        runIn(15, refresh)        
    }

    //If not, get a new token then refresh
    else{
    	 logger("Need new token", "debug")
    	doLogin()

        //Wait up to 20 seconds for successful login
        def i  = 0
        while (i < 5){
            pause(2000)
            if (atomicState.loginResponse != null){
                 logger("Got a response! Let's go!", "debug")
                i = 5
            }
            i++
        }
        logger("Done waiting." + "Current login response: " + atomicState.loginResponse, "debug")


        if (atomicState.loginResponse == null){
    		 logger("Unable to connect while trying to refresh zone/program data", "debug")
            return false
    	}


        if (atomicState.loginResponse == "Bad Login"){
             logger("Bad Login while trying to refresh zone/program data", "debug")
            return false
        }


        if (atomicState.loginResponse == "Success"){
             logger("Got a login response for sending command! Let's go!", "debug")
            sendCommand2(child, apiCommand, apiTime)
    	}

    }

}

// gets back a valid set of Paramas - URI and query - for a call to my controller.
def getControllerParams() {
	//If login token exists and is valid, reuse it and callout to refresh zone and program data
    if (loginTokenExists()){
		log.debug "Existing token detected for PARAMS"
    
        def params = [
                uri: "https://" + ip_address + ":" + port,
                query: ["access_token":atomicState.access_token],
                requestContentType: 'application/json',            
                contentType: 'application/json',            
                ignoreSSLIssues: true,
                path: "",
                body: []
            ]        
        
        return params       
    }
    //If not, get a new token then retry to get the Params
    else{
    	logger("Need new token", "debug")
    	def login = doLogin()        
        if (atomicState.loginResponse == null){
    		 logger("Unable to connect while trying to refresh zone/program data", "debug")
            return false
    	}

        if (atomicState.loginResponse == "Bad Login"){
             logger("Bad Login while trying to refresh zone/program data", "debug")
            return false
        }

        if (atomicState.loginResponse == "Success"){
             logger("Got a login response for sending command! Let's go!", "debug")
            return getControllerParams()
    	}

    }

}

def scheduledRefresh(){
    //If a command has been sent in the last 30 seconds, don't do the scheduled refresh.
    if (atomicState.lastCommandSent == null || atomicState.lastCommandSent < now()-30000){
    	refresh()
    }
    else{
    	log.debug "Skipping scheduled refresh due to recent command activity."
    }

}


def schedulePoll() {
    log.debug "Creating RainMachine schedule. Setting was " + settings.polling
    def pollSetting = settings.polling.toInteger()
    def pollFreq = 1
    if (pollSetting == 0){
    	pollFreq = 1
    }
    else if ( pollSetting >= 60){
    	pollFreq = 59
   	}
    else{
    	pollFreq = pollSetting
    }

    log.debug "Poll freq: " + pollFreq
    unschedule()
    schedule("37 */" + pollFreq + " * * * ?", scheduledRefresh )
    log.debug "RainMachine schedule successfully started!"
}


def sendAlert(alert){
	//sendSms("555-555-5555", "Alert: " + alert)
}

def showVersion(){
	return "0.9.5"
}


//*******************************************************
//*  logger()
//*
//*  Wrapper function for all logging.
//*******************************************************

def loggingLevel() {
    def lookup = [
        	    "None" : 0,
        	    "Error" : 1,
        	    "Warning" : 2,
        	    "Info" : 3,
        	    "Debug" : 4,
        	    "Trace" : 5]
     return lookup[state.loggingLevelIDE ? state.loggingLevelIDE : 'Debug']     
}

private logger(msg, level = "debug") {

    def lookup = [
        	    "None" : 0,
        	    "Error" : 1,
        	    "Warning" : 2,
        	    "Info" : 3,
        	    "Debug" : 4,
        	    "Trace" : 5]
     def logLevel = lookup[state.loggingLevelIDE ? state.loggingLevelIDE : 'Debug']     

    switch(level) {
        case "error":
            if (logLevel >= 1) log.error msg
            break

        case "warn":
            if (logLevel >= 2) log.warn msg
            break

        case "info":
            if (logLevel >= 3) log.info msg
            break

        case "debug":
            if (logLevel >= 4) log.debug msg
            break

        case "trace":
            if (logLevel >= 5) log.trace msg
            break

        default:
            log.debug msg
            break
    }
}


// **************************************************************************************************************************
// SmartThings/Hubitat Portability Library (SHPL)
// Copyright (c) 2019, Barry A. Burke (storageanarchy@gmail.com)
//
// The following 3 calls are safe to use anywhere within a Device Handler or Application
//  - these can be called (e.g., if (getPlatform() == 'SmartThings'), or referenced (i.e., if (platform == 'Hubitat') )
//  - performance of the non-native platform is horrendous, so it is best to use these only in the metadata{} section of a
//    Device Handler or Application
//
private String  getPlatform() { (physicalgraph?.device?.HubAction ? 'SmartThings' : 'Hubitat') }	// if (platform == 'SmartThings') ...
private Boolean getIsST()     { (physicalgraph?.device?.HubAction ? true : false) }					// if (isST) ...
private Boolean getIsHE()     { (hubitat?.device?.HubAction ? true : false) }						// if (isHE) ...
//
// The following 3 calls are ONLY for use within the Device Handler or Application runtime
//  - they will throw an error at compile time if used within metadata, usually complaining that "state" is not defined
//  - getHubPlatform() ***MUST*** be called from the installed() method, then use "state.hubPlatform" elsewhere
//  - "if (state.isST)" is more efficient than "if (isSTHub)"
//
private String getHubPlatform() {
    if (state?.hubPlatform == null) {
        state.hubPlatform = getPlatform()						// if (hubPlatform == 'Hubitat') ... or if (state.hubPlatform == 'SmartThings')...
        state.isST = state.hubPlatform.startsWith('S')			// if (state.isST) ...
        state.isHE = state.hubPlatform.startsWith('H')			// if (state.isHE) ...
    }
    return state.hubPlatform
}
private Boolean getIsSTHub() { (state.isST) }					// if (isSTHub) ...
private Boolean getIsHEHub() { (state.isHE) }
