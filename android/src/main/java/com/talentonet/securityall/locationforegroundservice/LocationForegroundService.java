package com.talentonet.securityall.locationforegroundservice;

import com.getcapacitor.Logger;

public class LocationForegroundService {

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }

    public String print(String message) {
        Logger.info("Print from JAVA", message);
        return message;
    }
}
