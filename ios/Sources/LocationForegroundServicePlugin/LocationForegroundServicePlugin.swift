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
        CAPPluginMethod(name: "echo", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = LocationForegroundService()

    @objc func echo(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve([
            "value": implementation.echo(value)
        ])
    }
}
