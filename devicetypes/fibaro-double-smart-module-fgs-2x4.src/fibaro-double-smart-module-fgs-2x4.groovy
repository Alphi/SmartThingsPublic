/**
 *  FIBARO Double Smart Module 2
 */
metadata {
    definition (name: "Fibaro Double Smart Module FGS2x4", namespace: "FibarGroup", author: "Fibar Group") {
        capability "Switch"
        capability "Button"
        capability "Configuration"
        capability "Health Check"
		capability "Refresh"

		fingerprint mfr: "010F", prod: "0203", model: "2000", deviceJoinName: "Fibaro Switch"
		fingerprint mfr: "010F", prod: "0203", model: "1000", deviceJoinName: "Fibaro Switch"
		fingerprint mfr: "010F", prod: "0203", model: "3000", deviceJoinName: "Fibaro Switch"
    }

    tiles (scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 3, height: 4){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "off", label: '', action: "switch.on", icon: "https://s3-eu-west-1.amazonaws.com/fibaro-smartthings/switch/switch_2.png", backgroundColor: "#ffffff"
                attributeState "on", label: '', action: "switch.off", icon: "https://s3-eu-west-1.amazonaws.com/fibaro-smartthings/switch/switch_1.png", backgroundColor: "#00a0dc"
            }
            tileAttribute("device.multiStatus", key:"SECONDARY_CONTROL") {
                attributeState("multiStatus", label:'${currentValue}')
            }
        }
        standardTile("main", "device.switch", decoration: "flat", canChangeIcon: true) {
            state "off", label: 'off', action: "switch.on", icon: "https://s3-eu-west-1.amazonaws.com/fibaro-smartthings/switch/switch_2.png", backgroundColor: "#ffffff"
            state "on", label: 'on', action: "switch.off", icon: "https://s3-eu-west-1.amazonaws.com/fibaro-smartthings/switch/switch_1.png", backgroundColor: "#00a0dc"
        }
		main(["switch"])
		details(["switch"])
    }

    preferences {
        input (
                title: "Fibaro Double Smart Module FGS-2x4 manual",
                description: "Tap to view the manual.",
                image: "https://manuals.fibaro.com/wp-content/uploads/2020/04/srs_icon.png",
                url: "https://manuals.fibaro.com/content/manuals/en/FGS-2x4/FGS-2x4-T-EN-1.0.pdf",
                type: "href",
                element: "href"
        )

		parameterMap().each {
			input (
					title: "${it.title}",
					description: it.descr,
					type: "paragraph",
					element: "paragraph"
			)
			def defVal = it.def as Integer
			def descrDefVal = it.options ? it.options.get(defVal) : defVal
			input (
					name: it.key,
					title: null,
					description: "$descrDefVal",
					type: it.type,
					options: it.options,
					range: (it.min != null && it.max != null) ? "${it.min}..${it.max}" : null,
					defaultValue: it.def,
					required: false
			)
		}

		input ( name: "logging", title: "Logging", type: "boolean", required: false )
    }
}

def on() {
    encap(zwave.basicV1.basicSet(value: 255),1)
}

def off() {
    encap(zwave.basicV1.basicSet(value: 0),1)
}

def childOn() {
    sendHubCommand(response(encap(zwave.basicV1.basicSet(value: 255),2)))
}

def childOff() {
    sendHubCommand(response(encap(zwave.basicV1.basicSet(value: 0),2)))
}

def refresh() {
	def cmds = []
	cmds << [zwave.meterV3.meterGet(scale: 0), 1]
	cmds << [zwave.meterV3.meterGet(scale: 2), 1]
	cmds << [zwave.switchBinaryV1.switchBinaryGet(),1]
	encapSequence(cmds,1000)
}

def childRefresh() {
	def cmds = []
	cmds << response(encap(zwave.meterV3.meterGet(scale: 0), 2))
	cmds << response(encap(zwave.meterV3.meterGet(scale: 2), 2))
	cmds << response(encap(zwave.switchBinaryV1.switchBinaryGet(), 2))
	sendHubCommand(cmds,1000)
}

def installed(){
	sendEvent(name: "checkInterval", value: 1920, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
	initialize()
	response(refresh())
}

def ping() {
	refresh()
}

//Configuration and synchronization
def updated() {
	if ( state.lastUpdated && (now() - state.lastUpdated) < 500 ) return
	def cmds = initialize()
	if (device.label != state.oldLabel) {
		childDevices.each {
			def newLabel = "${device.displayName} - USB"
			it.setLabel(newLabel)
		}
		state.oldLabel = device.label
	}
	return cmds
}

def initialize() {
	def cmds = []
	logging("${device.displayName} - Executing initialize()","info")
	if (!childDevices) {
		createChildDevices()
	}
	if (device.currentValue("numberOfButtons") != 6) { sendEvent(name: "numberOfButtons", value: 6) }

	cmds << zwave.multiChannelAssociationV2.multiChannelAssociationGet(groupingIdentifier: 1) //verify if group 1 association is correct  
	runIn(3, "syncStart")
	state.lastUpdated = now()
	response(encapSequence(cmds,1000))
}

def syncStart() {
	boolean syncNeeded = false
	parameterMap().each {
		if(settings."$it.key" != null) {
			if (state."$it.key" == null) { state."$it.key" = [value: null, state: "synced"] }
			if (state."$it.key".value != settings."$it.key" as Integer || state."$it.key".state in ["notSynced","inProgress"]) {
				state."$it.key".value = settings."$it.key" as Integer
				state."$it.key".state = "notSynced"
				syncNeeded = true
			}
		}
	}
	if ( syncNeeded ) {
		logging("${device.displayName} - starting sync.", "info")
		multiStatusEvent("Sync in progress.", true, true)
		syncNext()
	}
}

private syncNext() {
	logging("${device.displayName} - Executing syncNext()","info")
	def cmds = []
	for ( param in parameterMap() ) {
		if ( state."$param.key"?.value != null && state."$param.key"?.state in ["notSynced","inProgress"] ) {
			multiStatusEvent("Sync in progress. (param: ${param.num})", true)
			state."$param.key"?.state = "inProgress"
			cmds << response(encap(zwave.configurationV2.configurationSet(configurationValue: intToParam(state."$param.key".value, param.size), parameterNumber: param.num, size: param.size)))
			cmds << response(encap(zwave.configurationV2.configurationGet(parameterNumber: param.num)))
			break
		}
	}
	if (cmds) {
		runIn(10, "syncCheck")
		log.debug "cmds!"
		sendHubCommand(cmds,1000)
	} else {
		runIn(1, "syncCheck")
	}
}

def syncCheck() {
	logging("${device.displayName} - Executing syncCheck()","info")
	def failed = []
	def incorrect = []
	def notSynced = []
	parameterMap().each {
		if (state."$it.key"?.state == "incorrect" ) {
			incorrect << it
		} else if ( state."$it.key"?.state == "failed" ) {
			failed << it
		} else if ( state."$it.key"?.state in ["inProgress","notSynced"] ) {
			notSynced << it
		}
	}
	if (failed) {
		logging("${device.displayName} - Sync failed! Check parameter: ${failed[0].num}","info")
		sendEvent(name: "syncStatus", value: "failed")
		multiStatusEvent("Sync failed! Check parameter: ${failed[0].num}", true, true)
	} else if (incorrect) {
		logging("${device.displayName} - Sync mismatch! Check parameter: ${incorrect[0].num}","info")
		sendEvent(name: "syncStatus", value: "incomplete")
		multiStatusEvent("Sync mismatch! Check parameter: ${incorrect[0].num}", true, true)
	} else if (notSynced) {
		logging("${device.displayName} - Sync incomplete!","info")
		sendEvent(name: "syncStatus", value: "incomplete")
		multiStatusEvent("Sync incomplete! Open settings and tap Done to try again.", true, true)
	} else {
		logging("${device.displayName} - Sync Complete","info")
		sendEvent(name: "syncStatus", value: "synced")
		multiStatusEvent("Sync OK.", true, true)
	}
}

private multiStatusEvent(String statusValue, boolean force = false, boolean display = false) {
	if (!device.currentValue("multiStatus")?.contains("Sync") || device.currentValue("multiStatus") == "Sync OK." || force) {
		sendEvent(name: "multiStatus", value: statusValue, descriptionText: statusValue, displayed: display)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
	def paramKey = parameterMap().find( {it.num == cmd.parameterNumber } ).key
	logging("${device.displayName} - Parameter ${paramKey} value is ${cmd.scaledConfigurationValue} expected " + state."$paramKey".value, "info")
	state."$paramKey".state = (state."$paramKey".value == cmd.scaledConfigurationValue) ? "synced" : "incorrect"
	syncNext()
}

private createChildDevices() {
    logging("${device.displayName} - executing createChildDevices()","info")
    addChildDevice(
            "Fibaro Double Smart Module 2 - CH2",
            "${device.deviceNetworkId}-2",
            null,
            [completedSetup: true, label: "${device.displayName} (CH2)", isComponent: false, componentName: "ch2", componentLabel: "Channel 2"]
    )
}

private physicalgraph.app.ChildDeviceWrapper getChild(Integer childNum) {
    return childDevices.find({ it.deviceNetworkId == "${device.deviceNetworkId}-${childNum}" })
}

def zwaveEvent(physicalgraph.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
    logging("${device.displayName} - rejected request!","warn")
    for ( param in parameterMap() ) {
        if ( state."$param.key"?.state == "inProgress" ) {
            state."$param.key"?.state = "failed"
            break
        }
    }
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelassociationv2.MultiChannelAssociationReport cmd) {
    def cmds = []
    if (cmd.groupingIdentifier == 1) {
        if (cmd.nodeId != [0, zwaveHubNodeId, 1]) {
            log.debug "${device.displayName} - incorrect MultiChannel Association for Group 1! nodeId: ${cmd.nodeId} will be changed to [0, ${zwaveHubNodeId}, 1]"
            cmds << zwave.multiChannelAssociationV2.multiChannelAssociationRemove(groupingIdentifier: 1)
            cmds << zwave.multiChannelAssociationV2.multiChannelAssociationSet(groupingIdentifier: 1, nodeId: [0,zwaveHubNodeId,1])
        } else {
            logging("${device.displayName} - MultiChannel Association for Group 1 correct.","info")
        }
    }
    if (cmds) { [response(encapSequence(cmds, 1000))] }
}

//event handlers
def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    //ignore
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, ep=null) {
    logging("${device.displayName} - SwitchBinaryReport received, value: ${cmd.value} ep: $ep","info")
    switch (ep) {
        case 1:
            sendEvent([name: "switch", value: (cmd.value == 0 ) ? "off": "on"])
            break
        case 2:
            getChild(2)?.sendEvent([name: "switch", value: (cmd.value == 0 ) ? "off": "on"])
            break
    }
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd, ep=null) {
    logging("${device.displayName} - MeterReport received, value: ${cmd.scaledMeterValue} scale: ${cmd.scale} ep: $ep","info")
    if (ep==1) {
        switch (cmd.scale) {
            case 0:
                sendEvent([name: "energy", value: cmd.scaledMeterValue, unit: "kWh"])
                break
            case 2:
                sendEvent([name: "power", value: cmd.scaledMeterValue, unit: "W"])
                break
        }
        multiStatusEvent("${(device.currentValue("power") ?: "0.0")} W | ${(device.currentValue("energy") ?: "0.00")} kWh")

    } else if (ep==2) {
        switch (cmd.scale) {
            case 0:
                getChild(2)?.sendEvent([name: "energy", value: cmd.scaledMeterValue, unit: "kWh"])
                break
            case 2:
                getChild(2)?.sendEvent([name: "power", value: cmd.scaledMeterValue, unit: "W"])
                break
        }
        getChild(2)?.sendEvent([name: "combinedMeter", value: "${(getChild(2)?.currentValue("power") ?: "0.0")} W / ${(getChild(2)?.currentValue("energy") ?: "0.00")} kWh", displayed: false])
    }
}

def zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    logging("${device.displayName} - CentralSceneNotification received, sceneNumber: ${cmd.sceneNumber} keyAttributes: ${cmd.keyAttributes}","info")
    log.info cmd
    def String action
    def Integer button
    switch (cmd.keyAttributes as Integer) {
        case 0: action = "pushed"; button = cmd.sceneNumber; break
        case 1: action = "released"; button = cmd.sceneNumber; break
        case 2: action = "held"; button = cmd.sceneNumber; break
        case 3: action = "pushed"; button = 2+(cmd.sceneNumber as Integer); break
        case 4: action = "pushed"; button = 4+(cmd.sceneNumber as Integer); break
    }
    log.info "button $button $action"
    sendEvent(name: "button", value: action, data: [buttonNumber: button], isStateChange: true)
}

/*
####################
## Z-Wave Toolkit ##
####################
*/
def parse(String description) {
    def result = []
    logging("${device.displayName} - Parsing: ${description}")
    if (description.startsWith("Err 106")) {
        result = createEvent(
                descriptionText: "Failed to complete the network security key exchange. If you are unable to receive data from it, you must remove it from your network and add it again.",
                eventType: "ALERT",
                name: "secureInclusion",
                value: "failed",
                displayed: true,
        )
    } else if (description == "updated") {
        return null
    } else {
        def cmd = zwave.parse(description, cmdVersions())
        if (cmd) {
            logging("${device.displayName} - Parsed: ${cmd}")
            zwaveEvent(cmd)
        }
    }
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand(cmdVersions())
    if (encapsulatedCommand) {
        logging("${device.displayName} - Parsed SecurityMessageEncapsulation into: ${encapsulatedCommand}")
        zwaveEvent(encapsulatedCommand)
    } else {
        log.warn "Unable to extract Secure command from $cmd"
    }
}

def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd) {
    def version = cmdVersions()[cmd.commandClass as Integer]
    def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
    def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
    if (encapsulatedCommand) {
        logging("${device.displayName} - Parsed Crc16Encap into: ${encapsulatedCommand}")
        zwaveEvent(encapsulatedCommand)
    } else {
        log.warn "Unable to extract CRC16 command from $cmd"
    }
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand(cmdVersions())
    if (encapsulatedCommand) {
        logging("${device.displayName} - Parsed MultiChannelCmdEncap ${encapsulatedCommand}")
        zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
    } else {
        log.warn "Unable to extract MultiChannel command from $cmd"
    }
}

private logging(text, type = "debug") {
    if (settings.logging == "true") {
        log."$type" text
    }
}

private secEncap(physicalgraph.zwave.Command cmd) {
    logging("${device.displayName} - encapsulating command using Secure Encapsulation, command: $cmd","info")
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private crcEncap(physicalgraph.zwave.Command cmd) {
    logging("${device.displayName} - encapsulating command using CRC16 Encapsulation, command: $cmd","info")
    zwave.crc16EncapV1.crc16Encap().encapsulate(cmd).format()
}

private multiEncap(physicalgraph.zwave.Command cmd, Integer ep) {
    logging("${device.displayName} - encapsulating command using MultiChannel Encapsulation, ep: $ep command: $cmd","info")
    zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:ep).encapsulate(cmd)
}

private encap(physicalgraph.zwave.Command cmd, Integer ep) {
    encap(multiEncap(cmd, ep))
}

private encap(List encapList) {
    encap(encapList[0], encapList[1])
}

private encap(Map encapMap) {
    encap(encapMap.cmd, encapMap.ep)
}

private encap(physicalgraph.zwave.Command cmd) {
    if (zwaveInfo.zw.contains("s")) {
        secEncap(cmd)
    } else if (zwaveInfo.cc.contains("56")){
        crcEncap(cmd)
    } else {
        logging("${device.displayName} - no encapsulation supported for command: $cmd","info")
        cmd.format()
    }
}

private encapSequence(cmds, Integer delay=250) {
    delayBetween(cmds.collect{ encap(it) }, delay)
}

private encapSequence(cmds, Integer delay, Integer ep) {
    delayBetween(cmds.collect{ encap(it, ep) }, delay)
}

private List intToParam(Long value, Integer size = 1) {
    def result = []
    size.times {
        result = result.plus(0, (value & 0xFF) as Short)
        value = (value >> 8)
    }
    return result
}
/*
##########################
## Device Configuration ##
##########################
*/
private Map cmdVersions() {
    [0x5E: 2, 0x25: 1, 0x85: 2, 0x8E: 3, 0x59: 2, 0x55: 2, 0x86: 2, 0x72: 2, 0x5A: 1, 0x73: 1, 0x98: 1, 0x9F: 1, 0x70: 1, 0x56: 1, 0x71: 8, 0x75: 2, 0x5B: 3, 0x7A: 4, 0x22: 1, 0x6C: 1, 0x60: 4, 0x20: 1] //Fibaro Double Smart Module 2
}

private parameterMap() {[
        [key: "restoreState", num: 1, size: 1, type: "enum", options: [
                0: "power off after power failure",
                1: "restore state",
                2: "restore to toggle state"
        ], def: "0", title: "Restore state after power failure",
         descr: "This parameter determines the state of relays after power supply failure (e.g. power outage). For auto OFF and flashing modes the parameter is not relevant and the relay will always remain switched off.."],
        
        [key: "switchTypeS1", num: 20, size: 1, type: "enum", options: [
                0: "momentary switch",
                1: "toggle switch (contact closed - ON, contact opened - OFF)",
                2: "toggle switch (device changes status when switch changes status)"
        ], def: "0", title: "Switch type S1",
         descr: "Parameter defines as what type the device should treat the switch connected to the S1 terminal"],
        
        [key: "switchTypeS2", num: 21, size: 1, type: "enum", options: [
                0: "momentary switch",
                1: "toggle switch (contact closed - ON, contact opened - OFF)",
                2: "toggle switch (device changes status when switch changes status)"
        ], def: "0", title: "Switch type S2",
         descr: "Parameter defines as what type the device should treat the switch connected to the S2 terminal"],
        
        [key: "inputOrientation", num: 24, size: 1, type: "enum", options: [
                0: "default (S1: ch1, S2: ch2)",
                1: "reversed (S1: ch2, S2: ch1)",
        ], def: "0", title: "Inputs orientation",
         descr: "This parameter allows reversing operation of S1 and S2 inputs without changing the wiring. Use in case of incorrect wiring."],
        
        [key: "outputOrientation", num: 25, size: 1, type: "enum", options: [
                0: "default (Q1: ch1, Q2: ch2)",
                1: "reversed (Q1: ch2, Q2: ch1)",
        ], def: "0", title: "Outputs orientation",
         descr: "This parameter allows reversing operation of Q1 and Q2 outputs without changing the wiring. Use in case of incorrect wiring."],
        
        [key: "operationModeS1", num: 150, size: 1, type: "enum", options: [
                0: "standard operation",
                1: "delayed OFF",
                2: "auto OFF",
                3: "flashing",
        ], def: "0", title: "Operation Mode S1",
         descr: "This parameter allows to choose operating mode for channel controlled with Q/Q1 output. For timed modes (value 1, 2 or 3), time is set with parameter 154 and reaction to input change is set with parameter 152"],

        [key: "operationModeSS", num: 151, size: 1, type: "enum", options: [
                0: "standard operation",
                1: "delayed OFF",
                2: "auto OFF",
                3: "flashing",
        ], def: "0", title: "Operation Mode Ss",
         descr: "This parameter allows to choose operating mode for channel controlled with Q/Q1 output. For timed modes (value 1, 2 or 3), time is set with parameter 155 and reaction to input change is set with parameter 153"],

        [key: "outputTypeQ1", num: 162, size: 1, type: "enum", options: [
                0: "NO Normally Open",
                1: "NC Normally Closed"
        ], def: "0", title: "Output type Q1",
         descr: "This parameter determines type of Q1 output"],

        [key: "outputTypeQ2", num: 163, size: 1, type: "enum", options: [
                0: "NO Normally Open",
                1: "NC Normally Closed"
        ], def: "0", title: "Output type Q2",
         descr: "This parameter determines type of Q2 output"]
]}
