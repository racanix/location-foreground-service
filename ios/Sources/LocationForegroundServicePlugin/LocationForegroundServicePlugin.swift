import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(LocationForegroundServicePlugin)
public class LocationForegroundServicePlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "LocationForegroundServicePlugin"
    public let jsName = "LocationForegroundService"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "startTracking", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopTracking", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isTracking", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "addAlert", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeAlert", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "existsAlert", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getAllAlerts", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "existAlertType", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getAlertCount", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearAllAlerts", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = LocationForegroundService()

    @objc func startTracking(_ call: CAPPluginCall) {
        // TODO: Implement iOS tracking
        call.resolve(["running": true])
    }

    @objc func stopTracking(_ call: CAPPluginCall) {
        // TODO: Implement iOS stop tracking
        call.resolve(["running": false])
    }

    @objc func isTracking(_ call: CAPPluginCall) {
        // TODO: Check if tracking
        call.resolve(["running": false])
    }

    @objc func addAlert(_ call: CAPPluginCall) {
        // TODO: Implement addAlert
        call.resolve()
    }

    @objc func removeAlert(_ call: CAPPluginCall) {
        // TODO: Implement removeAlert
        call.resolve()
    }

    @objc func existsAlert(_ call: CAPPluginCall) {
        // TODO: Implement existsAlert
        call.resolve(["alert": NSNull()])
    }

    @objc func getAllAlerts(_ call: CAPPluginCall) {
        // TODO: Implement getAllAlerts
        call.resolve(["alerts": []])
    }

    @objc func existAlertType(_ call: CAPPluginCall) {
        // TODO: Implement existAlertType
        call.resolve(["exists": false])
    }

    @objc func getAlertCount(_ call: CAPPluginCall) {
        // TODO: Implement getAlertCount
        call.resolve(["count": 0])
    }

    @objc func clearAllAlerts(_ call: CAPPluginCall) {
        // TODO: Implement clearAllAlerts
        call.resolve(["success": true])
    }
}
