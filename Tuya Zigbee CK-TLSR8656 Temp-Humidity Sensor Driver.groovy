/**
 *  Tuya Zigbee Temperature/Humidity Sensor driver for Hubitat Elevation C8-PRO
 *
 *  Version 1.1.1
 *
 *	Copyright 2025 Ivan Piacun, BAP Enterprises Ltd (NZ)
 *
 *  https://community.hubitat.com/t/release-Tuya-Zigbee-CK-TLSR8656-Temp-Humidity-Sensor/86465
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
 *  Preferences Settings:
 *   Enable Debug Logging (debugLogging)
 *   When toggled ON, it activates general driver logs:
 *    Parsing messages
 *    Battery and voltage reports
 *    Scheduling info (auto-refresh, timeouts)
 *    Configuration steps (e.g. when configure() runs)
 *    These are usually log.debug or log.warn entries
 *   This helps you verify that the driver is functioning and responding as expected
 *
 *   Enable Trace Logging (traceLogging)
 *   When toggled ON, it enables detailed sensor value tracing:
 *    Cluster-specific readouts (e.g. 📡 Trace Temp: Raw=1482, Final=14.82)
 *    Calibration offsets applied
 *    tempStatus / humidityStatus outcomes
 *    Works in tandem with Cluster to trace selector:
 *    Choose between "Temperature (0402)" or "Humidity (0405)"
 *
 *   Temperature Change Threshold (tempChangeThreshold)
 *   Sets the minimum temperature change (in °C) required to trigger an update
 *   Default is 0.25°C
 *
 *   Notes:
 *    1) CK-TLSR8656 sensor sleeps between updates and a refresh will not wake it. To wake it and get a refresh 
 *       of Temperature, Humidity, and Battery you need to briefly depress its reset button with a pin.
 *    2) On an update from the sensor the current Temperature Offset is applied to the Raw Temperature and
 *       the Humidity Offset is applied to the Raw humidity and these are displayed as Temperature
 *       and Humidity values.
 *    3) Temperature updates only occur when the change exceeds the configured threshold (default 0.25°C)
 *
 *  Version 1.0.0  2025-07-18  Initial Version
 *  Version 1.0.1  2025-07-18  Synthetic Offset Application on Refresh even if no data arrives. 
 *  Version 1.0.2  2025-07-18  First published version. 
 *  Version 1.0.4  2025-07-19  Added support for fahrenheit configuration. 
 *  Version 1.0.5  2025-07-21  Added Import URL. 
 *  Version 1.0.6  2025-07-31  Rename device driver to a more generalised name from 'Tuya Zigbee CK-TLSR8656 Temp/Humidity SensoR". 
 *                             Added "TS0201"  "_TZ3000_fllyghyj".
 *                             Added Fingerprint matching and Diagnostic
 *  Version 1.0.7  2025-08-02  Surfaced version in attributes 
 *  Version 1.0.8  2025-08-23  Fixed Trace Logging of Temperature
 *  Version 1.1.0  2025-08-23  Changed from 15-minute updates to threshold-based updates (0.25°C default) (Claude AI)
 *  Version 1.1.1  2025-08-24  Renamed Driver back to general name and incorpoaredt code for humidity change threshold (1.0% default)
*/
import java.text.SimpleDateFormat

static String version() { '1.1.1' }
static String timeStamp() { '2025/08/24 07:10 PM' }

metadata { 
    definition(name: "Tuya Zigbee Temperature/Humidity Sensor", namespace: "ivanpiacun.driver", author: "Ivan Piacun & Copilot", importUrl: 'https://raw.githubusercontent.com/IvanPiacun/Hubitat/refs/heads/main/Tuya%20Zigbee%20Temperature-Humidity%20Sensor.groovy') {
    capability "TemperatureMeasurement" 
    capability "RelativeHumidityMeasurement" 
    capability "Battery" 
    capability "VoltageMeasurement"

attribute "tempStatus", "string"
attribute "humidityStatus", "string"
attribute "debugState", "string"
attribute "traceState", "string"
attribute "tempOffsetState", "string"
attribute "humidityOffsetState", "string"
attribute "tempUnitState", "string"
attribute "lastTempChange", "string"
attribute "tempChangeThresholdState", "string"
       
        
command "configure"
command "refresh"
command "forceUpdate"

fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0004,0020,0402,0405,FC11", outClusters:"0019,000A", model:"CK-TLSR8656-SS5-01(7014)", manufacturer:"eWeLink", deviceJoinName: "Tuya Temperature/Humidity Sensor"
fingerprint profileId:"0105", endpointId:"01", inClusters:"0001,0003,0402,0405,0000", outClusters:"0003,0019,000A",  model: "TS0201", manufacturer: "_TZ3000_fllyghyj", deviceJoinName: "Tuya Temperature/Humidity Sensor"

}

preferences { 
    input name: "debugLogging", type: "bool", title: "Enable debug logging?", defaultValue: true
    input name: "traceLogging", type: "bool", title: "Enable trace logging?", defaultValue: false
    input name: "traceCluster", type: "enum", title: "Cluster to trace", options: ["None", "Temperature (0402)", "Humidity (0405)"], defaultValue: "None" 
    input name: "tempUnit", type: "enum", title: "Temperature Unit", options: ["Celsius", "Fahrenheit"], defaultValue: "Celsius", description: "Choose Celsius or Fahrenheit display"
    input name: "tempOffset", type: "decimal", title: "Temperature offset (°C)", defaultValue: 0.00
    input name: "humidityOffset", type: "decimal", title: "Humidity offset (%)", defaultValue: 0.00
    input name: "tempChangeThreshold", type: "decimal", title: "Temperature change threshold (°C)", defaultValue: 0.25, description: "Minimum temperature change to trigger an update"
    input name: "humidityChangeThreshold", type: "decimal", title: "Humidity change threshold (%)", defaultValue: 1.0, description: "Minimum humidity change to trigger an update"
}
}

def getTuyaTempHumidityFingerprints() {
    return [
        [
            profileId: "0104",
            endpointId: "01",
            inClusters: "0000,0001,0003,0004,0020,0402,0405,FC11",
            outClusters: "0019,000A",
            manufacturer: "eWeLink",
            model: "CK-TLSR8656-SS5-01(7014)",
            deviceJoinName: "Tuya Temperature/Humidity Sensor"
        ],
        [
            profileId: "0105",
            endpointId: "01",
            inClusters: "0001,0003,0402,0405,0000",
            outClusters: "0003,0019,000A",
            manufacturer: "_TZ3000_fllyghyj",
            model: "TS0201",
            deviceJoinName: "Tuya Temperature/Humidity Sensor"
        ],
    ]
}

def diagnoseFingerprintMatch() {
    def model = device.getDataValue("model") ?: "UNKNOWN"
    def manufacturer = device.getDataValue("manufacturer") ?: "UNKNOWN"
    def clustersIn = device.getDataValue("inClusters") ?: "UNKNOWN"
    def endpointId = device.getDataValue("endpointId") ?: "UNKNOWN"

    def match = getTuyaTempHumidityFingerprints().find { fp ->
        fp.model == model &&
        fp.manufacturer == manufacturer &&
        fp.inClusters == clustersIn &&
        fp.endpointId == endpointId
    }

    if (match) {
        log.trace "✅ Device matches known fingerprint: ${match.deviceJoinName} (${match.model})"
        state.debugState = "Matched: ${match.model}"
    } else {
        log.warn "❌ No matching fingerprint found for device → Model: ${model}, Manufacturer: ${manufacturer}, Endpoint: ${endpointId}, InClusters: ${clustersIn}"
        state.debugState = "No match"
    }
}

private void processTemperature(Integer rawTemp, Boolean forceUpdate = false) {
    def offset = (settings?.tempOffset ?: 0)
    def tempC = ((rawTemp / 100.0) + offset) as BigDecimal
    def threshold = (settings?.tempChangeThreshold ?: 0.25) as BigDecimal
    
    // Get the last temperature in Celsius for comparison
    def lastTempC = state.lastTempCelsius as BigDecimal ?: null
    
    // Calculate temperature change
    def tempChange = lastTempC ? Math.abs(tempC - lastTempC) : threshold + 1
    
    // Check if update should be sent
    def shouldUpdate = forceUpdate || (lastTempC == null) || (tempChange >= threshold)
    
    if (shouldUpdate) {
        def converted = tempC
        def unit = "°C"
        if (settings?.tempUnit == "Fahrenheit") {
            converted = (tempC * 1.8 + 32).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
            unit = "°F"
        }
        
        state.tempStatus = ((tempC < 18.0) ? "Cold" : (tempC > 24.0) ? "Hot" : "Comfortable")
        state.lastTempCelsius = tempC
        
        sendEvent(name: "temperature", value: converted, unit: unit)
        sendEvent(name: "tempUnitState", value: settings?.tempUnit ?: "Celsius")
        sendEvent(name: "tempStatus", value: state.tempStatus)
        sendEvent(name: "lastTempChange", value: getFormattedDateTime())
        
        if (settings?.traceLogging) {
            log.trace "📏 processTemperature() → RawTemp=${rawTemp} | Offset=${offset} | Final=${converted}${unit} | Change=${tempChange}°C"
        }
        if (debugLogging) {
            log.debug "Temperature updated: ${converted}${unit} (change: ${tempChange}°C)"
        }
    } else {
        if (settings?.traceLogging) {
            log.trace "📏 Temperature change (${tempChange}°C) below threshold (${threshold}°C), skipping update"
        }
    }
}

private void processHumidity(Integer rawHumidity, Boolean forceUpdate = false) {
    def offsetHumidity = (settings?.humidityOffset ?: 0.0) as BigDecimal
    def finalHumidity = (offsetHumidity + (rawHumidity / 100.0)) as BigDecimal
    def threshold = (settings?.humidityChangeThreshold ?: 1.0) as BigDecimal
    
    // Get the last humidity for comparison
    def lastHumidity = state.lastHumidity as BigDecimal ?: null
    
    // Calculate humidity change
    def humidityChange = lastHumidity ? Math.abs(finalHumidity - lastHumidity) : threshold + 1
    
    // Check if update should be sent
    def shouldUpdate = forceUpdate || (lastHumidity == null) || (humidityChange >= threshold)
    
    if (shouldUpdate) {
        state.lastHumidity = finalHumidity
        sendEvent(name: "humidity", value: finalHumidity, unit: "%")
        state.humidityStatus = (finalHumidity < 40) ? "Dry" : (finalHumidity > 65) ? "Humid" : "Comfortable"
        sendEvent(name: "humidityStatus", value: state?.humidityStatus)
        
        if (traceLogging && traceCluster == "Humidity (0405)") {
            log.trace "💧 processHumidity() → Raw=${rawHumidity} | Offset=${offsetHumidity} | Final=${finalHumidity} | Change=${humidityChange}%"
        }
        if (debugLogging) {
            log.debug "Humidity updated: ${finalHumidity}% (change: ${humidityChange}%)"
        }
    } else {
        if (settings?.traceLogging) {
            log.trace "💧 Humidity change (${humidityChange}%) below threshold (${threshold}%), skipping update"
        }
    }
}

private void applySyntheticTempIfNoData(Integer lastKnownRaw) {
    if (lastKnownRaw == null) return
    processTemperature(lastKnownRaw, true)  // Force update on refresh
}

private void applySyntheticHumidityIfNoData(Integer lastKnownRaw) {
    if (lastKnownRaw == null) return
    processHumidity(lastKnownRaw, true)  // Force update on refresh
}
def initialize() {
    log.debug "⚙️ initialize() called"
    diagnoseFingerprintMatch()
    configure()
}

def uninstalled() {
    unschedule()
    state.clear()
    log.debug "🚪 uninstalled() → Driver resources released"
}

def getFormattedDateTime() {
    def formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return formatter.format(new Date())
}

def driverVersionAndTimeStamp() { version() + ' ' + timeStamp() }

def parse(String description) { 
    if (debugLogging) log.debug "Parsing: $description" 
    def descMap = zigbee.parseDescriptionAsMap(description)

    state.lastSensorReport = getFormattedDateTime()

    switch(descMap.clusterInt) {
        case 0x0402:
            if (traceLogging && traceCluster == "Temperature (0402)") {
               log.trace "🌡 Handling Temperature Measurement cluster (0402)"
            }
            def rawTemp = Integer.parseInt(descMap.value, 16)
            state.lastTempRaw = rawTemp
            processTemperature(rawTemp)
            break
            
        case 0x0405:
            if (traceLogging && traceCluster == "Humidity (0405)") {
               log.trace "💧 Handling Humidity Measurement cluster (0405)"
            }
            def rawHumidity = Integer.parseInt(descMap.value, 16)
            state.lastHumidityRaw = rawHumidity
            processHumidity(rawHumidity)
            break

        case 0x0001:
            if (descMap.attrInt == 0x0021) {
                def rawBattery = Integer.parseInt(descMap.value, 16)
                def battery = (rawBattery / 2).toInteger()
                sendEvent(name: "battery", value: battery, unit: "%")
                if (debugLogging) log.debug "Battery raw=${rawBattery}, final=${battery}%"
            } else if (descMap.attrInt == 0x0020) {
                def voltage = Integer.parseInt(descMap.value, 16) / 10.0
                sendEvent(name: "voltage", value: voltage, unit: "V")
                if (debugLogging) log.debug "Voltage=${voltage}V"
            }
            break

        default:
            if (debugLogging) log.debug "Unhandled cluster: ${descMap.clusterInt}"
            break
    }
}

def refresh() {  
    applySyntheticTempIfNoData(state?.lastTempRaw)
    applySyntheticHumidityIfNoData(state?.lastHumidityRaw)
    if (debugLogging) log.debug "🔄 Refresh requested..."
    state.lastSensorReport = getFormattedDateTime() 
    return [ 
        zigbee.readAttribute(0x0402, 0x0000), 
        zigbee.readAttribute(0x0405, 0x0000), 
        zigbee.readAttribute(0x0001, 0x0020), 
        zigbee.readAttribute(0x0001, 0x0021) 
    ] 
}

// Note: scheduledRefresh() definition is still required to be able to unschedule an existing occurance for a previous
// version that was installed on the Hubitat.
def scheduledRefresh() {
    if (settings?.traceLogging) log.trace "🔄 scheduledRefresh() triggered"
    refresh()
}

def forceUpdate() {
    log.info "⚡ Force update requested - bypassing thresholds"
    if (state?.lastTempRaw != null) {
        processTemperature(state.lastTempRaw, true)
    }
    if (state?.lastHumidityRaw != null) {
        processHumidity(state.lastHumidityRaw, true)
    }
}

def configure() {
    unschedule()
    // Removed the 15-minute scheduled refresh since we're now threshold-based
    // runEvery15Minutes("scheduledRefresh")
    // You can uncomment the next line if you want periodic battery checks
    // runEvery1Hour("checkBattery")
    
    state.driverVersion = driverVersionAndTimeStamp()
 
    def tempUnit = settings?.tempUnit ?: "Celsius"
    def tempOffsetLabel = "${settings?.tempOffset ?: 0.0}°C"
    def humidityOffsetLabel = "${settings?.humidityOffset ?: 0.0}%"
    def tempThresholdLabel = "${settings?.tempChangeThreshold ?: 0.25}°C"
    def humidityThresholdLabel = "${settings?.humidityChangeThreshold ?: 1.0}%"   
    
    if (state?.lastTempRaw != null) {
        processTemperature(state.lastTempRaw, true)  // Force update on config change
        log.info "🔁 Re-sent temp reading after configuration update"
    }
 
    if (state?.lastHumidityRaw != null) {
        processHumidity(state.lastHumidityRaw, true)  // Force update on config change
        log.info "🔁 Re-sent humidity reading after configuration update"
    }
    sendEvent(name: "tempUnitState", value: tempUnit)
    sendEvent(name: "tempOffsetState", value: tempOffsetLabel)
    sendEvent(name: "humidityOffsetState", value: humidityOffsetLabel)
    sendEvent(name: "tempChangeThresholdState", value: tempThresholdLabel)
    sendEvent(name: "humidityChangeThresholdState", value: humidityThresholdLabel)
    sendEvent(name: "debugState", value: "🌡 Threshold-based updates (${tempThresholdLabel})")

    if (debugLogging) log.debug "🔧 Configuring sensor bindings and reporting..."
    
    def cmds = []
    
    // Bind clusters
    cmds += zigbee.configureReporting(0x0402, 0x0000, DataType.INT16, 30, 3600, 25) // Temp every 30s-1hr, change of 0.25°C
    cmds += zigbee.configureReporting(0x0405, 0x0000, DataType.UINT16, 30, 3600, 100) // Humidity every 30s-1hr, change of 1%
    cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 3600, 21600, 1) // Battery every 1-6hrs, change of 1%
    
    // Removed the automatic refresh since sensor sleeps
    // runEvery15Minutes("scheduledRefresh")  // Comment this out
    
     if (settings?.traceLogging) {
        log.trace "🛠 configure() → Offsets: Temp=${tempOffsetLabel}, Humidity=${humidityOffsetLabel} | Humidity Threshold: ${humidityThresholdLabel} | Unit: ${tempUnit} | Threshold: ${tempThresholdLabel}"
    } else {
        log.info "🔧 configure() complete - Updates on ${tempThresholdLabel} change"
    }   
    if (debugLogging) log.debug "🔧 Configure complete - sensor should now report automatically"
    return cmds
}
def installed() { 
    state.lastSensorReport = now() 
    configure() 
}

def updated() { 
    sendEvent(name: "debugState", value: (settings?.debugLogging ?: false) ? "ON" : "OFF") 
    sendEvent(name: "traceState", value: (settings?.traceLogging ?: false) ? "ON" : "OFF") 
    def unit = (settings?.tempUnit == "Fahrenheit") ? "°F" : "°C"
    sendEvent(name: "tempOffsetState", value: "${settings?.tempOffset ?: 0.0}${unit}") 
    sendEvent(name: "humidityOffsetState", value: "${settings?.humidityOffset ?: 0.0}%")
    sendEvent(name: "tempChangeThresholdState", value: "${settings?.tempChangeThreshold ?: 0.25}°C")
    configure()
}

def checkBattery() {
    if (debugLogging) log.debug "🔋 Checking battery status"
    return zigbee.readAttribute(0x0001, 0x0021)
}

def logsOff() { 
    log.warn "Debug logging disabled." 
    device.updateSetting("debugLogging", [value:"false", type:"bool"]) 
    sendEvent(name: "debugState", value: "OFF") 
}

def timeoutSeconds() { return 3600 }  // Increased to 1 hour since updates are event-driven

def checkForTimeout() { 
    def last = state.lastSensorReport ?: 0 
    def elapsed = (now() - last) / 1000 
    if (elapsed > timeoutSeconds()) { 
        log.warn "⚠️ No sensor data received in last ${(elapsed/60).toInteger()} minutes. Check Zigbee link or sensor battery." 
    } else { 
        if (debugLogging) log.debug "✅ Sensor data received within timeout (${(elapsed/60).toInteger()} minutes ago)" 
    } 
}
