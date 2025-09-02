package com.izzy2lost.psx2;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.net.Uri;
import android.view.InputDevice;
import android.hardware.input.InputManager;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.util.TypedValue;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.activity.OnBackPressedCallback;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import android.provider.OpenableColumns;
import org.json.JSONArray;
import org.json.JSONException;
import androidx.fragment.app.FragmentManager;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements GamesCoverDialogFragment.OnGameSelectedListener, ControllerInputHandler.ControllerInputListener {
    private String m_szGamefile = "";

    private HIDDeviceManager mHIDDeviceManager;
    private ControllerInputHandler mControllerInputHandler;
    private Thread mEmulationThread = null;
    private boolean mHudVisible = false;
    private InputManager mInputManager;
    
    // Track joystick directional pressed state to avoid duplicate down events
    private boolean joyUpPressed = false;
    private boolean joyDownPressed = false;
    private boolean joyLeftPressed = false;
    private boolean joyRightPressed = false;
    private boolean controllerUiApplied = false;
    private AlertDialog mBiosPromptDialog = null;

    private boolean isThread() {
        if (mEmulationThread != null) {
            Thread.State _thread_state = mEmulationThread.getState();
            return _thread_state == Thread.State.BLOCKED
                    || _thread_state == Thread.State.RUNNABLE
                    || _thread_state == Thread.State.TIMED_WAITING
                    || _thread_state == Thread.State.WAITING;
        }
        return false;
    }

    // Expose whether a game has been chosen (non-empty path)
    public boolean hasSelectedGame() {
        return !TextUtils.isEmpty(m_szGamefile);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Keep fullscreen on rotate and reflow constraints without recreating
        hideStatusBar();
        applyConstraintsForOrientation(newConfig.orientation);
    }

    private void applyConstraintsForOrientation(int orientation) {
        // Views present in both layouts
        View quick = findViewById(R.id.ll_quick_actions);
        View btnSettings = findViewById(R.id.btn_settings);
        View btnControls = findViewById(R.id.btn_toggle_controls);
        View llJoy = findViewById(R.id.ll_pad_joy);
        View llDpad = findViewById(R.id.ll_pad_dpad);
        View llRight = findViewById(R.id.ll_pad_right_buttons);
        View llSelectStart = findViewById(R.id.ll_pad_select_start);

        // Use helper dp()

        if (quick != null && btnSettings != null && btnControls != null) {
            ConstraintLayout.LayoutParams lp = safeCLP(quick);
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Between settings and controls, top-aligned
                lp.width = 0; // chain between start/end
                lp.topToTop = btnSettings.getId();
                lp.topToBottom = ConstraintLayout.LayoutParams.UNSET;
                lp.startToEnd = btnSettings.getId();
                lp.endToStart = btnControls.getId();
                lp.startToStart = ConstraintLayout.LayoutParams.UNSET;
                lp.endToEnd = ConstraintLayout.LayoutParams.UNSET;
                lp.topMargin = dp(0);
                lp.horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED;
            } else {
                // Centered under settings
                lp.width = ConstraintLayout.LayoutParams.WRAP_CONTENT;
                lp.topToTop = ConstraintLayout.LayoutParams.UNSET;
                lp.topToBottom = btnSettings.getId();
                lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.startToEnd = ConstraintLayout.LayoutParams.UNSET;
                lp.endToStart = ConstraintLayout.LayoutParams.UNSET;
                lp.topMargin = dp(16);
                lp.horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_SPREAD;
            }
            quick.setLayoutParams(lp);
        }

        if (llJoy != null) {
            ConstraintLayout.LayoutParams lp = safeCLP(llJoy);
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.endToEnd = ConstraintLayout.LayoutParams.UNSET;
            lp.topToTop = ConstraintLayout.LayoutParams.UNSET;
            lp.topToBottom = ConstraintLayout.LayoutParams.UNSET;
            int m = (orientation == Configuration.ORIENTATION_LANDSCAPE) ? 6 : 6;
            lp.setMargins(dp(m), dp(m), dp(m), dp(m));
            llJoy.setLayoutParams(lp);
            // Nudge joystick further left in both orientations to avoid Select overlap
            llJoy.setTranslationX(-dp(28));
        }

        if (llDpad != null && llJoy != null) {
            ConstraintLayout.LayoutParams lp = safeCLP(llDpad);
            // Above-left of joystick for both, with slight spacing
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.endToEnd = ConstraintLayout.LayoutParams.UNSET;
            lp.bottomToTop = llJoy.getId();
            lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
            lp.topToTop = ConstraintLayout.LayoutParams.UNSET;
            lp.topToBottom = ConstraintLayout.LayoutParams.UNSET;
            // Shift the whole D-pad slightly to the right with a small start margin
            lp.setMargins(dp(48), dp(0), dp(0), dp(orientation == Configuration.ORIENTATION_LANDSCAPE ? 2 : 0));
            llDpad.setLayoutParams(lp);
            // Nudge D-pad downward a little
            llDpad.setTranslationY(dp(14));
        }

        if (llRight != null) {
            ConstraintLayout.LayoutParams lp = safeCLP(llRight);
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
                lp.setMargins(dp(12), dp(12), dp(12), dp(12));
            } else {
                // Above Select/Start, aligned to end
                if (llSelectStart != null) {
                    lp.bottomToTop = llSelectStart.getId();
                } else {
                    lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                }
                lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.bottomToBottom = (llSelectStart == null) ? ConstraintLayout.LayoutParams.PARENT_ID : ConstraintLayout.LayoutParams.UNSET;
                lp.setMargins(0,0,0,dp(8));
            }
            llRight.setLayoutParams(lp);
        }

        if (llSelectStart != null) {
            ConstraintLayout.LayoutParams lp = safeCLP(llSelectStart);
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.setMargins(0,0,0,dp(orientation == Configuration.ORIENTATION_LANDSCAPE ? 8 : 0));
            llSelectStart.setLayoutParams(lp);
        }
    }

    private ConstraintLayout.LayoutParams safeCLP(View v) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof ConstraintLayout.LayoutParams) return (ConstraintLayout.LayoutParams) p;
        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(p);
        v.setLayoutParams(lp);
        return lp;
    }

    private int dp(int d) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, d, getResources().getDisplayMetrics());
    }

    private void hideStatusBar() {
        // Make immersive (hide status + navigation) and allow swipe to reveal temporarily
        Window w = getWindow();
        if (w == null) return;
        // Request full screen window flags for additional assurance
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Allow drawing into display cutouts (notches) on API 28+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Tell Window to lay out edge-to-edge and hide all system bars
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = w.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decor = w.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            decor.setSystemUiVisibility(flags);
        }
    }

    // Enable immersive mode to hide navigation and status bars
    private void enableImmersiveMode() {
        hideStatusBar();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideStatusBar();
    }

    private void pickGamesFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityResultGamesFolderPick.launch(intent);
    }

    private void showGamesListOrReselect(Uri treeUri) {
        // Re-scan quickly each time to keep list fresh
        String[] names;
        String[] uris;
        try {
            GameList list = scanGamesFromTreeUri(treeUri);
            names = list.names;
            uris = list.uris;
        } catch (Exception e) {
            names = new String[0];
            uris = new String[0];
        }
        // Make effectively-final copies for use in lambda
        final String[] namesFinal = names;
        final String[] urisFinal = uris;

        if (namesFinal.length == 0) {
            new MaterialAlertDialogBuilder(this,
                    com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                    .setTitle("GAMES")
                    .setMessage("No games found. Pick a folder?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Pick Folder", (d,w) -> pickGamesFolder())
                    .show();
            return;
        }
        // Show covers grid dialog fragment
        GamesCoverDialogFragment frag = GamesCoverDialogFragment.newInstance(namesFinal, urisFinal);
        FragmentManager fm = getSupportFragmentManager();
        frag.show(fm, "covers_dialog");
    }

    @Override
    public void onGameSelected(String gameUri) {
        if (!TextUtils.isEmpty(gameUri)) {
            // Avoid any pre-VM native calls here; just set the game and launch.
            m_szGamefile = gameUri;
            // Record last played timestamp for sorting
            try {
                SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                prefs.edit().putLong("last_played:" + gameUri, System.currentTimeMillis()).apply();
            } catch (Throwable ignored) {}
            restartEmuThread();
        }
    }

    private static final String[] GAME_EXTS = new String[]{
            ".iso", ".bin", ".img", ".mdf", ".nrg", ".chd"
    };

    private static boolean hasGameExt(String name) {
        if (TextUtils.isEmpty(name)) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : GAME_EXTS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private static class GameList {
        final String[] names;
        final String[] uris;
        GameList(String[] n, String[] u) { names = n; uris = u; }
    }

    private GameList scanGamesFromTreeUri(Uri treeUri) {
        DocumentFile dir = DocumentFile.fromTreeUri(this, treeUri);
        if (dir == null || !dir.isDirectory()) return new GameList(new String[0], new String[0]);
        java.util.ArrayList<String> nameList = new java.util.ArrayList<>();
        java.util.ArrayList<String> uriList = new java.util.ArrayList<>();
        scanGamesRecursive(dir, nameList, uriList);

        // Persist folder and latest list
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        for (int i = 0; i < nameList.size(); i++) {
            JSONArray pair = new JSONArray();
            try {
                pair.put(nameList.get(i));
                pair.put(uriList.get(i));
            } catch (Exception ignored) {}
            arr.put(pair);
        }
        prefs.edit()
                .putString("games_folder_uri", treeUri.toString())
                .putString("games_list_json", arr.toString())
                .apply();

        return new GameList(nameList.toArray(new String[0]), uriList.toArray(new String[0]));
    }

    private void scanGamesRecursive(DocumentFile dir, java.util.List<String> names, java.util.List<String> uris) {
        DocumentFile[] children = dir.listFiles();
        if (children == null) return;
        for (DocumentFile child : children) {
            if (child == null) continue;
            if (child.isDirectory()) {
                scanGamesRecursive(child, names, uris);
            } else if (child.isFile()) {
                String name = child.getName();
                if (hasGameExt(name)) {
                    names.add(name);
                    uris.add(child.getUri().toString());
                }
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide action bar to improve immersive appearance
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setContentView(R.layout.activity_main);
        enableImmersiveMode();

        // Setup back button handler
        setupBackPressedHandler();

        // Default resources
        copyAssetAll(getApplicationContext(), "resources");

        Initialize();

        // Initialize controller input handler
        mControllerInputHandler = new ControllerInputHandler(this);
        
        // Log connected controllers for debugging
        ControllerConfig.logControllerInfo(this);

        makeButtonTouch();
        updateRendererButtonLabel();

        setSurfaceView(new SDLSurface(this));

        // Ensure consistent ripple across all MaterialButtons
        tintAllMaterialButtonOutlines();

        // Apply orientation-specific constraints once at startup
        int currentOrientation = getResources().getConfiguration().orientation;
        applyConstraintsForOrientation(currentOrientation);
      
        // Prompt for BIOS if missing
        maybePromptForBios();

        // Listen for controller attach/detach and update UI accordingly
        mInputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        if (mInputManager != null) {
            mInputManager.registerInputDeviceListener(mInputDeviceListener, null);
        }
        updateUiForControllerPresence();
    }

    // Public method to open the games covers dialog via controller quick actions
    public void openGamesDialog() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String folderUri = prefs.getString("games_folder_uri", null);
        if (TextUtils.isEmpty(folderUri)) {
            pickGamesFolder();
        } else {
            showGamesListOrReselect(Uri.parse(folderUri));
        }
    }

    private void setupBackPressedHandler() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitDialog();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void makeButtonTouch() {
        MaterialButton btn_file = findViewById(R.id.btn_file);
        if(btn_file != null) {
            // Tap: show games list (from persisted folder). If none, prompt to pick folder.
            btn_file.setOnClickListener(v -> {
                SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                String folderUri = prefs.getString("games_folder_uri", null);
                if (TextUtils.isEmpty(folderUri)) {
                    pickGamesFolder();
                    return;
                }
                showGamesListOrReselect(Uri.parse(folderUri));
            });
            // Long-press: reselect games folder
            btn_file.setOnLongClickListener(v -> {
                pickGamesFolder();
                return true;
            });
        }

        // Combined saves dialog
        MaterialButton btn_saves = findViewById(R.id.btn_saves);
        if(btn_saves != null) {
            btn_saves.setOnClickListener(v -> {
                SavesDialogFragment dialog = new SavesDialogFragment();
                dialog.show(getSupportFragmentManager(), "saves_dialog");
            });
        }

        // BIOS button repurposed: short tap toggles renderer, long-press picks BIOS folder
        MaterialButton btn_bios = findViewById(R.id.btn_bios);
        if (btn_bios != null) {
            btn_bios.setOnClickListener(v -> {
                int current = getCurrentRendererPref();
                int next;
                // Cycle: AUTO(-1) -> VK(14) -> OGL(12) -> SW(13) -> AUTO
                if (current == -1) next = 14;
                else if (current == 14) next = 12;
                else if (current == 12) next = 13;
                else next = -1;
                setRendererAndSave(next);
            });
            btn_bios.setOnLongClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                startActivityResultBiosFolderPick.launch(intent);
                return true;
            });
        }

        // Settings button opens dialog
        MaterialButton btn_settings = findViewById(R.id.btn_settings);
        if (btn_settings != null) {
            btn_settings.setOnClickListener(v -> {
                FragmentManager fm = getSupportFragmentManager();
                SettingsDialogFragment dialog = new SettingsDialogFragment();
                dialog.show(fm, "settings_dialog");
            });
        }

        // Toggle all UI visibility (including controls)
        MaterialButton btnToggleControls = findViewById(R.id.btn_toggle_controls);
        if (btnToggleControls != null) {
            btnToggleControls.setOnClickListener(v -> {
                toggleAllUIVisibility();
            });
        }

        // PAD
        MaterialButton btn_pad_select = findViewById(R.id.btn_pad_select);
        if(btn_pad_select != null) {
            btn_pad_select.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_SELECT);
                return true;
            });
        }
        MaterialButton btn_pad_start = findViewById(R.id.btn_pad_start);
        if(btn_pad_start != null) {
            btn_pad_start.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_START);
                return true;
            });
        }
         MaterialButton btn_pad_a = findViewById(R.id.btn_pad_a);
        if(btn_pad_a != null) {
            btn_pad_a.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_A);
                return true;
            });
        }
        MaterialButton btn_pad_b = findViewById(R.id.btn_pad_b);
        if(btn_pad_b != null) {
            btn_pad_b.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_B);
                return true;
            });
        }
        MaterialButton btn_pad_x = findViewById(R.id.btn_pad_x);
        if(btn_pad_x != null) {
            btn_pad_x.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_X);
                return true;
            });
        }
        MaterialButton btn_pad_y = findViewById(R.id.btn_pad_y);
        if(btn_pad_y != null) {
            btn_pad_y.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_Y);
                return true;
            });
        }

        //// Shoulder buttons
        MaterialButton btn_pad_l1 = findViewById(R.id.btn_pad_l1);
        if(btn_pad_l1 != null) {
            btn_pad_l1.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_L1);
                return true;
            });
        }
        MaterialButton btn_pad_r1 = findViewById(R.id.btn_pad_r1);
        if(btn_pad_r1 != null) {
            btn_pad_r1.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_R1);
                return true;
            });
        }

        MaterialButton btn_pad_l2 = findViewById(R.id.btn_pad_l2);
        if(btn_pad_l2 != null) {
            btn_pad_l2.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_L2);
                return true;
            });
        }
        MaterialButton btn_pad_r2 = findViewById(R.id.btn_pad_r2);
        if(btn_pad_r2 != null) {
            btn_pad_r2.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_R2);
                return true;
            });
        }

        MaterialButton btn_pad_l3 = findViewById(R.id.btn_pad_l3);
        if(btn_pad_l3 != null) {
            btn_pad_l3.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_THUMBL);
                return true;
            });
        }
        MaterialButton btn_pad_r3 = findViewById(R.id.btn_pad_r3);
        if(btn_pad_r3 != null) {
            btn_pad_r3.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_BUTTON_THUMBR);
                return true;
            });
        }

        //// D-Pad
        final int PAD_L_UP = 110;
        final int PAD_L_RIGHT = 111;
        final int PAD_L_DOWN = 112;
        final int PAD_L_LEFT = 113;

        final int PAD_R_UP = 120;
        final int PAD_R_RIGHT = 121;
        final int PAD_R_DOWN = 122;
        final int PAD_R_LEFT = 123;

        // Draggable JoystickView (portrait/landscape layouts)
        View joystick = findViewById(R.id.joystick_view);
        if (joystick instanceof JoystickView) {
            JoystickView jv = (JoystickView) joystick;
            jv.setOnMoveListener((nx, ny, action) -> {
                // Thresholds
                final float T = 0.3f;
                boolean up = ny < -T;
                boolean down = ny > T;
                boolean left = nx < -T;
                boolean right = nx > T;

                // Issue key down/up transitions only when state changes
                if (up != joyUpPressed) {
                    sendKeyAction(jv, up ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP, PAD_L_UP);
                    joyUpPressed = up;
                }
                if (down != joyDownPressed) {
                    sendKeyAction(jv, down ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP, PAD_L_DOWN);
                    joyDownPressed = down;
                }
                if (left != joyLeftPressed) {
                    sendKeyAction(jv, left ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP, PAD_L_LEFT);
                    joyLeftPressed = left;
                }
                if (right != joyRightPressed) {
                    sendKeyAction(jv, right ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP, PAD_L_RIGHT);
                    joyRightPressed = right;
                }

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    // Ensure all released
                    if (joyUpPressed)  sendKeyAction(jv, MotionEvent.ACTION_UP, PAD_L_UP);
                    if (joyDownPressed) sendKeyAction(jv, MotionEvent.ACTION_UP, PAD_L_DOWN);
                    if (joyLeftPressed) sendKeyAction(jv, MotionEvent.ACTION_UP, PAD_L_LEFT);
                    if (joyRightPressed) sendKeyAction(jv, MotionEvent.ACTION_UP, PAD_L_RIGHT);
                    joyUpPressed = joyDownPressed = joyLeftPressed = joyRightPressed = false;
                }
            });
        }

        //// D-Pad buttons
        MaterialButton btn_pad_dir_top = findViewById(R.id.btn_pad_dir_top);
        if(btn_pad_dir_top != null) {
            btn_pad_dir_top.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_DPAD_UP);
                return true;
            });
        }
        MaterialButton btn_pad_dir_bottom = findViewById(R.id.btn_pad_dir_bottom);
        if(btn_pad_dir_bottom != null) {
            btn_pad_dir_bottom.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_DPAD_DOWN);
                return true;
            });
        }
        MaterialButton btn_pad_dir_left = findViewById(R.id.btn_pad_dir_left);
        if(btn_pad_dir_left != null) {
            btn_pad_dir_left.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_DPAD_LEFT);
                return true;
            });
        }
        MaterialButton btn_pad_dir_right = findViewById(R.id.btn_pad_dir_right);
        if(btn_pad_dir_right != null) {
            btn_pad_dir_right.setOnTouchListener((v, event) -> {
                sendKeyAction(v, event.getAction(), KeyEvent.KEYCODE_DPAD_RIGHT);
                return true;
            });
        }
    }

    private boolean allUIHidden = false;

    private void setControlsVisible(boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        View llDpad = findViewById(R.id.ll_pad_dpad);
        View llRight = findViewById(R.id.ll_pad_right_buttons);
        View llSelectStart = findViewById(R.id.ll_pad_select_start);
        View llJoy = findViewById(R.id.ll_pad_joy);

        if (llDpad != null) llDpad.setVisibility(vis);
        if (llRight != null) llRight.setVisibility(vis);
        if (llSelectStart != null) llSelectStart.setVisibility(vis);
        if (llJoy != null) llJoy.setVisibility(vis);
    }

    private void toggleAllUIVisibility() {
        allUIHidden = !allUIHidden;
        int vis = allUIHidden ? View.GONE : View.VISIBLE;
        boolean hasController = isAnyControllerConnected();
        
        // Hide/show all UI elements except the toggle button itself
        View btnSettings = findViewById(R.id.btn_settings);
        MaterialButton btnToggleControls = findViewById(R.id.btn_toggle_controls);
        View btnFile = findViewById(R.id.btn_file);
        View btnBios = findViewById(R.id.btn_bios);
        View btnSaves = findViewById(R.id.btn_saves);
        View llQuickActions = findViewById(R.id.ll_quick_actions);
        
        if (btnSettings != null) btnSettings.setVisibility(vis);
        // Keep toggle button visible but change its icon
        if (btnToggleControls != null) {
            // Change icon based on visibility state
            int iconRes = allUIHidden ? R.drawable.visibility_off_24px : R.drawable.visibility_24px;
            btnToggleControls.setIcon(ContextCompat.getDrawable(this, iconRes));
        }
        if (btnFile != null) btnFile.setVisibility(vis);
        if (btnBios != null) btnBios.setVisibility(vis);
        if (btnSaves != null) btnSaves.setVisibility(vis);
        if (llQuickActions != null) llQuickActions.setVisibility(vis);

        // Hide/show on-screen controls
        if (!allUIHidden) {
            // When showing with a controller connected, keep touch controls hidden
            setControlsVisible(!hasController);
        } else {
            // When hiding all UI, also hide controls
            setControlsVisible(false);
        }

        // Update renderer label when UI toggled back on
        if (!allUIHidden) updateRendererButtonLabel();
    }

    // --- Controller presence handling ---
    private final InputManager.InputDeviceListener mInputDeviceListener = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int deviceId) { updateUiForControllerPresence(); }
        @Override public void onInputDeviceRemoved(int deviceId) { updateUiForControllerPresence(); }
        @Override public void onInputDeviceChanged(int deviceId) { updateUiForControllerPresence(); }
    };

    private boolean isControllerDevice(InputDevice device) {
        if (device == null) return false;

        // Filter out virtual/built-in devices which can falsely report DPAD sources
        try {
            if (device.isVirtual()) return false;
        } catch (Throwable ignored) {}

        // Heuristic: devices with both vendor and product = 0 are often virtual
        try {
            if (device.getVendorId() == 0 && device.getProductId() == 0) return false;
        } catch (Throwable ignored) {}

        // Filter by name for common virtual/built-in inputs
        String name = device.getName();
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("virtual") || lower.contains("uinput") || lower.contains("touch")
                    || lower.contains("keyboard") || lower.contains("keypad") || lower.contains("gpio")) {
                return false;
            }
        }

        int sources = device.getSources();
        boolean gamepad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        boolean joystick = (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        boolean dpad = (sources & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;

        // Consider DPAD-only devices as controllers only if they are external (heuristic above)
        return gamepad || joystick || dpad;
    }

    private boolean isAnyControllerConnected() {
        try {
            int[] ids = InputDevice.getDeviceIds();
            for (int id : ids) {
                InputDevice dev = InputDevice.getDevice(id);
                if (isControllerDevice(dev)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void updateUiForControllerPresence() {
        boolean hasController = isAnyControllerConnected();
        // Show all UI when no controller; hide UI when a controller is connected.
        int vis = hasController ? View.GONE : View.VISIBLE;

        View btnSettings = findViewById(R.id.btn_settings);
        View btnFile = findViewById(R.id.btn_file);
        View btnBios = findViewById(R.id.btn_bios);
        View btnSaves = findViewById(R.id.btn_saves);
        MaterialButton btnToggle = findViewById(R.id.btn_toggle_controls);
        View llQuickActions = findViewById(R.id.ll_quick_actions);

        if (btnSettings != null) btnSettings.setVisibility(vis);
        if (btnFile != null) btnFile.setVisibility(vis);
        if (btnBios != null) btnBios.setVisibility(vis);
        if (btnSaves != null) btnSaves.setVisibility(vis);
        if (llQuickActions != null) llQuickActions.setVisibility(vis);
        if (btnToggle != null) btnToggle.setVisibility(View.VISIBLE); // always visible
        // Set toggle icon and internal state
        if (btnToggle != null) {
            int iconRes = hasController ? R.drawable.visibility_off_24px : R.drawable.visibility_24px;
            btnToggle.setIcon(ContextCompat.getDrawable(this, iconRes));
        }
        allUIHidden = hasController;

        // Hide on-screen touch controls when a physical controller is connected
        setControlsVisible(!hasController);
    }

    private int getCurrentRendererPref() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        // Default to -1 (Automatic) if not set
        return prefs.getInt("renderer", -1);
    }

    private String rendererShortLabel(int r) {
        if (r == -1) return "AUTO";
        if (r == 12) return "OGL";
        if (r == 13) return "SW";
        return "VK"; // 14
    }

    private void updateRendererButtonLabel() {
        MaterialButton btn_bios = findViewById(R.id.btn_bios);
        if (btn_bios != null) {
            int current = getCurrentRendererPref();
            try {
                // Prefer runtime renderer from core to reflect per-game overrides
                int runtime = NativeApp.getCurrentRenderer();
                // Only accept expected values
                if (runtime == -1 || runtime == 12 || runtime == 13 || runtime == 14) {
                    current = runtime;
                }
            } catch (Throwable ignored) {}
            btn_bios.setText(rendererShortLabel(current));
        }
    }

    // Public minimal UI refresh hook for dialogs
    public void refreshQuickUi() {
        updateRendererButtonLabel();
    }

    private void setRendererAndSave(int renderer) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        prefs.edit().putInt("renderer", renderer).apply();
        // Apply live if possible; this path has proven stable via top bar buttons
        try {
            NativeApp.renderGpu(renderer);
        } catch (Throwable t) {
            android.util.Log.e("MainActivity", "Renderer switch failed: " + t.getMessage());
        }
        updateRendererButtonLabel();
    }

    private void tintAllMaterialButtonOutlines() {
        // Ripple + specific color tweaks, no strokes (transparent container style)
        View root = findViewById(android.R.id.content);
        if (root instanceof ViewGroup) {
            traverseAndTintButtons((ViewGroup) root);
        }
    }

    private void traverseAndTintButtons(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup) {
                traverseAndTintButtons((ViewGroup) child);
            }
            if (child instanceof MaterialButton) {
                MaterialButton mb = (MaterialButton) child;
                // Ensure ripple matches brand globally
                mb.setRippleColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_ripple)));
                int id = mb.getId();
                if (id == R.id.btn_pad_y) { // Triangle = green
                    final int base = ContextCompat.getColor(this, R.color.ps2_triangle_green);
                    ColorStateList stateful = pressedColorStateList(base);
                    mb.setTextColor(stateful);
                } else if (id == R.id.btn_pad_b) { // Circle = red
                    final int base = ContextCompat.getColor(this, R.color.ps2_circle_red);
                    ColorStateList stateful = pressedColorStateList(base);
                    mb.setTextColor(stateful);
                } else if (id == R.id.btn_pad_a) { // Cross = blue
                    final int base = ContextCompat.getColor(this, R.color.ps2_cross_blue);
                    ColorStateList stateful = pressedColorStateList(base);
                    mb.setTextColor(stateful);
                } else if (id == R.id.btn_pad_x) { // Square = pink
                    final int base = ContextCompat.getColor(this, R.color.ps2_square_pink);
                    ColorStateList stateful = pressedColorStateList(base);
                    mb.setTextColor(stateful);
                } else if (id == R.id.btn_pad_dir_top || id == R.id.btn_pad_dir_left || id == R.id.btn_pad_dir_right || id == R.id.btn_pad_dir_bottom) {
                    // D-pad arrow icon tint to brand accent
                    ColorStateList iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_accent));
                    mb.setIconTint(iconTint);
                }
                // No stroke; background is transparent per style
            }
        }
    }

    // --- BIOS presence check and prompt ---
    private boolean hasAnyBiosFiles(File dir) {
        if (dir == null || !dir.exists()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f == null) continue;
            if (f.isDirectory()) {
                if (hasAnyBiosFiles(f)) return true;
            } else {
                String name = f.getName();
                // Common PCSX2 BIOS components: SCPH*.bin (main), and component ROMs ROM0/ROM1/ROM2/EROM
                if (name != null) {
                    String lower = name.toLowerCase(Locale.ROOT);
                    boolean isMainBios = lower.startsWith("scph") && (lower.endsWith(".bin") || lower.endsWith(".rom"));
                    boolean isComponentSuffix = lower.endsWith(".rom0") || lower.endsWith(".rom1") || lower.endsWith(".rom2") || lower.endsWith(".erom");
                    boolean isBareComponent = lower.equals("rom0") || lower.equals("rom1") || lower.equals("rom2") || lower.equals("erom");

                    if (isMainBios) {
                        if (f.length() >= 256 * 1024) return true; // main BIOS typically >= 2MB, but allow 256KB+
                    } else if (isComponentSuffix || isBareComponent) {
                        if (f.length() >= 64 * 1024) return true; // component ROMs can be smaller
                    } else if (lower.endsWith(".bin") || lower.endsWith(".rom")) {
                        // Fallback: any .bin/.rom over 256KB
                        if (f.length() >= 256 * 1024) return true;
                    }
                }
            }
        }
        return false;
    }

    private void maybePromptForBios() {
        File biosDir = new File(getApplicationContext().getExternalFilesDir(null), "bios");
        if (hasAnyBiosFiles(biosDir)) return;
        showBiosPrompt();
    }

    private boolean ensureBiosOrPrompt() {
        File biosDir = new File(getApplicationContext().getExternalFilesDir(null), "bios");
        if (hasAnyBiosFiles(biosDir)) return true;
        showBiosPrompt();
        return false;
    }

    private void showBiosPrompt() {
        if (mBiosPromptDialog != null && mBiosPromptDialog.isShowing()) return;
        mBiosPromptDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("BIOS Required")
                .setMessage("No PS2 BIOS detected. Import your BIOS files to run games.\n\nHint: Press Select+Start for Quick Actions.")
                .setNegativeButton("Later", (d, w) -> { /* leave dialog dismiss */ })
                .setPositiveButton("Pick Files", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.setType("*/*");
                    startActivityResultBiosPick.launch(intent);
                })
                .setNeutralButton("Pick Folder", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                    startActivityResultBiosFolderPick.launch(intent);
                })
                .create();
        mBiosPromptDialog.setOnDismissListener(dialog -> mBiosPromptDialog = null);
        mBiosPromptDialog.show();
    }

    private void dismissBiosPromptIfResolved() {
        File biosDir = new File(getApplicationContext().getExternalFilesDir(null), "bios");
        if (hasAnyBiosFiles(biosDir)) {
            if (mBiosPromptDialog != null && mBiosPromptDialog.isShowing()) {
                try { mBiosPromptDialog.dismiss(); } catch (Exception ignored) {}
            }
        }
    }

    private ColorStateList pressedColorStateList(int base) {
        int pressed = darkenColor(base, 0.85f); // 15% darker when pressed
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        };
        int[] colors = new int[]{
                pressed,
                base
        };
        return new ColorStateList(states, colors);
    }

    private int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.max(0, Math.min(255, (int)(r * factor)));
        g = Math.max(0, Math.min(255, (int)(g * factor)));
        b = Math.max(0, Math.min(255, (int)(b * factor)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void applySavedSettings() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        // Renderer constants (match SettingsDialogFragment)
        final int RENDERER_OPENGL = 12;
        final int RENDERER_SOFTWARE = 13;
        final int RENDERER_VULKAN = 14;
        
        int renderer = prefs.getInt("renderer", RENDERER_VULKAN);
        NativeApp.renderGpu(renderer);

        // Resolution scale multiplier (float), default 1.0
        float scale = prefs.getFloat("upscale_multiplier", 1.0f);
        NativeApp.renderUpscalemultiplier(scale);

        // Aspect ratio: 0=Stretch, 1=Auto 4:3/3:2, 2=4:3, 3=16:9, 4=10:7
        int aspectRatio = prefs.getInt("aspect_ratio", 1); // Default to Auto 4:3/3:2
        NativeApp.setAspectRatio(aspectRatio);

        // Widescreen patches
        boolean widescreenPatches = prefs.getBoolean("widescreen_patches", true);
        NativeApp.setWidescreenPatches(widescreenPatches);

        // No interlacing patches
        boolean noInterlacingPatches = prefs.getBoolean("no_interlacing_patches", true);
        NativeApp.setNoInterlacingPatches(noInterlacingPatches);

        // Texture loading options
        boolean loadTextures = prefs.getBoolean("load_textures", false);
        NativeApp.setLoadTextures(loadTextures);
        
        boolean asyncTextureLoading = prefs.getBoolean("async_texture_loading", true);
        NativeApp.setAsyncTextureLoading(asyncTextureLoading);

        // HUD visibility
        boolean hudVisible = prefs.getBoolean("hud_visible", false);
        NativeApp.setHudVisible(hudVisible);
        
        // Set brighter default brightness (60 instead of 50)
        NativeApp.setShadeBoost(true);
        NativeApp.setShadeBoostBrightness(60);
        NativeApp.setShadeBoostContrast(50);
        NativeApp.setShadeBoostSaturation(50);
    }

    public final ActivityResultLauncher<Intent> startActivityResultLocalFilePlay = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if(result.getResultCode() == Activity.RESULT_OK) {
                    try {
                        Intent _intent = result.getData();
                        if(_intent != null) {
                            m_szGamefile = _intent.getDataString();
                            if(!TextUtils.isEmpty(m_szGamefile)) {
                                try {
                                    SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                                    prefs.edit().putLong("last_played:" + m_szGamefile, System.currentTimeMillis()).apply();
                                } catch (Throwable ignored) {}
                                restartEmuThread();
                            }
                        }
                    }
                    catch (Exception ignored) {}
                }
            }
        );

    public final ActivityResultLauncher<Intent> startActivityResultBiosPick = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    try {
                        Intent data = result.getData();
                        if (data != null) {
                            File biosDir = new File(getApplicationContext().getExternalFilesDir(null), "bios");
                            if (!biosDir.exists()) biosDir.mkdirs();

                            ClipData clipData = data.getClipData();
                            if (clipData != null && clipData.getItemCount() > 0) {
                                for (int i = 0; i < clipData.getItemCount(); i++) {
                                    Uri uri = clipData.getItemAt(i).getUri();
                                    importSingleBiosUri(uri, biosDir);
                                }
                            } else {
                                Uri uri = data.getData();
                                if (uri != null) {
                                    importSingleBiosUri(uri, biosDir);
                                }
                            }
                            // If BIOS now present, dismiss the prompt
                            dismissBiosPromptIfResolved();
                        }
                    } catch (Exception ignored) {}
                }
            });

    public final ActivityResultLauncher<Intent> startActivityResultBiosFolderPick = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    try {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri treeUri = data.getData();
                            if (treeUri != null) {
                                // Persist read permission for future imports, optional
                                final int takeFlags = (data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                                if ((takeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                                    getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                }
                                if ((takeFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                                    getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                }

                                DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
                                if (pickedDir != null && pickedDir.isDirectory()) {
                                    File biosDir = new File(getApplicationContext().getExternalFilesDir(null), "bios");
                                    if (!biosDir.exists()) biosDir.mkdirs();
                                    copyDocumentTreeToDirectory(pickedDir, biosDir);
                                    // If BIOS now present, dismiss the prompt
                                    dismissBiosPromptIfResolved();
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            });

    public final ActivityResultLauncher<Intent> startActivityResultGamesFolderPick = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    try {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri treeUri = data.getData();
                            if (treeUri != null) {
                                final int takeFlags = (data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                                if ((takeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                                    try {
                                        getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    } catch (SecurityException ignored) {}
                                }
                                if ((takeFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                                    try {
                                        getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                    } catch (SecurityException ignored) {}
                                }
                                // Save folder and show games
                                getSharedPreferences("app_prefs", MODE_PRIVATE)
                                        .edit()
                                        .putString("games_folder_uri", treeUri.toString())
                                        .apply();
                                showGamesListOrReselect(treeUri);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            });

    private void importSingleBiosUri(Uri uri, File biosDir) {
        if (uri == null) return;
        String displayName = getDisplayNameFromUri(this, uri);
        if (TextUtils.isEmpty(displayName)) displayName = "bios.bin";
        File outFile = new File(biosDir, displayName);
        copyUriToFile(this, uri, outFile);
    }

    private void copyDocumentTreeToDirectory(DocumentFile dir, File outDir) {
        if (dir == null || !dir.isDirectory()) return;
        DocumentFile[] children = dir.listFiles();
        if (children == null) return;
        for (DocumentFile child : children) {
            if (child == null) continue;
            if (child.isDirectory()) {
                File sub = new File(outDir, child.getName() != null ? child.getName() : "folder");
                if (!sub.exists()) sub.mkdirs();
                copyDocumentTreeToDirectory(child, sub);
            } else if (child.isFile()) {
                String name = child.getName();
                if (TextUtils.isEmpty(name)) name = "bios.bin";
                File dest = new File(outDir, name);
                copyUriToFile(this, child.getUri(), dest);
            }
        }
    }

    private static String getDisplayNameFromUri(Context context, Uri uri) {
        String name = null;
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex);
                }
            } finally {
                cursor.close();
            }
        }
        if (TextUtils.isEmpty(name)) {
            name = uri.getLastPathSegment();
        }
        return name;
    }

    private static boolean copyUriToFile(Context context, Uri uri, File destFile) {
        InputStream is = null;
        FileOutputStream os = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            if (is == null) return false;
            os = new FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { if (os != null) os.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onPause() {
        NativeApp.pause();
        super.onPause();
        ////
        if (mHIDDeviceManager != null) {
            mHIDDeviceManager.setFrozen(true);
        }
    }

    @Override
    protected void onResume() {
        NativeApp.resume();
        super.onResume();
        ////
        if (mHIDDeviceManager != null) {
            mHIDDeviceManager.setFrozen(false);
        }
        // Re-assert full screen when returning to the activity
        enableImmersiveMode();
        updateUiForControllerPresence();
    }

    @Override
    protected void onDestroy() {
        NativeApp.shutdown();
        super.onDestroy();
        ////
        if (mHIDDeviceManager != null) {
            HIDDeviceManager.release(mHIDDeviceManager);
            mHIDDeviceManager = null;
        }
        if (mInputManager != null) {
            try { mInputManager.unregisterInputDeviceListener(mInputDeviceListener); } catch (Exception ignored) {}
            mInputManager = null;
        }
        ////
        if (mEmulationThread != null) {
            try {
                mEmulationThread.join();
                mEmulationThread = null;
            }
            catch (InterruptedException ignored) {}
        }

        int appPid = android.os.Process.myPid();
        android.os.Process.killProcess(appPid);
    }

    public void Initialize() {
        NativeApp.initializeOnce(getApplicationContext());

        // Set up JNI
        SDLControllerManager.nativeSetupJNI();

        // Initialize state
        SDLControllerManager.initialize();

        // Load and apply saved settings
        SettingsDialogFragment.loadAndApplySettings(this);

        mHIDDeviceManager = HIDDeviceManager.acquire(this);
        // Initialize HID device manager for USB and Bluetooth controllers
        mHIDDeviceManager.initialize(true, true);
        
    }

    private void setSurfaceView(Object p_value) {
        FrameLayout fl_board = findViewById(R.id.fl_board);
        if(fl_board != null) {
            if(fl_board.getChildCount() > 0) {
                fl_board.removeAllViews();
            }
            ////
            if(p_value instanceof SDLSurface) {
                fl_board.addView((SDLSurface)p_value);
            }
        }
    }

    public void startEmuThread() {
        // Ensure BIOS present before starting emulation
        if (!ensureBiosOrPrompt()) return;
        if(!isThread()) {
            mEmulationThread = new Thread(() -> NativeApp.runVMThread(m_szGamefile));
            mEmulationThread.start();
        }
    }

    private void restartEmuThread() {
        // Ensure BIOS present before starting/restarting emulation
        if (!ensureBiosOrPrompt()) return;
        NativeApp.shutdown();
        if (mEmulationThread != null) {
            try {
                mEmulationThread.join();
                mEmulationThread = null;
            }
            catch (InterruptedException ignored) {}
        }
        
        // Apply global renderer setting before starting new game
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int renderer = prefs.getInt("renderer", 14); // Default to Vulkan if no setting
        android.util.Log.d("MainActivity", "Applying global renderer before game restart: " + renderer);
        NativeApp.renderGpu(renderer);
        
        ////
        startEmuThread();
    }

    // Public API for UI components to reboot the emulator
    public void rebootEmu() {
        if (!TextUtils.isEmpty(m_szGamefile)) {
            restartEmuThread();
        } else {
            // No game loaded; just shutdown for safety
            NativeApp.shutdown();
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // Use only our controller handler - disable SDL fallback to avoid conflicts
        if (mControllerInputHandler != null && mControllerInputHandler.handleMotionEvent(event)) {
            return true;
        }
        
        // Skip SDL controller manager to avoid mapping conflicts
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Allow dialogs to receive DPAD navigation when visible
        if (!isAnyAppDialogShowing()) {
            // Intercept controller DPAD/gamepad keys before focused views consume them
            if (mControllerInputHandler != null && mControllerInputHandler.handleKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isAnyAppDialogShowing() {
        try {
            java.util.List<androidx.fragment.app.Fragment> frags = getSupportFragmentManager().getFragments();
            for (androidx.fragment.app.Fragment f : frags) {
                if (f instanceof androidx.fragment.app.DialogFragment) {
                    android.app.Dialog d = ((androidx.fragment.app.DialogFragment) f).getDialog();
                    if (d != null && d.isShowing()) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public boolean onKeyDown(int p_keyCode, KeyEvent p_event) {
        // Use only our controller handler - disable SDL fallback to avoid conflicts
        if (mControllerInputHandler != null && mControllerInputHandler.handleKeyEvent(p_event)) {
            return true;
        }
        
        // Handle back button for non-gamepad sources
        if (p_keyCode == KeyEvent.KEYCODE_BACK) {
            showExitDialog();
            return true;
        }
        
        // Skip SDL controller manager to avoid mapping conflicts
        return super.onKeyDown(p_keyCode, p_event);
    }

    @Override
    public boolean onKeyUp(int p_keyCode, KeyEvent p_event) {
        // Use only our controller handler - disable SDL fallback to avoid conflicts
        if (mControllerInputHandler != null && mControllerInputHandler.handleKeyEvent(p_event)) {
            return true;
        }
        
        // Skip SDL controller manager to avoid mapping conflicts
        return super.onKeyUp(p_keyCode, p_event);
    }

    public static void sendKeyAction(View p_view, int p_action, int p_keycode) {
        if(p_action == MotionEvent.ACTION_DOWN) {
            p_view.setPressed(true);
            int pad_force = 0;
            if(p_keycode >= 110) {
                float _abs = 90; // Joystic test value
                _abs = Math.min(_abs, 100);
                pad_force = (int) (_abs * 32766.0f / 100);
            }
            NativeApp.setPadButton(p_keycode, pad_force, true);
        } else if(p_action == MotionEvent.ACTION_UP || p_action == MotionEvent.ACTION_CANCEL) {
            p_view.setPressed(false);
            NativeApp.setPadButton(p_keycode, 0, false);
        }
    }

    // Asset copy helpers used on first launch to seed default resources
    private void copyAssetAll(Context context, String srcPath) {
        AssetManager assetMgr = context.getAssets();
        try {
            String[] assets = assetMgr.list(srcPath);
            String destPath = context.getExternalFilesDir(null) + File.separator + srcPath;
            if (assets != null) {
                if (assets.length == 0) {
                    copyFile(context, srcPath, destPath);
                } else {
                    File dir = new File(destPath);
                    if (!dir.exists()) dir.mkdirs();
                    for (String element : assets) {
                        copyAssetAll(context, srcPath + File.separator + element);
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private static void copyFile(Context context, String srcFile, String destFile) {
        AssetManager assetMgr = context.getAssets();
        InputStream is = null;
        FileOutputStream os = null;
        try {
            is = assetMgr.open(srcFile);
            boolean exists = new File(destFile).exists();
            if (srcFile.contains("shaders")) {
                exists = false; // always refresh shaders
            }
            if (!exists) {
                File parent = new File(destFile).getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                os = new FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
        } catch (IOException ignored) {
        } finally {
            try { if (is != null) is.close(); } catch (IOException ignored) {}
            try { if (os != null) os.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public void onBackPressed() {
        // Fallback for older Android versions
        super.onBackPressed();
        showExitDialog();
    }

    private void showExitDialog() {
        new MaterialAlertDialogBuilder(this,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Exit App")
                .setMessage("Do you want to exit PSX2?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Exit", (dialog, which) -> {
                    // Stop emulator first
                    NativeApp.shutdown();
                    // Quit the app
                    finishAffinity();
                    finishAndRemoveTask();
                    // As a fallback ensure process exit
                    System.exit(0);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ControllerInputHandler.ControllerInputListener implementation
    @Override
    public void onControllerButtonPressed(int controllerId, int button, boolean pressed) {
        android.util.Log.d("Controller", "Controller " + controllerId + " button " + button + " (" + getButtonName(button) + ") " + (pressed ? "pressed" : "released"));

        // Send controller input to native code using existing setPadButton method
        // Range 255 for pressed, 0 for released (matching PS2 button range)
        int range = pressed ? 255 : 0;
        NativeApp.setPadButton(button, range, pressed);

        // Hide touch controls as soon as controller activity is detected
        maybeActivateControllerUi();
    }
    
    private String getButtonName(int button) {
        switch (button) {
            case KeyEvent.KEYCODE_BUTTON_A: return "Cross";
            case KeyEvent.KEYCODE_BUTTON_B: return "Circle";
            case KeyEvent.KEYCODE_BUTTON_X: return "Square";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Triangle";
            case KeyEvent.KEYCODE_BUTTON_L1: return "L1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "R1";
            case KeyEvent.KEYCODE_BUTTON_L2: return "L2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "R2";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Select";
            case KeyEvent.KEYCODE_BUTTON_START: return "Start";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_DPAD_UP: return "D-Up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "D-Down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "D-Left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "D-Right";
            default: return "Unknown(" + button + ")";
        }
    }

    @Override
    public void onControllerAnalogInput(int controllerId, int axis, float value) {
        android.util.Log.d("Controller", "Controller " + controllerId + " axis " + axis + " (" + getAxisName(axis) + ") value " + value);

        // Send analog input to native code using setPadButton
        handleAnalogInput(axis, value);

        // Hide touch controls on analog movement as well
        if (Math.abs(value) > 0.1f) {
            maybeActivateControllerUi();
        }
    }

    private void maybeActivateControllerUi() {
        if (!controllerUiApplied) {
            controllerUiApplied = true;
            runOnUiThread(() -> setControlsVisible(false));
        }
    }

    @Override
    public void onControllerCombo(int controllerId, String comboName) {
        android.util.Log.d("Controller", "Controller " + controllerId + " combo: " + comboName);
        
        if ("select_start".equals(comboName)) {
            // Show quick actions dialog
            runOnUiThread(() -> {
                QuickActionsDialogFragment dialog = new QuickActionsDialogFragment();
                dialog.show(getSupportFragmentManager(), "quick_actions");
            });
        }
    }
    
    private String getAxisName(int axis) {
        switch (axis) {
            case 110: return "L-Up";
            case 111: return "L-Right";
            case 112: return "L-Down";
            case 113: return "L-Left";
            case 120: return "R-Up";
            case 121: return "R-Right";
            case 122: return "R-Down";
            case 123: return "R-Left";
            case KeyEvent.KEYCODE_BUTTON_L2: return "L2-Trigger";
            case KeyEvent.KEYCODE_BUTTON_R2: return "R2-Trigger";
            default: return "Unknown(" + axis + ")";
        }
    }
    
    private void handleAnalogInput(int axis, float value) {
        // Convert analog input to button presses for the native interface    
        // For analog sticks, only send positive values (negative values are handled by opposite direction)
        int intensity = Math.max(0, Math.round(Math.abs(value) * 255));
        boolean pressed = Math.abs(value) > 0.1f;
        
        switch (axis) {
            case ControllerInputHandler.PAD_L_UP:
            case ControllerInputHandler.PAD_L_DOWN:
            case ControllerInputHandler.PAD_L_LEFT:
            case ControllerInputHandler.PAD_L_RIGHT:
            case ControllerInputHandler.PAD_R_UP:
            case ControllerInputHandler.PAD_R_DOWN:
            case ControllerInputHandler.PAD_R_LEFT:
            case ControllerInputHandler.PAD_R_RIGHT:
                // Analog stick directions
                NativeApp.setPadButton(axis, intensity, pressed);
                break;
            case ControllerInputHandler.PAD_L2:
            case ControllerInputHandler.PAD_R2:
                // Triggers: 0-255 range
                NativeApp.setPadButton(axis, Math.round(value * 255), value > 0.1f);
                break;
        }
    }
}
