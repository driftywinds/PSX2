package com.izzy2lost.psx2;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

/**
 * Dialog for testing controller input
 */
public class ControllerTestDialogFragment extends DialogFragment implements ControllerInputHandler.ControllerInputListener {
    
    private TextView mControllerListText;
    private TextView mInputLogText;
    private StringBuilder mInputLog = new StringBuilder();
    private ControllerInputHandler mControllerHandler;
    
    public static ControllerTestDialogFragment newInstance() {
        return new ControllerTestDialogFragment();
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_controller_test, null);
        
        mControllerListText = view.findViewById(R.id.tv_controller_list);
        mInputLogText = view.findViewById(R.id.tv_input_log);
        
        // Initialize controller handler for this dialog
        mControllerHandler = new ControllerInputHandler(this);
        
        // Update controller list
        updateControllerList();
        
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Controller Test")
                .setView(view)
                .setPositiveButton("Close", null)
                .create();
    }
    
    private void updateControllerList() {
        List<ControllerConfig.ControllerInfo> controllers = ControllerConfig.getConnectedControllers();
        
        if (controllers.isEmpty()) {
            mControllerListText.setText("No controllers detected.\n\nMake sure your controller is connected and try pressing a button.");
        } else {
            StringBuilder sb = new StringBuilder("Connected Controllers:\n\n");
            for (ControllerConfig.ControllerInfo controller : controllers) {
                sb.append("• ").append(controller.toString()).append("\n");
            }
            sb.append("\n").append(ControllerConfig.getButtonMappingDescription());
            mControllerListText.setText(sb.toString());
        }
    }
    
    @Override
    public void onControllerButtonPressed(int controllerId, int button, boolean pressed) {
        String buttonName = getButtonName(button);
        String action = pressed ? "PRESSED" : "RELEASED";
        String logEntry = String.format("Controller %d: %s %s\n", controllerId, buttonName, action);
        
        mInputLog.append(logEntry);
        
        // Keep only last 10 entries
        String[] lines = mInputLog.toString().split("\n");
        if (lines.length > 10) {
            mInputLog = new StringBuilder();
            for (int i = lines.length - 10; i < lines.length; i++) {
                mInputLog.append(lines[i]).append("\n");
            }
        }
        
        if (mInputLogText != null) {
            mInputLogText.setText("Input Log:\n\n" + mInputLog.toString());
        }
    }
    
    @Override
    public void onControllerAnalogInput(int controllerId, int axis, float value) {
        if (Math.abs(value) > 0.1f) { // Only log significant analog input
            String axisName = getAxisName(axis);
            String logEntry = String.format("Controller %d: %s %.2f\n", controllerId, axisName, value);
            
            mInputLog.append(logEntry);
            
            // Keep only last 10 entries
            String[] lines = mInputLog.toString().split("\n");
            if (lines.length > 10) {
                mInputLog = new StringBuilder();
                for (int i = lines.length - 10; i < lines.length; i++) {
                    mInputLog.append(lines[i]).append("\n");
                }
            }
            
            if (mInputLogText != null) {
                mInputLogText.setText("Input Log:\n\n" + mInputLog.toString());
            }
        }
    }
    
    private String getButtonName(int button) {
        switch (button) {
            case ControllerInputHandler.PAD_CROSS: return "Cross (X)";
            case ControllerInputHandler.PAD_CIRCLE: return "Circle (O)";
            case ControllerInputHandler.PAD_SQUARE: return "Square (□)";
            case ControllerInputHandler.PAD_TRIANGLE: return "Triangle (△)";
            case ControllerInputHandler.PAD_L1: return "L1";
            case ControllerInputHandler.PAD_R1: return "R1";
            case ControllerInputHandler.PAD_L2: return "L2";
            case ControllerInputHandler.PAD_R2: return "R2";
            case ControllerInputHandler.PAD_L3: return "L3";
            case ControllerInputHandler.PAD_R3: return "R3";
            case ControllerInputHandler.PAD_SELECT: return "Select";
            case ControllerInputHandler.PAD_START: return "Start";
            case ControllerInputHandler.PAD_UP: return "D-Pad Up";
            case ControllerInputHandler.PAD_DOWN: return "D-Pad Down";
            case ControllerInputHandler.PAD_LEFT: return "D-Pad Left";
            case ControllerInputHandler.PAD_RIGHT: return "D-Pad Right";
            default: return "Button " + button;
        }
    }
    
    private String getAxisName(int axis) {
        switch (axis) {
            case ControllerInputHandler.PAD_L_UP: return "Left Stick Up";
            case ControllerInputHandler.PAD_L_DOWN: return "Left Stick Down";
            case ControllerInputHandler.PAD_L_LEFT: return "Left Stick Left";
            case ControllerInputHandler.PAD_L_RIGHT: return "Left Stick Right";
            case ControllerInputHandler.PAD_R_UP: return "Right Stick Up";
            case ControllerInputHandler.PAD_R_DOWN: return "Right Stick Down";
            case ControllerInputHandler.PAD_R_LEFT: return "Right Stick Left";
            case ControllerInputHandler.PAD_R_RIGHT: return "Right Stick Right";
            default: return "Axis " + axis;
        }
    }
    
    @Override
    public void onControllerCombo(int controllerId, String comboName) {
        String logEntry = String.format("Controller %d: COMBO %s\n", controllerId, comboName);
        
        mInputLog.append(logEntry);
        
        // Keep only last 10 entries
        String[] lines = mInputLog.toString().split("\n");
        if (lines.length > 10) {
            mInputLog = new StringBuilder();
            for (int i = lines.length - 10; i < lines.length; i++) {
                mInputLog.append(lines[i]).append("\n");
            }
        }
        
        if (mInputLogText != null) {
            mInputLogText.setText("Input Log:\n\n" + mInputLog.toString());
        }
    }
}