package com.appletv.launcher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.LruCache;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.appletv.launcher.dialog.AppMenuDialogManager;
import com.appletv.launcher.dialog.BluetoothDialogManager;
import com.appletv.launcher.dialog.SettingsDialogManager;
import com.appletv.launcher.media.MediaWidgetManager;
import com.appletv.launcher.recents.RecentsOverlayManager;
import com.appletv.launcher.reorder.TileReorderManager;
import com.appletv.launcher.util.UIUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class MainActivity extends Activity implements View.OnClickListener, View.OnFocusChangeListener, BluetoothTracker.OnBluetoothStateChangedListener {

    private FrameLayout rootLayout;
    private TextView tvClock;
    private TextView tvDate;
    private TextView btnWifi;
    private TextView btnBluetooth;
    private TextView btnMetrics;
    private TextView btnSettings;

    private TextView tvFocusedAppTitle;
    private LinearLayout layoutSpotlight;
    private View layoutNowPlaying;
    private View btnNowPlayingArt;
    private View btnNowPlayingPrev;
    private View btnNowPlayingPlay;
    private View btnNowPlayingNext;
    private View btnNowPlayingStop;
    private AppleMusicGradientView appleMusicGradientView;
    private View viewHeaderScrim;
    private ScrollView mainScroll;
    private GridLayout gridDock;
    private GridLayout gridApps;

    private MediaWidgetManager mediaWidgetManager;
    private BluetoothDialogManager bluetoothDialogManager;
    private SettingsDialogManager settingsDialogManager;
    private AppMenuDialogManager appMenuDialogManager;
    private RecentsOverlayManager recentsOverlayManager;
    private TileReorderManager tileReorderManager;

    private final LruCache<String, Drawable> iconCache = new LruCache<String, Drawable>(64);
    private final LruCache<String, Integer> colorCache = new LruCache<String, Integer>(128);
    private final ExecutorService appLoaderExecutor = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int count = 0;
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "AppIconLoader-" + (++count));
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    });

    private int currentSurfaceColor = 0xFF141218;
    private ValueAnimator ambientAnimator;
    private ObjectAnimator driftAnimatorX;
    private ObjectAnimator driftAnimatorY;
    private boolean isAmbientMotionEnabled = true;

    private View lastFocusedView = null;
    private int currentGlowColor = 0x25FFFFFF;
    private Typeface outfitThin;
    private Typeface outfitBold;
    private Typeface outfitMedium;
    private TimeTickReceiver timeTickReceiver;
    private BluetoothTracker bluetoothTracker;
    private RecentAppsManager recentAppsManager;
    private AppPreferencesManager appPrefsManager;
    private final Map<String, AppInfo> allAppsMap = new LinkedHashMap<String, AppInfo>();

    private String lastMovedPackage = null;
    private boolean isLongPressHandled = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PathInterpolator m3EmphasizedInterpolator = new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);

    private final Runnable longPressDetector = new Runnable() {
        @Override
        public void run() {
            View focused = getCurrentFocus();
            if (focused != null && focused.getTag() instanceof AppInfo && !appMenuDialogManager.isOpen()) {
                isLongPressHandled = true;
                boolean isDock = isChildOf(gridDock, focused);
                appMenuDialogManager.open((AppInfo) focused.getTag(), isDock, focused);
            }
        }
    };

    private final BroadcastReceiver recentsToggleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            recentsOverlayManager.toggle(lastFocusedView, allAppsMap);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timeTickReceiver = new TimeTickReceiver(this);
        bluetoothTracker = new BluetoothTracker(this, this);
        recentAppsManager = new RecentAppsManager(this);
        appPrefsManager = new AppPreferencesManager(this);

        initViews();
        loadTypeface();
        updateTimeAndGreeting();
        updateWifiSsid();
        updateSystemMetrics();
        setupTopBar();
        loadApps();

        registerReceiver(recentsToggleReceiver, new IntentFilter(KeyInterceptorService.ACTION_TOGGLE_RECENTS));
    }

    private void initViews() {
        rootLayout = findViewById(R.id.root_layout);
        tvClock = findViewById(R.id.tv_clock);
        tvDate = findViewById(R.id.tv_date);
        btnWifi = findViewById(R.id.btn_wifi);
        btnBluetooth = findViewById(R.id.btn_bluetooth);
        btnMetrics = findViewById(R.id.btn_metrics);
        btnSettings = findViewById(R.id.btn_settings);

        tvFocusedAppTitle = findViewById(R.id.tv_focused_app_title);
        layoutSpotlight = findViewById(R.id.layout_spotlight);
        layoutNowPlaying = findViewById(R.id.layout_now_playing);
        btnNowPlayingArt = findViewById(R.id.container_now_playing_art);
        if (btnNowPlayingArt != null) {
            btnNowPlayingArt.setClipToOutline(true);
            btnNowPlayingArt.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(40));
                }
            });
        }
        btnNowPlayingPrev = findViewById(R.id.btn_now_playing_prev);
        btnNowPlayingPlay = findViewById(R.id.btn_now_playing_play);
        btnNowPlayingNext = findViewById(R.id.btn_now_playing_next);
        btnNowPlayingStop = findViewById(R.id.btn_now_playing_stop);

        View.OnFocusChangeListener mediaFocusListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(final View v, boolean hasFocus) {
                if (hasFocus) {
                    lastFocusedView = v;
                    if (tvFocusedAppTitle != null) {
                        tvFocusedAppTitle.setText("Now Playing");
                    }
                    if (layoutNowPlaying != null) {
                        layoutNowPlaying.setActivated(true);
                    }
                    float scale = (v == btnNowPlayingPlay) ? 1.15f : (v == btnNowPlayingArt ? 1.08f : 1.14f);
                    v.animate()
                            .scaleX(scale)
                            .scaleY(scale)
                            .translationZ(dpToPx(6))
                            .setDuration(160)
                            .setInterpolator(m3EmphasizedInterpolator)
                            .start();
                } else {
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .translationZ(0f)
                            .setDuration(160)
                            .setInterpolator(m3EmphasizedInterpolator)
                            .start();
                    v.post(new Runnable() {
                        @Override
                        public void run() {
                            if (layoutNowPlaying != null && !layoutNowPlaying.hasFocus()) {
                                layoutNowPlaying.setActivated(false);
                            }
                        }
                    });
                }
            }
        };
        if (btnNowPlayingArt != null) btnNowPlayingArt.setOnFocusChangeListener(mediaFocusListener);
        if (btnNowPlayingPrev != null) btnNowPlayingPrev.setOnFocusChangeListener(mediaFocusListener);
        if (btnNowPlayingPlay != null) btnNowPlayingPlay.setOnFocusChangeListener(mediaFocusListener);
        if (btnNowPlayingNext != null) btnNowPlayingNext.setOnFocusChangeListener(mediaFocusListener);
        if (btnNowPlayingStop != null) btnNowPlayingStop.setOnFocusChangeListener(mediaFocusListener);

        appleMusicGradientView = findViewById(R.id.apple_music_gradient_view);
        viewHeaderScrim = findViewById(R.id.view_header_scrim);
        mainScroll = findViewById(R.id.main_scroll);
        if (mainScroll != null) {
            mainScroll.setClipToPadding(false);
        }
        gridDock = findViewById(R.id.grid_dock);
        gridApps = findViewById(R.id.grid_apps);

        mediaWidgetManager = new MediaWidgetManager(this, rootLayout, new MediaWidgetManager.OnMediaColorListener() {
            @Override
            public void onMediaColorExtracted(int dominantColor) {
                animateAmbientGlow(dominantColor);
            }
        });
        mediaWidgetManager.start();

        if (viewHeaderScrim != null) {
            viewHeaderScrim.setAlpha(0f);
            updateHeaderScrim(currentSurfaceColor);
        }

        if (mainScroll != null) {
            mainScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override
                public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                    int collapseDistance = dpToPx(48);
                    float scrollRatio = Math.min(1.0f, Math.max(0.0f, (float) scrollY / (float) collapseDistance));
                    if (viewHeaderScrim != null) {
                        viewHeaderScrim.setAlpha(scrollRatio * 0.94f);
                    }
                }
            });
        }

        BluetoothDialogManager.AmbientDriftListener driftListener = new BluetoothDialogManager.AmbientDriftListener() {
            @Override
            public void onStartAmbientDrift() {
                startAmbientDriftMotion();
            }
            @Override
            public void onStopAmbientDrift() {
                stopAmbientDriftMotion();
            }
        };

        // Initialize Bluetooth Dialog Manager
        FrameLayout layoutBluetoothScrim = findViewById(R.id.layout_bluetooth_scrim);
        LinearLayout layoutBluetoothDialog = findViewById(R.id.layout_bluetooth_dialog);
        LinearLayout containerBtDevices = findViewById(R.id.container_bt_devices);
        TextView btnOpenBtSettings = findViewById(R.id.btn_open_bt_settings);
        bluetoothDialogManager = new BluetoothDialogManager(
                this,
                bluetoothTracker,
                layoutBluetoothScrim,
                layoutBluetoothDialog,
                containerBtDevices,
                btnOpenBtSettings,
                btnBluetooth,
                m3EmphasizedInterpolator,
                driftListener
        );

        // Initialize Settings Dialog Manager
        FrameLayout layoutSettingsScrim = findViewById(R.id.layout_settings_scrim);
        LinearLayout layoutSettingsDialog = findViewById(R.id.layout_settings_dialog);
        TextView settingItemSystem = findViewById(R.id.setting_item_system);
        TextView settingItemNetwork = findViewById(R.id.setting_item_network);
        TextView settingItemBluetooth = findViewById(R.id.setting_item_bluetooth);
        TextView settingItemApps = findViewById(R.id.setting_item_apps);
        TextView settingItemDevice = findViewById(R.id.setting_item_device);
        TextView settingItemAmbient = findViewById(R.id.setting_item_ambient);

        settingsDialogManager = new SettingsDialogManager(
                this,
                layoutSettingsScrim,
                layoutSettingsDialog,
                btnSettings,
                settingItemSystem,
                settingItemNetwork,
                settingItemBluetooth,
                settingItemApps,
                settingItemDevice,
                settingItemAmbient,
                m3EmphasizedInterpolator,
                driftListener,
                new SettingsDialogManager.AmbientToggleListener() {
                    @Override
                    public boolean onToggleAmbientMotion() {
                        isAmbientMotionEnabled = !isAmbientMotionEnabled;
                        if (isAmbientMotionEnabled) {
                            startAmbientDriftMotion();
                        } else {
                            stopAmbientDriftMotion();
                        }
                        return isAmbientMotionEnabled;
                    }
                    @Override
                    public boolean isAmbientMotionActive() {
                        return isAmbientMotionEnabled;
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        bluetoothDialogManager.toggle();
                    }
                }
        );

        // Initialize App Menu Dialog Manager
        FrameLayout layoutAppMenuScrim = findViewById(R.id.layout_app_menu_scrim);
        LinearLayout layoutAppMenuCard = findViewById(R.id.layout_app_menu_card);
        FrameLayout iconFrame = findViewById(R.id.menu_app_icon_frame);
        ImageView menuAppIcon = findViewById(R.id.menu_app_icon);
        TextView menuAppTitle = findViewById(R.id.menu_app_title);
        TextView menuAppPackage = findViewById(R.id.menu_app_package);
        TextView btnMenuOpen = findViewById(R.id.btn_menu_open);
        TextView btnMenuMove = findViewById(R.id.btn_menu_move);
        TextView btnMenuTogglePin = findViewById(R.id.btn_menu_toggle_pin);
        TextView btnMenuForceStop = findViewById(R.id.btn_menu_force_stop);
        TextView btnMenuUninstall = findViewById(R.id.btn_menu_uninstall);
        TextView btnMenuClose = findViewById(R.id.btn_menu_close);

        appMenuDialogManager = new AppMenuDialogManager(
                this,
                appPrefsManager,
                recentAppsManager,
                layoutAppMenuScrim,
                layoutAppMenuCard,
                iconFrame,
                menuAppIcon,
                menuAppTitle,
                menuAppPackage,
                btnMenuOpen,
                btnMenuMove,
                btnMenuTogglePin,
                btnMenuForceStop,
                btnMenuUninstall,
                btnMenuClose,
                m3EmphasizedInterpolator,
                new AppMenuDialogManager.AppMenuActionListener() {
                    @Override
                    public void onLaunchApp(AppInfo app) {
                        launchApp(app);
                    }
                    @Override
                    public void onStartReordering(AppInfo app) {
                        View tile = findTileByPackage(gridDock, app.packageName);
                        tileReorderManager.startReordering(app, tile);
                    }
                    @Override
                    public void onFavoritesChanged(String changedPkg) {
                        lastMovedPackage = changedPkg;
                        loadApps();
                    }
                    @Override
                    public View getFallbackFocusView() {
                        return (gridDock.getChildCount() > 0) ? gridDock.getChildAt(0) : null;
                    }
                }
        );

        // Initialize Recents Overlay Manager
        FrameLayout layoutRecentsOverlay = findViewById(R.id.layout_recents_overlay);
        LinearLayout containerRecentsCarousel = findViewById(R.id.container_recents_carousel);
        View layoutRecentsEmptyState = findViewById(R.id.layout_recents_empty_state);
        View btnClearRecents = findViewById(R.id.btn_clear_recents);
        ImageView iconClearSwing = findViewById(R.id.icon_clear_swing);
        TextView tvClearRecentsLabel = findViewById(R.id.tv_clear_recents_label);

        recentsOverlayManager = new RecentsOverlayManager(
                this,
                recentAppsManager,
                layoutRecentsOverlay,
                containerRecentsCarousel,
                layoutRecentsEmptyState,
                btnClearRecents,
                tvClearRecentsLabel,
                iconClearSwing,
                m3EmphasizedInterpolator,
                driftListener,
                new RecentsOverlayManager.RecentsActionListener() {
                    @Override
                    public void onLaunchApp(AppInfo app) {
                        launchApp(app);
                    }
                    @Override
                    public void onUpdateSystemMetrics() {
                        updateSystemMetrics();
                    }
                }
        );

        // Initialize Tile Reorder Manager
        tileReorderManager = new TileReorderManager(
                this,
                gridDock,
                appPrefsManager,
                tvFocusedAppTitle,
                m3EmphasizedInterpolator,
                new TileReorderManager.ReorderListener() {
                    @Override
                    public void onLinkDpadNavigation() {
                        int columns = getResources().getInteger(R.integer.columns);
                        if (columns <= 0) columns = 4;
                        MainActivity.this.linkDpadNavigation(gridDock.getChildCount(), gridApps.getChildCount(), columns);
                    }
                }
        );
    }

    private void loadTypeface() {
        try {
            outfitThin = Typeface.createFromAsset(getAssets(), "fonts/Outfit.ttf");
            outfitBold = Typeface.createFromAsset(getAssets(), "fonts/Outfit-Bold.ttf");
            outfitMedium = Typeface.createFromAsset(getAssets(), "fonts/Outfit-Medium.ttf");

            if (outfitThin != null) {
                tvClock.setTypeface(outfitThin);
                tvFocusedAppTitle.setTypeface(outfitThin);
            }
            if (outfitMedium != null) {
                tvDate.setTypeface(outfitMedium);
            }
            if (outfitBold != null) {
                btnWifi.setTypeface(outfitBold);
                btnBluetooth.setTypeface(outfitBold);
                btnMetrics.setTypeface(outfitBold);
                btnSettings.setTypeface(outfitBold);

                TextView favHeader = findViewById(R.id.tv_favorites_header);
                if (favHeader != null) favHeader.setTypeface(outfitBold);
                TextView allAppsHeader = findViewById(R.id.tv_all_apps_header);
                if (allAppsHeader != null) allAppsHeader.setTypeface(outfitBold);
                TextView recentsHeader = findViewById(R.id.tv_recents_header);
                if (recentsHeader != null) recentsHeader.setTypeface(outfitBold);

                TextView emptyTitle = findViewById(R.id.tv_empty_state_title);
                if (emptyTitle != null) emptyTitle.setTypeface(outfitBold);
                TextView npTitle = findViewById(R.id.tv_now_playing_title);
                if (npTitle != null) npTitle.setTypeface(outfitBold, Typeface.NORMAL);
                TextView npSubtitle = findViewById(R.id.tv_now_playing_subtitle);
                if (npSubtitle != null && outfitMedium != null) npSubtitle.setTypeface(outfitMedium, Typeface.NORMAL);
            }

            bluetoothDialogManager.setTypefaces(outfitBold, outfitMedium);
            recentsOverlayManager.setTypefaces(outfitBold, outfitMedium);
        } catch (Throwable ignored) {
        }
    }

    public void updateTimeAndGreeting() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm", Locale.getDefault());
        tvClock.setText(timeFormat.format(new Date()));

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
        tvDate.setText(dateFormat.format(new Date()));
    }

    private void updateWifiSsid() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo active = cm.getActiveNetworkInfo();
                if (active != null && active.isConnected()) {
                    if (active.getType() == ConnectivityManager.TYPE_WIFI) {
                        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        if (wm != null) {
                            WifiInfo info = wm.getConnectionInfo();
                            if (info != null) {
                                String ssid = info.getSSID();
                                if (ssid != null && !ssid.isEmpty() && !ssid.equals("<unknown ssid>")) {
                                    if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() >= 2) {
                                        ssid = ssid.substring(1, ssid.length() - 1);
                                    }
                                    btnWifi.setText(ssid);
                                    btnWifi.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_wifi, 0, 0, 0);
                                    return;
                                }
                            }
                        }
                        btnWifi.setText("Wi-Fi");
                        btnWifi.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_wifi, 0, 0, 0);
                        return;
                    } else if (active.getType() == ConnectivityManager.TYPE_ETHERNET) {
                        btnWifi.setText("Ethernet");
                        btnWifi.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_wifi, 0, 0, 0);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}
        btnWifi.setText("Disconnected");
        btnWifi.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_wifi_off, 0, 0, 0);
    }

    private void updateSystemMetrics() {
        btnMetrics.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_memory, 0, 0, 0);
        SystemMetricsTracker.MetricsData metrics = SystemMetricsTracker.readMetrics(this);
        if (metrics != null && btnMetrics != null) {
            btnMetrics.setText(metrics.getPillDisplay());
        }
    }

    @Override
    public void onBluetoothStateChanged(String statusText, boolean isConnected) {
        if (btnBluetooth != null) {
            btnBluetooth.setText(statusText);
            btnBluetooth.setCompoundDrawablesWithIntrinsicBounds(
                    isConnected ? R.drawable.ic_bluetooth_connected : R.drawable.ic_bluetooth, 0, 0, 0);
        }
    }

    private void setupTopBar() {
        btnWifi.setNextFocusLeftId(R.id.btn_wifi);
        btnWifi.setNextFocusRightId(R.id.btn_bluetooth);
        btnWifi.setNextFocusUpId(R.id.btn_wifi);

        btnBluetooth.setNextFocusLeftId(R.id.btn_wifi);
        btnBluetooth.setNextFocusRightId(R.id.btn_metrics);
        btnBluetooth.setNextFocusUpId(R.id.btn_bluetooth);

        btnMetrics.setNextFocusLeftId(R.id.btn_bluetooth);
        btnMetrics.setNextFocusRightId(R.id.btn_settings);
        btnMetrics.setNextFocusUpId(R.id.btn_metrics);

        btnSettings.setNextFocusLeftId(R.id.btn_metrics);
        btnSettings.setNextFocusRightId(R.id.btn_settings);
        btnSettings.setNextFocusUpId(R.id.btn_settings);

        btnWifi.setOnClickListener(this);
        btnBluetooth.setOnClickListener(this);
        btnMetrics.setOnClickListener(this);
        btnSettings.setOnClickListener(this);

        if (tvClock != null) {
            tvClock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
                    } catch (Throwable e) {
                        try {
                            startActivity(new Intent(Settings.ACTION_SETTINGS));
                        } catch (Throwable ignored) {}
                    }
                }
            });
            tvClock.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        lastFocusedView = v;
                        tvFocusedAppTitle.setText("Date & Time Settings");
                        animateAmbientGlow(0x30FFFFFF);
                        v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(160).start();
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start();
                    }
                }
            });
        }

        View.OnFocusChangeListener pillFocus = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    lastFocusedView = v;
                    int id = v.getId();
                    if (id == R.id.btn_wifi) {
                        tvFocusedAppTitle.setText("Wi-Fi Network");
                        animateAmbientGlow(0x30FFFFFF);
                    } else if (id == R.id.btn_bluetooth) {
                        tvFocusedAppTitle.setText("Bluetooth Devices");
                        animateAmbientGlow(0x30FFFFFF);
                    } else if (id == R.id.btn_metrics) {
                        tvFocusedAppTitle.setText("Recent Apps & Memory");
                        animateAmbientGlow(0x30FFFFFF);
                    } else if (id == R.id.btn_settings) {
                        tvFocusedAppTitle.setText("Settings");
                        animateAmbientGlow(0x30FFFFFF);
                    }
                    if (mainScroll != null) {
                        mainScroll.smoothScrollTo(0, 0);
                    }
                }
            }
        };
        btnWifi.setOnFocusChangeListener(pillFocus);
        btnBluetooth.setOnFocusChangeListener(pillFocus);
        btnMetrics.setOnFocusChangeListener(pillFocus);
        btnSettings.setOnFocusChangeListener(pillFocus);

        btnMetrics.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
                } catch (Throwable e) {
                    try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) {}
                }
                return true;
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (tileReorderManager.isReordering()) {
            tileReorderManager.stopReordering();
            return;
        }
        int id = v.getId();
        if (id == R.id.btn_bluetooth) {
            bluetoothDialogManager.toggle();
        } else if (id == R.id.btn_wifi) {
            try {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } catch (Throwable e) {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) {}
            }
        } else if (id == R.id.btn_metrics) {
            recentsOverlayManager.toggle(lastFocusedView, allAppsMap);
        } else if (id == R.id.btn_settings) {
            settingsDialogManager.toggle();
        } else {
            Object tag = v.getTag();
            if (tag instanceof AppInfo) {
                if (isLongPressHandled || (SystemClock.uptimeMillis() - appMenuDialogManager.getOpenedTimestamp() < 600)) {
                    return;
                }
                launchApp((AppInfo) tag);
            }
        }
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        int columns = getResources().getInteger(R.integer.columns);
        if (columns <= 0) columns = 4;
        gridDock.setColumnCount(columns);
        gridApps.setColumnCount(columns);

        String[] defaultDock = getResources().getStringArray(R.array.dock_apps);
        List<String> userFavorites = appPrefsManager.getFavorites(defaultDock);

        allAppsMap.clear();

        // Query Leanback launcher activities
        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> leanbackApps = pm.queryIntentActivities(leanbackIntent, 0);

        for (ResolveInfo ri : leanbackApps) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;

            String label = ri.loadLabel(pm).toString();
            AppInfo info = new AppInfo(pkg, ri.activityInfo.name, label);
            info.isBanner = true;

            Drawable cached = iconCache.get(pkg);
            if (cached != null) {
                info.banner = cached;
                info.icon = cached;
                info.isLoaded = true;
                Integer dom = colorCache.get(pkg);
                if (dom != null) info.dominantColor = dom;
            }
            allAppsMap.put(pkg, info);
        }

        // Query standard launcher activities as fallback
        Intent standardIntent = new Intent(Intent.ACTION_MAIN);
        standardIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> standardApps = pm.queryIntentActivities(standardIntent, 0);

        for (ResolveInfo ri : standardApps) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || allAppsMap.containsKey(pkg)) continue;

            String label = ri.loadLabel(pm).toString();
            AppInfo info = new AppInfo(pkg, ri.activityInfo.name, label);
            info.isBanner = false;

            Drawable cached = iconCache.get(pkg);
            if (cached != null) {
                info.banner = cached;
                info.icon = cached;
                info.isLoaded = true;
                Integer dom = colorCache.get(pkg);
                if (dom != null) info.dominantColor = dom;
            }
            allAppsMap.put(pkg, info);
        }

        List<AppInfo> dockApps = new ArrayList<AppInfo>();
        List<AppInfo> remainingApps = new ArrayList<AppInfo>(allAppsMap.values());

        // Fill dock based on user preferences order
        for (String favPkg : userFavorites) {
            if (allAppsMap.containsKey(favPkg)) {
                AppInfo app = allAppsMap.get(favPkg);
                if (!dockApps.contains(app)) {
                    dockApps.add(app);
                    remainingApps.remove(app);
                }
            }
        }

        // Ensure dock is not empty if apps are available
        while (dockApps.size() < columns && !remainingApps.isEmpty()) {
            dockApps.add(remainingApps.remove(0));
        }

        Collections.sort(remainingApps, new AppComparator());

        populateGrid(gridDock, dockApps, true, columns);
        populateGrid(gridApps, remainingApps, false, columns);

        linkDpadNavigation(dockApps.size(), remainingApps.size(), columns);

        View focusTarget = null;
        if (lastMovedPackage != null) {
            focusTarget = findTileByPackage(gridDock, lastMovedPackage);
            if (focusTarget == null) {
                focusTarget = findTileByPackage(gridApps, lastMovedPackage);
            }
            lastMovedPackage = null;
        }

        if (focusTarget != null) {
            focusTarget.requestFocus();
        } else if (gridDock.getChildCount() > 0) {
            gridDock.getChildAt(0).requestFocus();
        }
    }

    private View findTileByPackage(GridLayout grid, String pkg) {
        if (grid == null || pkg == null) return null;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof AppInfo && pkg.equals(((AppInfo) tag).packageName)) {
                return child;
            }
        }
        return null;
    }

    private void populateGrid(GridLayout grid, List<AppInfo> apps, final boolean isDock, int columns) {
        grid.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < apps.size(); i++) {
            final AppInfo app = apps.get(i);
            final View tile = inflater.inflate(R.layout.item_app, grid, false);
            tile.setId(View.generateViewId());
            tile.setTag(app);

            final FrameLayout cardFrame = (FrameLayout) tile.findViewById(R.id.card_frame);
            cardFrame.setClipToOutline(true);
            cardFrame.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(16));
                }
            });

            final ImageView imgBanner = (ImageView) tile.findViewById(R.id.img_banner);
            final TextView tvTitle = (TextView) tile.findViewById(R.id.tv_title);
            if (outfitBold != null) {
                tvTitle.setTypeface(outfitBold);
            }
            tvTitle.setText(app.label);

            if (app.isLoaded && app.banner != null) {
                imgBanner.setImageDrawable(app.banner);
                if (app.isBanner) {
                    imgBanner.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imgBanner.setPadding(0, 0, 0, 0);
                } else {
                    imgBanner.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int pad = dpToPx(18);
                    imgBanner.setPadding(pad, pad, pad, pad);
                }
            } else {
                appLoaderExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        Drawable d = null;
                        PackageManager pm = getPackageManager();
                        try {
                            if (app.isBanner) {
                                d = pm.getActivityBanner(new android.content.ComponentName(app.packageName, app.activityName));
                                if (d == null) d = pm.getApplicationBanner(app.packageName);
                            }
                            if (d == null) {
                                d = pm.getActivityIcon(new android.content.ComponentName(app.packageName, app.activityName));
                                if (d == null) d = pm.getApplicationIcon(app.packageName);
                                app.isBanner = false;
                            }
                        } catch (Throwable ignored) {}

                        if (d != null) {
                            iconCache.put(app.packageName, d);
                            app.banner = d;
                            app.icon = d;
                            app.isLoaded = true;

                            int dom = ColorExtractor.extractDominantColor(d);
                            if (dom != 0) {
                                colorCache.put(app.packageName, dom);
                                app.dominantColor = dom;
                            }
                        }

                        final Drawable finalDrawable = d;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (finalDrawable != null) {
                                    imgBanner.setImageDrawable(finalDrawable);
                                    if (app.isBanner) {
                                        imgBanner.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                        imgBanner.setPadding(0, 0, 0, 0);
                                    } else {
                                        imgBanner.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                        int pad = dpToPx(18);
                                        imgBanner.setPadding(pad, pad, pad, pad);
                                    }
                                }
                            }
                        });
                    }
                });
            }

            tile.setOnClickListener(this);
            tile.setOnFocusChangeListener(this);
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    isLongPressHandled = true;
                    appMenuDialogManager.open(app, isDock, v);
                    return true;
                }
            });

            grid.addView(tile);
        }
    }

    private void linkDpadNavigation(int dockCount, int gridCount, int columns) {
        for (int i = 0; i < dockCount; i++) {
            View tile = gridDock.getChildAt(i);
            if (tile == null) continue;

            if (i > 0) {
                tile.setNextFocusLeftId(gridDock.getChildAt(i - 1).getId());
            } else {
                tile.setNextFocusLeftId(tile.getId());
            }

            if (i < dockCount - 1) {
                tile.setNextFocusRightId(gridDock.getChildAt(i + 1).getId());
            } else {
                tile.setNextFocusRightId(tile.getId());
            }

            if (gridCount > 0) {
                int targetDown = Math.min(i, gridCount - 1);
                tile.setNextFocusDownId(gridApps.getChildAt(targetDown).getId());
            } else {
                tile.setNextFocusDownId(tile.getId());
            }
        }

        for (int i = 0; i < gridCount; i++) {
            View tile = gridApps.getChildAt(i);
            if (tile == null) continue;

            int col = i % columns;

            if (col > 0) {
                tile.setNextFocusLeftId(gridApps.getChildAt(i - 1).getId());
            } else {
                tile.setNextFocusLeftId(tile.getId());
            }

            if (col < columns - 1 && (i + 1) < gridCount) {
                tile.setNextFocusRightId(gridApps.getChildAt(i + 1).getId());
            } else {
                tile.setNextFocusRightId(tile.getId());
            }

            if (i >= columns) {
                tile.setNextFocusUpId(gridApps.getChildAt(i - columns).getId());
            } else {
                int targetUp = Math.min(col, dockCount - 1);
                if (targetUp >= 0 && targetUp < dockCount) {
                    tile.setNextFocusUpId(gridDock.getChildAt(targetUp).getId());
                } else {
                    tile.setNextFocusUpId(tile.getId());
                }
            }

            if (i + columns < gridCount) {
                tile.setNextFocusDownId(gridApps.getChildAt(i + columns).getId());
            } else {
                tile.setNextFocusDownId(tile.getId());
            }
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            lastFocusedView = v;
            Object tag = v.getTag();
            if (tag instanceof AppInfo) {
                AppInfo app = (AppInfo) tag;
                if (tvFocusedAppTitle != null) {
                    tvFocusedAppTitle.setText(app.label);
                }
                int targetColor = (app.dominantColor != 0) ? app.dominantColor : 0x25FFFFFF;
                animateAmbientGlow(targetColor);
            }

            v.animate()
                    .scaleX(1.10f)
                    .scaleY(1.10f)
                    .translationZ(dpToPx(14))
                    .setDuration(160)
                    .setInterpolator(m3EmphasizedInterpolator)
                    .start();

            ensureVisibleInScroll(v);
        } else {
            v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(160)
                    .setInterpolator(m3EmphasizedInterpolator)
                    .start();
        }
    }

    private void startAmbientDriftMotion() {
        if (appleMusicGradientView != null && isAmbientMotionEnabled) {
            appleMusicGradientView.startMotion();
        }
    }

    private void stopAmbientDriftMotion() {
        if (appleMusicGradientView != null) {
            appleMusicGradientView.stopMotion();
        }
    }

    private void animateAmbientGlow(int targetColor) {
        if (ambientAnimator != null && ambientAnimator.isRunning()) {
            ambientAnimator.cancel();
        }
        currentGlowColor = targetColor;
        if (appleMusicGradientView != null) {
            appleMusicGradientView.setAppAccentColor(targetColor, true);
        }
    }

    private void updateHeaderScrim(int surf) {
        if (viewHeaderScrim != null) {
            int topColor = UIUtils.blendColors(surf, 0xFF000000, 0.45f);
            int midColor = UIUtils.blendColors(surf, 0xFF000000, 0.25f);
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{topColor, midColor, 0x00000000}
            );
            viewHeaderScrim.setBackground(gd);
        }
    }

    private void ensureVisibleInScroll(final View v) {
        if (mainScroll == null || v == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                int[] vLoc = new int[2];
                v.getLocationInWindow(vLoc);
                int tileTop = vLoc[1];
                int tileHeight = v.getHeight();
                int screenHeight = getResources().getDisplayMetrics().heightPixels;

                int topSafeMargin = dpToPx(140);
                int bottomSafeMargin = screenHeight - dpToPx(90);

                if (tileTop < topSafeMargin) {
                    int scrollDelta = tileTop - topSafeMargin;
                    mainScroll.smoothScrollBy(0, scrollDelta);
                } else if (tileTop + tileHeight > bottomSafeMargin) {
                    int scrollDelta = (tileTop + tileHeight) - bottomSafeMargin;
                    mainScroll.smoothScrollBy(0, scrollDelta);
                }
            }
        });
    }

    private void launchApp(final AppInfo app) {
        if (app == null) return;
        try {
            recentAppsManager.recordAppLaunched(app.packageName);
            Intent launchIntent = getPackageManager().getLeanbackLaunchIntentForPackage(app.packageName);
            if (launchIntent == null) {
                launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName);
            }
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(launchIntent);
            }
        } catch (Throwable e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_MAIN);
                fallback.setClassName(app.packageName, app.activityName);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Throwable ignored) {}
        }

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateSystemMetrics();
            }
        }, 1200);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        // 1. Reorder Mode handling
        int columns = getResources().getInteger(R.integer.columns);
        if (columns <= 0) columns = 4;
        if (tileReorderManager.handleKeyEvent(event, columns)) {
            return true;
        }

        // 2. App Long-Press detection
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    isLongPressHandled = false;
                    mainHandler.removeCallbacks(longPressDetector);
                    View focused = getCurrentFocus();
                    if (focused != null && focused.getTag() instanceof AppInfo && !appMenuDialogManager.isOpen()) {
                        int timeout = Math.max(400, ViewConfiguration.getLongPressTimeout());
                        mainHandler.postDelayed(longPressDetector, timeout);
                    }
                } else if (!isLongPressHandled) {
                    mainHandler.removeCallbacks(longPressDetector);
                    View focused = getCurrentFocus();
                    if (focused != null && focused.getTag() instanceof AppInfo && !appMenuDialogManager.isOpen()) {
                        isLongPressHandled = true;
                        boolean isDock = isChildOf(gridDock, focused);
                        appMenuDialogManager.open((AppInfo) focused.getTag(), isDock, focused);
                        return true;
                    }
                }
                if (isLongPressHandled) {
                    return true;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                mainHandler.removeCallbacks(longPressDetector);
                if (isLongPressHandled || (SystemClock.uptimeMillis() - appMenuDialogManager.getOpenedTimestamp() < 600)) {
                    isLongPressHandled = false;
                    return true;
                }
            }
        }

        // 3. Modal Key Trapping & Dismissals
        if (appMenuDialogManager.handleKeyEvent(event)) {
            return true;
        }
        if (bluetoothDialogManager.handleKeyEvent(event)) {
            return true;
        }
        if (settingsDialogManager.handleKeyEvent(event)) {
            return true;
        }
        if (recentsOverlayManager.handleKeyEvent(event)) {
            return true;
        }

        // 4. Quick Actions
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                recentsOverlayManager.toggle(lastFocusedView, allAppsMap);
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
                View focused = getCurrentFocus();
                if (focused != null && focused.getTag() instanceof AppInfo) {
                    boolean isDock = isChildOf(gridDock, focused);
                    appMenuDialogManager.open((AppInfo) focused.getTag(), isDock, focused);
                    return true;
                }
            }

            View focused = getCurrentFocus();

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (focused != null && isChildOf(gridDock, focused)) {
                    int idx = getChildIndex(gridDock, focused);
                    if (layoutNowPlaying != null && layoutNowPlaying.getVisibility() == View.VISIBLE) {
                        if (idx == 0) {
                            btnWifi.requestFocus();
                            return true;
                        } else if (idx == 1) {
                            btnBluetooth.requestFocus();
                            return true;
                        } else if (idx == 2) {
                            if (btnNowPlayingArt != null) {
                                btnNowPlayingArt.requestFocus();
                                return true;
                            }
                        } else if (idx == 3) {
                            if (btnNowPlayingPlay != null) {
                                btnNowPlayingPlay.requestFocus();
                                return true;
                            }
                        } else if (idx >= 4) {
                            if (btnNowPlayingStop != null) {
                                btnNowPlayingStop.requestFocus();
                                return true;
                            }
                        }
                    } else {
                        if (idx == 0) {
                            btnWifi.requestFocus();
                            return true;
                        } else if (idx == 1) {
                            btnBluetooth.requestFocus();
                            return true;
                        } else if (idx == 2) {
                            btnMetrics.requestFocus();
                            return true;
                        } else {
                            btnSettings.requestFocus();
                            return true;
                        }
                    }
                } else if (focused != null && (focused == btnNowPlayingArt || focused == btnNowPlayingPrev || focused == btnNowPlayingPlay || focused == btnNowPlayingNext || focused == btnNowPlayingStop)) {
                    if (focused == btnNowPlayingArt) {
                        btnBluetooth.requestFocus();
                        return true;
                    } else if (focused == btnNowPlayingPrev || focused == btnNowPlayingPlay) {
                        btnMetrics.requestFocus();
                        return true;
                    } else {
                        btnSettings.requestFocus();
                        return true;
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (focused != null) {
                    int id = focused.getId();
                    if (id == R.id.btn_wifi) {
                        if (gridDock.getChildCount() > 0) {
                            gridDock.getChildAt(0).requestFocus();
                            return true;
                        }
                    } else if (id == R.id.btn_bluetooth) {
                        if (layoutNowPlaying != null && layoutNowPlaying.getVisibility() == View.VISIBLE) {
                            if (btnNowPlayingArt != null) {
                                btnNowPlayingArt.requestFocus();
                                return true;
                            }
                        } else if (gridDock.getChildCount() > 1) {
                            gridDock.getChildAt(1).requestFocus();
                            return true;
                        }
                    } else if (id == R.id.btn_metrics) {
                        if (layoutNowPlaying != null && layoutNowPlaying.getVisibility() == View.VISIBLE) {
                            if (btnNowPlayingPlay != null) {
                                btnNowPlayingPlay.requestFocus();
                                return true;
                            }
                        } else if (gridDock.getChildCount() > 2) {
                            gridDock.getChildAt(2).requestFocus();
                            return true;
                        }
                    } else if (id == R.id.btn_settings) {
                        if (layoutNowPlaying != null && layoutNowPlaying.getVisibility() == View.VISIBLE) {
                            if (btnNowPlayingStop != null) {
                                btnNowPlayingStop.requestFocus();
                                return true;
                            }
                        } else if (gridDock.getChildCount() > 3) {
                            gridDock.getChildAt(3).requestFocus();
                            return true;
                        }
                    } else if (focused == btnNowPlayingArt || focused == btnNowPlayingPrev || focused == btnNowPlayingPlay || focused == btnNowPlayingNext || focused == btnNowPlayingStop) {
                        if (gridDock.getChildCount() > 0) {
                            int targetIdx = 0;
                            if (focused == btnNowPlayingArt) targetIdx = Math.min(2, gridDock.getChildCount() - 1);
                            else if (focused == btnNowPlayingPrev || focused == btnNowPlayingPlay) targetIdx = Math.min(3, gridDock.getChildCount() - 1);
                            else targetIdx = Math.min(4, gridDock.getChildCount() - 1);
                            gridDock.getChildAt(targetIdx).requestFocus();
                            return true;
                        }
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private boolean isChildOf(ViewGroup parent, View child) {
        if (parent == null || child == null) return false;
        View curr = child;
        while (curr != null) {
            if (curr.getParent() == parent) return true;
            if (curr.getParent() instanceof View) {
                curr = (View) curr.getParent();
            } else {
                break;
            }
        }
        return false;
    }

    private int getChildIndex(ViewGroup parent, View child) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View c = parent.getChildAt(i);
            if (c == child || isDescendant(c, child)) return i;
        }
        return 0;
    }

    private boolean isDescendant(View root, View target) {
        if (root == target) return true;
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (isDescendant(vg.getChildAt(i), target)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (appMenuDialogManager.isOpen()) {
                appMenuDialogManager.dismiss();
                return true;
            }
            if (settingsDialogManager.isOpen()) {
                settingsDialogManager.hide();
                return true;
            }
            if (bluetoothDialogManager.isOpen()) {
                bluetoothDialogManager.hide();
                return true;
            }
            if (recentsOverlayManager.isOpen()) {
                recentsOverlayManager.hide();
                return true;
            }
            if (mainScroll != null && mainScroll.getScrollY() > 50) {
                mainScroll.smoothScrollTo(0, 0);
                if (gridDock.getChildCount() > 0) {
                    gridDock.getChildAt(0).requestFocus();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dpToPx(int dp) {
        return UIUtils.dpToPx(this, dp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(timeTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        updateTimeAndGreeting();
        updateWifiSsid();
        updateSystemMetrics();
        bluetoothTracker.start();
        if (mediaWidgetManager != null) {
            mediaWidgetManager.start();
        }
        startAmbientDriftMotion();

        if (lastFocusedView != null) {
            lastFocusedView.requestFocus();
        } else if (gridDock.getChildCount() > 0) {
            gridDock.getChildAt(0).requestFocus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tileReorderManager.isReordering()) {
            tileReorderManager.stopReordering();
        }
        try {
            unregisterReceiver(timeTickReceiver);
        } catch (Throwable ignored) {
        }
        bluetoothTracker.stop();
        if (mediaWidgetManager != null) {
            mediaWidgetManager.stop();
        }
        stopAmbientDriftMotion();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(recentsToggleReceiver);
        } catch (Throwable ignored) {
        }
        if (mediaWidgetManager != null) {
            mediaWidgetManager.stop();
        }
        stopAmbientDriftMotion();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        try {
            appLoaderExecutor.shutdownNow();
        } catch (Throwable ignored) {}
        if (iconCache != null) {
            iconCache.evictAll();
        }
        if (colorCache != null) {
            colorCache.evictAll();
        }
    }
}
