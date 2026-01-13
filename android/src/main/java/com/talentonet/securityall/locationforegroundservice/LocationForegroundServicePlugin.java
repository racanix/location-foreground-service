package com.talentonet.securityall.locationforegroundservice;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "LocationForegroundService")
public class LocationForegroundServicePlugin extends Plugin {

    private LocationForegroundService implementation = new LocationForegroundService();

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    @PluginMethod
    public void print(PluginCall call) {
        String message = call.getString("value");
        if (message == null) {
            call.reject("Must provide a value");
            return;
        }

        JSObject ret = new JSObject();
        ret.put("value", implementation.print(message));
        call.resolve(ret);
    }
}
