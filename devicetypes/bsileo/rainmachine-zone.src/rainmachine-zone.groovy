/**
  * -----------------------
 * ------ DEVICE HANDLER--ZONE----
 * -----------------------
 *	RainMachine Smart Device
 *
 *	Author: Jason Mok/Brian Beaird
 *  Last Updated: 2018-08-12
 *
 *	Author: Brad Sileo
 *  Last Updated: 2020-08-02
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
 **************************
 *
 * REQUIREMENTS:
 * Refer to RainMachine Service Manager SmartApp
 *
 **************************
 *
 * USAGE:
 * Put this in Device Type. Don't install until you have all other device types scripts added
 * Refer to RainMachine Service Manager SmartApp
 *
 */
metadata {
	definition (name: "RainMachine Zone", namespace: "bsileo", author: "Brad Sileo/Jason Mok/Brian Beaird") {
		capability "Valve"
		capability "Refresh"
		capability "Polling"
        capability "Switch"
        capability "Sensor"

		attribute "runTime", "number"
        attribute "lastRefresh", "string"
        attribute "lastStarted", "string"
        attribute "deviceType", "string"
        attribute "paused", "string"
        
		command "refresh"
        command "stopAll"
	}

     command "setRunTime", [[ name: "Run Time*", type: "NUMBER", description: "Set the runtime in minutes for the next time I am manually Opened"]]     
     command "waterForTime", [[ name: "Run Time*", type: "NUMBER", description: "Start watering now for this many minutes"]]     
     command "pauseAll", [[ name: "Pause Time*", type: "NUMBER", description: "Pause water for this many seconds on All Zones"]]     
     command "resumeAll"    
}

// installation, set default value
def installed() {
	setRunTime(5)    
}


// turn on sprinkler
def open(mins)  {
    log.debug "Turning the sprinkler on (valve)"
    if (!mins) {
        mins = device.currentValue("runTime")
    }
    deviceStatus(1)
    parent.sendCommand2(this, "start", (mins * 60))    
}
// turn off sprinkler
def close() {
	log.debug "Turning the sprinkler off (valve)"
    deviceStatus(0)
    parent.sendCommand2(this, "stop",  (device.currentValue("runTime") * 60))    
}


def on() {
	open()
}
def off() {
	close()
}

def waterForTime(mins) {
   open(mins)
}


def pauseAll(time) {
    def params = parent.getControllerParams()
    logger("Got Params for /pauseall " + time + "s -- " + params,"debug")
    params.path =  "/api/4/watering/pauseall"
    params.body = [duration: time ]
    httpPost(params) { response -> 
         if (response.status == 200) {            
             def data = response.data
            logger("PauseAll result: " + data , "debug")            
            logger("PauseAll Completed code:" + data.message, "info")
            refresh()
        } else {
            logger("Failed to execute /pauseall: " + response.data, "error")
        }
    }
}

def resumeAll() {
    def params = parent.getControllerParams()
    logger("Got Params for /pauseall " + time + "s -- " + params,"debug")
    params.path =  "/api/4/watering/pauseall"
    params.body = [duration: 0 ] // A time of Zero signals a resume
    httpPost(params) { response -> 
         if (response.status == 200) {            
             def data = response.data
            logger("Resume result: " + data , "debug")            
            logger("ResumeAll Completed code:" + data.message, "info")
            refresh()
        } else {
            logger("Failed to execute /pauseall: " + response.data, "error")
        }
    }
}

// refresh status
def refresh() {
    sendEvent(name:"lastRefresh", value: "Checking..." , display: true , displayed: false)
    def params = parent.getControllerParams()
    logger("Got Params for Refresh - " + params,"debug")
    params.path =  "/api/4/zone/" + getUID()
    httpGet(params) { response -> 
         if (response.status == 200) {            
             def data = response.data
            logger("zone result: " + data , "debug")
             sendEvent(name: "remaining", value: data.remaining)
             sendEvent(name: "valve", value: data.state == 0 ? "closed" : "open")
             sendEvent(name: "switch", value: data.state == 0 ? "off" : "on")
             sendEvent(name: "paused", value: data.state == 2 ? "yes" : "no")
             updateDeviceLastRefresh()
        } else {
            logger("Failed to get /Zone : " + response.data, "error")
        }
    }
}


// update status
def poll() {
	logger("Polling...", "info")   
    refresh()
}



// stop everything
def stopAll() {
	deviceStatus(0)
    parent.sendCommand2(this, "stopall",  0)
    refresh()
}

private getUID() {
	return device.deviceNetworkId.split("\\|")[2]
}

def updateDeviceType(){
	sendEvent(name: "deviceType", value: parent.getChildType(this), display: false , displayed: true)
}

// update the run time for manual zone
void setRunTime(runTimeSecs) {
	sendEvent("name":"runTime", "value": runTimeSecs)
}

// Legacy compatibility with the time included, though it is ignored
def updateDeviceLastRefresh(time) {
    updateDeviceLastRefresh()
}

def updateDeviceLastRefresh(){
    def refreshDate = new Date()
    def hour = refreshDate.format("h", location.timeZone)
    def minute =refreshDate.format("m", location.timeZone)
    def ampm =refreshDate.format("a", location.timeZone)
    //def finalString = refreshDate.getDateString() + ' ' + hour + ':' + minute + ampm
    
    def finalString = new Date().format('MM/d/yyyy hh:mm',location.timeZone)
    logger("Last refresh final : " + finalString, "debug")
    sendEvent(name: "lastRefresh", value: finalString, display: false , displayed: false)
}

def updateDeviceStatus(status){
	deviceStatus(status)
}

// update status
def deviceStatus(status) {
	def oldStatus = device.currentValue("valve")
	logger("Old Device Status: " + device.currentValue("valve"),  "debug")
    logger("New Device Status: " + status, "debug")

    if (status == 0) {	//Device has turned off

 		//Handle null values
		if (oldStatus == null){
     		sendEvent(name: "switch", value: "off", display: true, displayed: false, isStateChange: true)		// off == closed
 			sendEvent(name: "valve", value: "closed",   display: false, displayed: false)
        }
        sendEvent(name: "paused", value: status == 2 ? "yes" : "no")
        //If device has just recently closed, send notification
        if (oldStatus != 'closed' && oldStatus != null){
        	logger("Logging status.", "debug")
            sendEvent(name: "switch", value: "off", display: true, displayed: false, isStateChange: true)		// off == closed
            sendEvent(name: "valve", value: "closed", display: true, descriptionText: device.displayName + " was inactive")

            //Take note of how long it ran and send notification
            logger("lastStarted: " + device.currentValue("lastStarted"), "debug")
            def lastStarted = device.currentValue("lastStarted")
            def lastActivityValue = "Unknown."

            if (lastStarted != null){
            	lastActivityValue = ""
                long lastStartedLong = lastStarted.toLong()

                logger("lastStarted converted: " + lastStarted, "debug")


                def diffTotal = now() - lastStartedLong
                def diffDays  = (diffTotal / 86400000) as long
                def diffHours = (diffTotal % 86400000 / 3600000) as long
                def diffMins  = (diffTotal % 86400000 % 3600000 / 60000) as long

                if      (diffDays == 1)  lastActivityValue += "${diffDays} Day "
                else if (diffDays > 1)   lastActivityValue += "${diffDays} Days "

                if      (diffHours == 1) lastActivityValue += "${diffHours} Hour "
                else if (diffHours > 1)  lastActivityValue += "${diffHours} Hours "

                if      (diffMins == 1 || diffMins == 0 )  lastActivityValue += "${diffMins} Min"
                else if (diffMins > 1)   lastActivityValue += "${diffMins} Mins"
            }

            def deviceName = device.displayName
            def message = deviceName + " finished watering. Run time: " + lastActivityValue
            logger(message,"info")

            def deviceType = device.currentValue("deviceType")
            logger("Device type is: " + device.currentValue("deviceType"), "debug")

            if (parent.prefSendPush && deviceType.toUpperCase() == "ZONE") {
        		//parent.sendAlert(message)
                //sendNotificationEvent(message.toString())
                parent.sendPushMessage(message)
    		}

            if (parent.prefSendPushPrograms && deviceType.toUpperCase() == "PROGRAM") {
                //sendNotificationEvent(message.toString())
                parent.sendPushMessage(message)
    		}

		}        


	}
	if (status == 1) {	//Device has turned on
		logger("Zone turned on!","debug")

        //If device has just recently opened, take note of time
        if (oldStatus != 'open'){
            logger("Logging status.","debug")
            sendEvent(name: "valve", value: "open", display: true, descriptionText: device.displayName + " was active")
            sendEvent(name: "switch", value: "on", display: true, displayed: false, isStateChange: true)		// on == open

            //Take note of current time the zone started
            def refreshDate = new Date()
            def hour = refreshDate.format("h", location.timeZone)
            def minute =refreshDate.format("m", location.timeZone)
            def ampm =refreshDate.format("a", location.timeZone)
            def finalString = new Date().format('MM/d/yyyy hh:mm',location.timeZone)
            sendEvent(name: "lastStarted", value: now(), display: false , displayed: false)
            logger("stored lastStarted as : " + device.currentValue("lastStarted"),"debug")
        }
        sendEvent(name: "paused", value: status == 2 ? "yes" : "no")
	}
	if (status == 2) {  //Device is pending
		sendEvent(name: "valve", value: "open", display: true, descriptionText: device.displayName + " was pending")        
        sendEvent(name: "paused", value: status == 2 ? "yes" : "no")
	}
}

def showVersion(){
	return "0.9.8"
}


//*******************************************************
//*  logger()
//*
//*  Wrapper function for all logging.
//*******************************************************

private logger(msg, level = "debug") {

    def logLevel = parent.loggingLevel()

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
