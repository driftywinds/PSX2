package com.izzy2lost.psx2;

import android.content.Context;
import android.view.InputDevice;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller configuration and detection utility
 * Based on AetherSX2's controller support
 */
public class ControllerConfig {
    private static final String TAG = "ControllerConfig";
    
    /**
     * Get list of connected controllers
     */
    public static List<ControllerInfo> getConnectedControllers() {
        List<ControllerInfo> controllers = new ArrayList<>();
        
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && isController(device)) {
                controllers.add(new ControllerInfo(deviceId, device.getName(), getControllerType(device)));
            }
        }
        
        return controllers;
    }
    
    /**
     * Check if a device is a controller
     */
    private static boolean isController(InputDevice device) {
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
               (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }
    
    /**
     * Get controller type description
     */
    private static String getControllerType(InputDevice device) {
        String name = device.getName().toLowerCase(Locale.ROOT);
        
        if (name.contains("xbox")) {
            return "Xbox Controller";
        } else if (name.contains("playstation") || name.contains("ps4") || name.contains("ps5") || name.contains("dualshock") || name.contains("dualsense")) {
            return "PlayStation Controller";
        } else if (name.contains("nintendo") || name.contains("pro controller")) {
            return "Nintendo Controller";
        } else if (name.contains("steam")) {
            return "Steam Controller";
        } else {
            return "Generic Controller";
        }
    }
    
    /**
     * Log controller information for debugging
     */
    public static void logControllerInfo(Context context) {
        List<ControllerInfo> controllers = getConnectedControllers();
        
        Log.i(TAG, "=== Connected Controllers ===");
        if (controllers.isEmpty()) {
            Log.i(TAG, "No controllers detected");
        } else {
            for (ControllerInfo controller : controllers) {
                Log.i(TAG, "Controller: " + controller.name + " (ID: " + controller.deviceId + ", Type: " + controller.type + ")");
            }
        }
        Log.i(TAG, "============================");
    }
    
    /**
     * Controller information holder
     */
    public static class ControllerInfo {
        public final int deviceId;
        public final String name;
        public final String type;
        
        public ControllerInfo(int deviceId, String name, String type) {
            this.deviceId = deviceId;
            this.name = name;
            this.type = type;
        }
        
        @Override
        public String toString() {
            return name + " (" + type + ")";
        }
    }
    
    /**
     * Get button mapping description for user reference
     */
    public static String getButtonMappingDescription() {
        return "Controller Button Mapping:\n\n" +
               "• A Button → Cross (X)\n" +
               "• B Button → Circle (O)\n" +
               "• X Button → Square (□)\n" +
               "• Y Button → Triangle (△)\n" +
               "• L1/LB → L1\n" +
               "• R1/RB → R1\n" +
               "• L2/LT → L2\n" +
               "• R2/RT → R2\n" +
               "• Left Stick Click → L3\n" +
               "• Right Stick Click → R3\n" +
               "• Select/Back → Select\n" +
               "• Start/Menu → Start\n" +
               "• D-Pad → D-Pad\n" +
               "• Left Stick → Left Analog\n" +
               "• Right Stick → Right Analog";
    }
}