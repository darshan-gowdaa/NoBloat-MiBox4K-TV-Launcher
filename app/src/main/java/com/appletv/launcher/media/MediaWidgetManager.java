package com.appletv.launcher.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import com.appletv.launcher.ColorExtractor;
import com.appletv.launcher.R;
import com.appletv.launcher.widget.EqualizerView;

import java.util.List;

public class MediaWidgetManager {

    public interface OnMediaColorListener {
        void onMediaColorExtracted(int dominantColor);
    }

    private final Context context;
    private final View container;
    private final View containerArt;
    private final ImageView ivAlbumArt;
    private final TextView tvTrackTitle;
    private final TextView tvArtistApp;
    private final ImageView btnPrev;
    private final ImageView btnPlayPause;
    private final ImageView btnNext;
    private final ImageView btnStop;
    private final EqualizerView equalizerView;
    private final OnMediaColorListener colorListener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MediaSessionManager sessionManager;
    private final ComponentName listenerComponent;
    private final PackageManager packageManager;

    private MediaController activeController = null;
    private String lastTrackSignature = "";
    private final java.util.concurrent.ExecutorService artExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile String currentTrackSignature = "";
    private java.util.concurrent.Future<?> pendingArtTask = null;

    private final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
    private Bitmap lastSourceArt = null;
    private int lastArtResId = 0;
    private boolean hasArtContent = false;
    private String lastTitle = null;
    private String lastArtistSub = null;

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    updatePlaybackState(state);
                    if (activeController != null) {
                        updateMetadata(activeController.getMetadata());
                    }
                }
            });
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateMetadata(metadata);
                }
            });
        }

        @Override
        public void onSessionDestroyed() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    findAndBindActiveSession();
                }
            });
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            findAndBindActiveSession();
                        }
                    });
                }
            };

    public MediaWidgetManager(Context context, View rootView, OnMediaColorListener colorListener) {
        this.context = context.getApplicationContext();
        this.colorListener = colorListener;

        this.container = rootView.findViewById(R.id.layout_now_playing);
        this.containerArt = rootView.findViewById(R.id.container_now_playing_art);
        this.ivAlbumArt = (ImageView) rootView.findViewById(R.id.iv_now_playing_art);
        this.tvTrackTitle = (TextView) rootView.findViewById(R.id.tv_now_playing_title);
        this.tvArtistApp = (TextView) rootView.findViewById(R.id.tv_now_playing_subtitle);
        try {
            android.graphics.Typeface bold = android.graphics.Typeface.createFromAsset(context.getAssets(), "fonts/Outfit-Bold.ttf");
            android.graphics.Typeface med = android.graphics.Typeface.createFromAsset(context.getAssets(), "fonts/Outfit-Medium.ttf");
            if (this.tvTrackTitle != null && bold != null) this.tvTrackTitle.setTypeface(bold, android.graphics.Typeface.NORMAL);
            if (this.tvArtistApp != null && med != null) this.tvArtistApp.setTypeface(med, android.graphics.Typeface.NORMAL);
        } catch (Throwable ignored) {}
        this.btnPrev = (ImageView) rootView.findViewById(R.id.btn_now_playing_prev);
        this.btnPlayPause = (ImageView) rootView.findViewById(R.id.btn_now_playing_play);
        this.btnNext = (ImageView) rootView.findViewById(R.id.btn_now_playing_next);
        this.btnStop = (ImageView) rootView.findViewById(R.id.btn_now_playing_stop);
        this.equalizerView = (EqualizerView) rootView.findViewById(R.id.equalizer_view);

        this.packageManager = context.getPackageManager();
        this.sessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        this.listenerComponent = new ComponentName(context, MediaNotificationListener.class);

        setupControls();
    }

    public void start() {
        try {
            android.util.Log.i("MediaWidget", "MediaWidgetManager start() called");
            if (sessionManager != null) {
                sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, listenerComponent, mainHandler);
            }
            findAndBindActiveSession();
        } catch (Throwable e) {
            android.util.Log.e("MediaWidget", "Error in start()", e);
            hideWidget();
        }
    }

    public void stop() {
        try {
            if (sessionManager != null) {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
            }
        } catch (Throwable ignored) {}
        if (pendingArtTask != null) {
            pendingArtTask.cancel(true);
        }
        if (equalizerView != null) {
            equalizerView.setPlaying(false);
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        if (tvTrackTitle instanceof com.appletv.launcher.widget.MarqueeTextView) {
            ((com.appletv.launcher.widget.MarqueeTextView) tvTrackTitle).stopMarquee();
        }
        if (ivAlbumArt != null) {
            ivAlbumArt.animate().cancel();
        }
        if (tvTrackTitle != null) {
            tvTrackTitle.animate().cancel();
        }
        if (tvArtistApp != null) {
            tvArtistApp.animate().cancel();
        }
        hasArtContent = false;
        lastSourceArt = null;
        lastArtResId = 0;
        lastTitle = null;
        lastArtistSub = null;
        unbindCurrentController();
    }

    private void setupControls() {
        if (containerArt != null) {
            containerArt.setClipToOutline(true);
            containerArt.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    float density = view.getContext().getResources().getDisplayMetrics().density;
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 40f * density);
                }
            });
            containerArt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (activeController == null) return;
                    try {
                        if (activeController.getSessionActivity() != null) {
                            activeController.getSessionActivity().send();
                        } else {
                            android.content.Intent launch = packageManager.getLaunchIntentForPackage(activeController.getPackageName());
                            if (launch != null) {
                                context.startActivity(launch);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
        }

        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (activeController == null) return;
                    try {
                        PlaybackState state = activeController.getPlaybackState();
                        if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                            activeController.getTransportControls().pause();
                        } else {
                            activeController.getTransportControls().play();
                        }
                    } catch (Throwable t) {
                        findAndBindActiveSession();
                    }
                }
            });
        }

        if (btnStop != null) {
            btnStop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (activeController == null) return;
                    try {
                        activeController.getTransportControls().stop();
                        if (equalizerView != null) {
                            equalizerView.setPlaying(false);
                        }
                    } catch (Throwable t) {
                        findAndBindActiveSession();
                    }
                }
            });
        }

        if (btnPrev != null) {
            btnPrev.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (activeController == null) return;
                    try {
                        activeController.getTransportControls().skipToPrevious();
                    } catch (Throwable t) {
                        findAndBindActiveSession();
                    }
                }
            });
        }

        if (btnNext != null) {
            btnNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (activeController == null) return;
                    try {
                        activeController.getTransportControls().skipToNext();
                    } catch (Throwable t) {
                        findAndBindActiveSession();
                    }
                }
            });
        }
    }

    private synchronized void findAndBindActiveSession() {
        if (sessionManager == null) {
            hideWidget();
            return;
        }

        try {
            List<MediaController> controllers = sessionManager.getActiveSessions(listenerComponent);
            android.util.Log.i("MediaWidget", "Controllers count: " + (controllers != null ? controllers.size() : "null"));
            if (controllers != null) {
                for (MediaController mc : controllers) {
                    PlaybackState ps = mc.getPlaybackState();
                    android.util.Log.i("MediaWidget", "Found session: " + mc.getPackageName() + " state: " + (ps != null ? ps.getState() : "null"));
                }
            }
            if (controllers == null || controllers.isEmpty()) {
                unbindCurrentController();
                hideWidget();
                return;
            }

            MediaController selected = null;
            // 1. Prioritize playing session
            for (MediaController mc : controllers) {
                try {
                    PlaybackState ps = mc.getPlaybackState();
                    if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                        selected = mc;
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            // 2. Fall back to paused/buffering session
            if (selected == null) {
                for (MediaController mc : controllers) {
                    try {
                        PlaybackState ps = mc.getPlaybackState();
                        if (ps != null && (ps.getState() == PlaybackState.STATE_PAUSED || ps.getState() == PlaybackState.STATE_BUFFERING)) {
                            selected = mc;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            if (selected == null) {
                unbindCurrentController();
                hideWidget();
                return;
            }

            if (activeController != null && activeController.getSessionToken().equals(selected.getSessionToken())) {
                try {
                    updatePlaybackState(activeController.getPlaybackState());
                    updateMetadata(activeController.getMetadata());
                } catch (Throwable ignored) {}
                return;
            }

            unbindCurrentController();
            activeController = selected;
            activeController.registerCallback(controllerCallback, mainHandler);
            updatePlaybackState(activeController.getPlaybackState());
            updateMetadata(activeController.getMetadata());

        } catch (Throwable t) {
            hideWidget();
        }
    }

    private void unbindCurrentController() {
        if (activeController != null) {
            try {
                activeController.unregisterCallback(controllerCallback);
            } catch (Throwable ignored) {}
            activeController = null;
        }
        hasArtContent = false;
        lastSourceArt = null;
        lastArtResId = 0;
        lastTitle = null;
        lastArtistSub = null;
    }

    private void updatePlaybackState(PlaybackState state) {
        if (state == null || container == null) {
            android.util.Log.w("MediaWidget", "updatePlaybackState: state or container is null (container=" + container + ")");
            if (equalizerView != null) equalizerView.setPlaying(false);
            hideWidget();
            return;
        }

        int s = state.getState();
        android.util.Log.i("MediaWidget", "updatePlaybackState: state=" + s);
        boolean isPlaying = (s == PlaybackState.STATE_PLAYING);
        if (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING) {
            showWidget();
            if (btnPlayPause != null) {
                if (isPlaying) {
                    btnPlayPause.setImageResource(R.drawable.ic_pause);
                } else {
                    btnPlayPause.setImageResource(R.drawable.ic_play);
                }
            }
            if (equalizerView != null) {
                equalizerView.setPlaying(isPlaying);
            }
        } else {
            if (equalizerView != null) equalizerView.setPlaying(false);
            hideWidget();
        }
    }

    private void updateMetadata(final MediaMetadata metadata) {
        if (metadata == null || activeController == null || container == null) return;

        try {
            String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null) {
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }

            String pkg = activeController.getPackageName();
            String appName = pkg;
            try {
                appName = packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString();
            } catch (Throwable ignored) {}

            String displayTitle = (title != null && !title.trim().isEmpty() ? title : "Unknown Title");
            String sub = "";
            if (artist != null && !artist.trim().isEmpty()) {
                sub = artist + " • ";
            }
            sub += appName;

            updateTrackText(displayTitle, sub);

            final String trackSig = (title != null ? title : "") + "|" + (artist != null ? artist : "") + "|" + pkg;
            currentTrackSignature = trackSig;

            // Extract album art bitmap from all possible MediaMetadata / MediaDescription keys
            Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) {
                art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            if (art == null && metadata.getDescription() != null) {
                art = metadata.getDescription().getIconBitmap();
            }

            if (art != null && !art.isRecycled()) {
                applyAlbumArt(art);
            } else {
                android.net.Uri iconUri = null;
                if (metadata.getDescription() != null) {
                    iconUri = metadata.getDescription().getIconUri();
                }
                if (iconUri == null) {
                    String uriStr = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
                    if (uriStr == null) uriStr = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);
                    if (uriStr != null) iconUri = android.net.Uri.parse(uriStr);
                }

                if (iconUri != null) {
                    final android.net.Uri targetUri = iconUri;
                    final String expectedSig = trackSig;

                    if (pendingArtTask != null && !pendingArtTask.isDone()) {
                        pendingArtTask.cancel(true);
                    }

                    pendingArtTask = artExecutor.submit(new Runnable() {
                        @Override
                        public void run() {
                            java.io.InputStream is = null;
                            try {
                                String scheme = targetUri.getScheme();
                                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(targetUri.toString()).openConnection();
                                    conn.setConnectTimeout(5000);
                                    conn.setReadTimeout(5000);
                                    conn.setInstanceFollowRedirects(true);
                                    conn.connect();
                                    is = conn.getInputStream();
                                } else {
                                    is = context.getContentResolver().openInputStream(targetUri);
                                }
                                if (is != null) {
                                    final Bitmap decoded = decodeSampledBitmap(is, 160, 160);
                                    if (decoded != null && expectedSig.equals(currentTrackSignature)) {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (expectedSig.equals(currentTrackSignature)) {
                                                    applyAlbumArt(decoded);
                                                }
                                            }
                                        });
                                    } else {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (expectedSig.equals(currentTrackSignature)) {
                                                    applyFallbackArt();
                                                }
                                            }
                                        });
                                    }
                                } else {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (expectedSig.equals(currentTrackSignature)) {
                                                applyFallbackArt();
                                            }
                                        }
                                    });
                                }
                            } catch (Throwable t) {
                                android.util.Log.w("MediaWidget", "Failed to load album art URI: " + targetUri, t);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (expectedSig.equals(currentTrackSignature)) {
                                            applyFallbackArt();
                                        }
                                    }
                                });
                            } finally {
                                if (is != null) {
                                    try { is.close(); } catch (Throwable ignored) {}
                                }
                            }
                        }
                    });
                } else {
                    applyFallbackArt();
                }
            }
        } catch (Throwable ignored) {}
    }

    private Bitmap decodeSampledBitmap(java.io.InputStream is, int reqWidth, int reqHeight) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            byte[] data = baos.toByteArray();

            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, options);

            int inSampleSize = 1;
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                final int halfHeight = options.outHeight / 2;
                final int halfWidth = options.outWidth / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            options.inSampleSize = inSampleSize;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            return android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Throwable t) {
            return null;
        }
    }

    private float dpToPx(float dp) {
        if (context == null) return dp;
        return dp * context.getResources().getDisplayMetrics().density;
    }

    private void updateTrackText(final String newTitle, final String newSubtitle) {
        if (newTitle == null && newSubtitle == null) return;

        final String safeTitle = newTitle != null ? newTitle : "Unknown Title";
        final String safeSubtitle = newSubtitle != null ? newSubtitle : "";

        boolean titleChanged = (lastTitle == null || !lastTitle.equals(safeTitle));
        boolean subtitleChanged = (lastArtistSub == null || !lastArtistSub.equals(safeSubtitle));

        if (!titleChanged && !subtitleChanged) {
            return;
        }

        boolean isFirstLoad = (lastTitle == null && lastArtistSub == null);
        lastTitle = safeTitle;
        lastArtistSub = safeSubtitle;

        if (isFirstLoad) {
            if (tvTrackTitle != null) {
                tvTrackTitle.animate().cancel();
                tvTrackTitle.setText(safeTitle);
                tvTrackTitle.setSelected(true);
                tvTrackTitle.setAlpha(1.0f);
                tvTrackTitle.setTranslationY(0f);
            }
            if (tvArtistApp != null) {
                tvArtistApp.animate().cancel();
                tvArtistApp.setText(safeSubtitle);
                tvArtistApp.setAlpha(1.0f);
                tvArtistApp.setTranslationY(0f);
            }
            return;
        }

        if (tvTrackTitle != null) {
            tvTrackTitle.animate().cancel();
        }
        if (tvArtistApp != null) {
            tvArtistApp.animate().cancel();
        }

        final float titleOutY = -dpToPx(4f);
        final float artistOutY = -dpToPx(3f);
        final float resetInY = dpToPx(4f);

        Runnable withEndAction = new Runnable() {
            @Override
            public void run() {
                if (tvTrackTitle != null) {
                    tvTrackTitle.setText(safeTitle);
                    tvTrackTitle.setSelected(true);
                    tvTrackTitle.setTranslationY(resetInY);
                    tvTrackTitle.animate()
                            .alpha(1.0f)
                            .translationY(0f)
                            .setDuration(200)
                            .setInterpolator(decelerateInterpolator)
                            .start();
                }
                if (tvArtistApp != null) {
                    tvArtistApp.setText(safeSubtitle);
                    tvArtistApp.setTranslationY(resetInY);
                    tvArtistApp.animate()
                            .alpha(1.0f)
                            .translationY(0f)
                            .setDuration(200)
                            .setInterpolator(decelerateInterpolator)
                            .start();
                }
            }
        };

        if (tvArtistApp != null) {
            if (tvTrackTitle != null) {
                tvTrackTitle.animate()
                        .alpha(0.0f)
                        .translationY(titleOutY)
                        .setDuration(120)
                        .start();
            }
            tvArtistApp.animate()
                    .alpha(0.0f)
                    .translationY(artistOutY)
                    .setDuration(120)
                    .withEndAction(withEndAction)
                    .start();
        } else if (tvTrackTitle != null) {
            tvTrackTitle.animate()
                    .alpha(0.0f)
                    .translationY(titleOutY)
                    .setDuration(120)
                    .withEndAction(withEndAction)
                    .start();
        }
    }

    private void applyAlbumArt(final Bitmap art) {
        if (art == null || art.isRecycled() || ivAlbumArt == null) return;

        if (lastArtResId == 0 && lastSourceArt != null) {
            if (art == lastSourceArt || (art.getWidth() == lastSourceArt.getWidth()
                    && art.getHeight() == lastSourceArt.getHeight()
                    && art.getGenerationId() == lastSourceArt.getGenerationId())) {
                return;
            }
        }

        lastSourceArt = art;
        lastArtResId = 0;

        Bitmap displayArt = art;
        if (art.getWidth() > 240 || art.getHeight() > 240) {
            try {
                displayArt = Bitmap.createScaledBitmap(art, 240, 240, true);
            } catch (Throwable ignored) {}
        }
        final Bitmap finalDisplayArt = displayArt;

        ivAlbumArt.animate().cancel();

        boolean isAlreadyVisible = hasArtContent && ivAlbumArt.getDrawable() != null && ivAlbumArt.getAlpha() > 0.05f;

        if (isAlreadyVisible) {
            ivAlbumArt.animate()
                    .alpha(0.0f)
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(140)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (ivAlbumArt == null) return;
                            ivAlbumArt.setImageBitmap(finalDisplayArt);
                            ivAlbumArt.animate()
                                    .alpha(1.0f)
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(220)
                                    .setInterpolator(decelerateInterpolator)
                                    .start();
                        }
                    })
                    .start();
        } else {
            ivAlbumArt.setImageBitmap(finalDisplayArt);
            ivAlbumArt.setScaleX(1.0f);
            ivAlbumArt.setScaleY(1.0f);
            ivAlbumArt.setAlpha(0.0f);
            ivAlbumArt.animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(220)
                    .setInterpolator(decelerateInterpolator)
                    .start();
        }
        hasArtContent = true;

        if (equalizerView != null) {
            equalizerView.setBarColor(0xFFFFFFFF);
        }
        int domColor = ColorExtractor.extractDominantColor(displayArt);
        if (colorListener != null && domColor != 0) {
            colorListener.onMediaColorExtracted(domColor);
        }
    }

    private void applyAlbumArtResource(final int resId) {
        if (ivAlbumArt == null) return;

        if (lastArtResId == resId && lastSourceArt == null) {
            return;
        }

        lastArtResId = resId;
        lastSourceArt = null;

        ivAlbumArt.animate().cancel();

        boolean isAlreadyVisible = hasArtContent && ivAlbumArt.getDrawable() != null && ivAlbumArt.getAlpha() > 0.05f;

        if (isAlreadyVisible) {
            ivAlbumArt.animate()
                    .alpha(0.0f)
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(140)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (ivAlbumArt == null) return;
                            ivAlbumArt.setImageResource(resId);
                            ivAlbumArt.animate()
                                    .alpha(1.0f)
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(220)
                                    .setInterpolator(decelerateInterpolator)
                                    .start();
                        }
                    })
                    .start();
        } else {
            ivAlbumArt.setImageResource(resId);
            ivAlbumArt.setScaleX(1.0f);
            ivAlbumArt.setScaleY(1.0f);
            ivAlbumArt.setAlpha(0.0f);
            ivAlbumArt.animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(220)
                    .setInterpolator(decelerateInterpolator)
                    .start();
        }
        hasArtContent = true;

        if (equalizerView != null) {
            equalizerView.setBarColor(0xFFFFFFFF);
        }
    }

    private void applyFallbackArt() {
        applyAlbumArtResource(R.drawable.ic_music_note);
    }

    private void showWidget() {
        android.util.Log.i("MediaWidget", "showWidget() called, container=" + container);
        if (container != null) {
            container.setVisibility(View.VISIBLE);
            container.setAlpha(1.0f);
        }
    }

    private void hideWidget() {
        android.util.Log.i("MediaWidget", "hideWidget() called");
        if (equalizerView != null) {
            equalizerView.setPlaying(false);
        }
        if (tvTrackTitle instanceof com.appletv.launcher.widget.MarqueeTextView) {
            ((com.appletv.launcher.widget.MarqueeTextView) tvTrackTitle).stopMarquee();
        }
        if (ivAlbumArt != null) {
            ivAlbumArt.animate().cancel();
            ivAlbumArt.setAlpha(1.0f);
            ivAlbumArt.setScaleX(1.0f);
            ivAlbumArt.setScaleY(1.0f);
        }
        if (tvTrackTitle != null) {
            tvTrackTitle.animate().cancel();
            tvTrackTitle.setAlpha(1.0f);
            tvTrackTitle.setTranslationY(0f);
        }
        if (tvArtistApp != null) {
            tvArtistApp.animate().cancel();
            tvArtistApp.setAlpha(1.0f);
            tvArtistApp.setTranslationY(0f);
        }
        hasArtContent = false;
        lastSourceArt = null;
        lastArtResId = 0;
        lastTitle = null;
        lastArtistSub = null;
        if (container != null) {
            if (container.hasFocus()) {
                View root = container.getRootView();
                if (root != null) {
                    View dock = root.findViewById(R.id.grid_dock);
                    if (dock instanceof android.view.ViewGroup && ((android.view.ViewGroup) dock).getChildCount() > 0) {
                        ((android.view.ViewGroup) dock).getChildAt(0).requestFocus();
                    } else if (dock != null) {
                        dock.requestFocus();
                    } else {
                        root.requestFocus();
                    }
                }
            }
            container.setVisibility(View.GONE);
        }
    }
}
