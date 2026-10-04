package com.combodrive.game;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.AudioFormat;
import android.media.AudioAttributes;
import android.util.Log;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.speech.tts.TextToSpeech;
import android.view.*;
import java.util.*;
import java.util.Locale;

public class MainActivity extends Activity {
    private ComboDriveView gameView;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(15, 23, 42));
        getWindow().setNavigationBarColor(Color.rgb(15, 23, 42));
        gameView = new ComboDriveView(this);
        setContentView(gameView);
    }

    @Override public void onBackPressed() {
        // Navigasi tombol Back Android mengikuti alur halaman game,
        // sehingga pemain tidak langsung keluar dari aplikasi.
        if (gameView != null && gameView.handleSystemBack()) return;
        super.onBackPressed();
    }
}

class ComboDriveView extends View {
    enum Screen { MENU, MATERIALS, LEVELS, VEHICLES, SETTINGS, PLAYING, RESULT, GAME_OVER }
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Random random = new Random();
    final Handler handler = new Handler();
    final SharedPreferences prefs;
    final Vibrator vibrator;
    final AudioManager audioManager;
    Bitmap correctMonkey, wrongMonkey;
    Bitmap refVehicles, refTracks, refEnvironment, refBackground, refGameplay, refMenuUi, refLevelComplete, refGameOverUi, refVfx, refSfx, refMusicHaptic, refExamples, refGarage;
    Bitmap[] vehicleImages = new Bitmap[3];

    Screen screen = Screen.MENU;
    int level = 1, question = 0, lives = 3, score = 0, coins = 0, vehicle = 0;
    boolean survivalMode = false;
    int a, b, correct;
    int[] currentAnswers = new int[3];
    int correctCount = 0;
    boolean[] usedMultipliers = new boolean[11];
    long deadline;
    float roadOffset = 0f;
    int lane = 1, obstacleLane = 0;
    float obstacleOffset = 0f;
    boolean paused = false, sound = true, music = true, ads = true;
    boolean answersReady = false;
    boolean showAnswerFeedback = false, feedbackCorrect = false;
    ToneGenerator toneGenerator;
    TextToSpeech voice;
    boolean voiceReady = false;
    AudioTrack musicTrack;
    Thread musicThread;
    volatile boolean musicRunning = false;

    final String[] vehicleNames = {"MOBIL", "MOTOR", "SEPEDA"};
    final int[] vehicleColors = {
        Color.rgb(37, 99, 235), Color.rgb(220, 38, 38), Color.rgb(22, 163, 74)
    };

    ComboDriveView(Context c) {
        super(c);
        setFocusable(true);
        text.setTypeface(Typeface.create("sans", Typeface.BOLD));
        prefs = c.getSharedPreferences("combo", Context.MODE_PRIVATE);
        coins = prefs.getInt("total_coins", 0);
        vibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        audioManager = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        correctMonkey = load(c, com.combodrive.game.R.drawable.monkey_correct);
        wrongMonkey = load(c, com.combodrive.game.R.drawable.monkey_wrong);
        refVehicles = load(c, R.drawable.ref_vehicles);
        refTracks = load(c, R.drawable.ref_tracks);
        refEnvironment = load(c, R.drawable.ref_environment);
        refBackground = load(c, R.drawable.ref_background);
        refGameplay = load(c, R.drawable.ref_gameplay);
        refMenuUi = load(c, R.drawable.ref_menu_ui);
        refLevelComplete = load(c, R.drawable.ref_level_complete);
        refGameOverUi = load(c, R.drawable.ref_game_over_ui);
        refVfx = load(c, R.drawable.ref_vfx);
        refSfx = load(c, R.drawable.ref_sfx);
        refMusicHaptic = load(c, R.drawable.ref_music_haptic);
        refExamples = load(c, R.drawable.ref_examples);
        refGarage = load(c, R.drawable.ref_garage);
        vehicleImages[0] = load(c, R.drawable.vehicle_car);
        vehicleImages[1] = load(c, R.drawable.vehicle_motor);
        vehicleImages[2] = load(c, R.drawable.vehicle_bicycle);
        sound = prefs.getBoolean("sound", true);
        music = prefs.getBoolean("music", true);
        ads = prefs.getBoolean("ads", true);
        AudioManager audioManager = this.audioManager;
        if (audioManager != null) {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                0
            );
        }
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        voice = new TextToSpeech(c.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS && voice != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    voice.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build());
                }
                int result = voice.setLanguage(new Locale("id", "ID"));
                voice.setSpeechRate(0.82f);
                voice.setPitch(1.0f);
                voiceReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
        if (music) startMusic();
        handler.post(tick);
    }

    Bitmap load(Context c, int id) {
        return BitmapFactory.decodeResource(c.getResources(), id);
    }
    /**
     * Menangani tombol Back fisik/sistem Android berdasarkan halaman aktif.
     * Mengembalikan true jika navigasi ditangani oleh game.
     */
    boolean handleSystemBack() {
        switch (screen) {
            case MATERIALS:
                screen = Screen.MENU;
                break;
            case LEVELS:
                screen = Screen.MATERIALS;
                break;
            case VEHICLES:
            case SETTINGS:
                screen = Screen.MENU;
                break;
            case PLAYING:
                paused = false;
                screen = Screen.LEVELS;
                break;
            case RESULT:
            case GAME_OVER:
                screen = Screen.MATERIALS;
                break;
            case MENU:
                return false;
        }
        invalidate();
        return true;
    }


    void playSound(int tone) {
        if (!sound || toneGenerator == null) return;
        toneGenerator.startTone(tone, 90);
    }

    // Suara klik yang seragam untuk seluruh tombol dan navigasi aplikasi.
    // Mengikuti suara tombol pada halaman awal.
    void playButtonSound() {
        playSound(ToneGenerator.TONE_PROP_BEEP);
    }

    void speakFeedback(String message) {
        if (!sound || !voiceReady || voice == null) return;
        // Hentikan ucapan sebelumnya agar feedback tidak menumpuk dan terasa lag.
        voice.stop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
            voice.speak(message, TextToSpeech.QUEUE_FLUSH, params, "combo_feedback");
        } else {
            voice.speak(message, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    void vibrate(long durationMs) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(durationMs);
        }
    }

    void feedbackCorrect() {
        playSound(ToneGenerator.TONE_PROP_ACK);
        vibrate(55);
    }

    void feedbackWrong() {
        playSound(ToneGenerator.TONE_PROP_NACK);
        vibrate(180);
    }

    void startMusic() {
        if (musicRunning || !music) return;
        musicRunning = true;
        final int sampleRate = 44100;
        final int seconds = 8;
        final short[] pcm = new short[sampleRate * seconds];

        // Generate a short, audible melody in memory and loop it with MODE_STATIC.
        double[] melody = {261.63, 329.63, 392.00, 329.63,
                           293.66, 349.23, 440.00, 349.23};
        int samplesPerNote = sampleRate / 2;
        for (int i = 0; i < pcm.length; i++) {
            int noteIndex = (i / samplesPerNote) % melody.length;
            double freq = melody[noteIndex];
            double local = (i % samplesPerNote) / (double) samplesPerNote;
            double envelope = Math.min(1.0, local * 25.0) *
                    Math.min(1.0, (1.0 - local) * 12.0);
            pcm[i] = (short)(Math.sin(2.0 * Math.PI * freq * i / sampleRate)
                    * 0.28 * Short.MAX_VALUE * envelope);
        }

        try {
            int min = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            musicTrack = new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, pcm.length * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();

            if (musicTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e("ComboDriveAudio", "AudioTrack tidak terinisialisasi");
                musicRunning = false;
                musicTrack.release();
                musicTrack = null;
                return;
            }

            int written = musicTrack.write(pcm, 0, pcm.length);
            if (written != pcm.length) {
                Log.e("ComboDriveAudio", "PCM gagal ditulis: " + written);
                musicRunning = false;
                musicTrack.release();
                musicTrack = null;
                return;
            }

            musicTrack.setVolume(1.0f);
            musicTrack.setLoopPoints(0, pcm.length, -1);
            musicTrack.play();
            Log.i("ComboDriveAudio", "Musik berhasil dimulai");
        } catch (Exception e) {
            Log.e("ComboDriveAudio", "Gagal menjalankan musik", e);
            musicRunning = false;
            if (musicTrack != null) {
                try { musicTrack.release(); } catch (Exception ignored) {}
                musicTrack = null;
            }
        }
    }

    void stopMusic() {
        musicRunning = false;
        if (musicTrack != null) {
            try { musicTrack.pause(); } catch (Exception ignored) {}
            try { musicTrack.flush(); } catch (Exception ignored) {}
            try { musicTrack.release(); } catch (Exception ignored) {}
            musicTrack = null;
        }
    }

    final Runnable tick = new Runnable() {
        public void run() {
            if (screen == Screen.PLAYING && !paused) {
                roadOffset = (roadOffset + 7f) % 80f;
                obstacleOffset = (obstacleOffset + 5f) % 620f;
                if (obstacleOffset < 8f) obstacleLane = random.nextInt(3);
                if (System.currentTimeMillis() >= deadline) answer(-1);
                invalidate();
            }
            handler.postDelayed(this, 40);
        }
    };

    float sx() { return getWidth() / 360f; }
    float sy() { return getHeight() / 800f; }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.save();
        c.scale(sx(), sy());
        c.drawColor(Color.rgb(241, 245, 249));
        switch (screen) {
            case MENU: drawMenu(c); break;
            case MATERIALS: drawMaterials(c); break;
            case LEVELS: drawLevels(c); break;
            case VEHICLES: drawVehicles(c); break;
            case SETTINGS: drawSettings(c); break;
            case PLAYING: drawPlaying(c); break;
            case RESULT: drawResult(c); break;
            case GAME_OVER: drawGameOver(c); break;
        }
        c.restore();
    }

    void label(Canvas c, String s, float x, float y, float size, int color, boolean center) {
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(center ? Paint.Align.CENTER : Paint.Align.LEFT);
        c.drawText(s, x, y, text);
    }

    void round(Canvas c, float l, float t, float r, float b, float radius, int color) {
        p.setColor(color);
        c.drawRoundRect(l, t, r, b, radius, radius, p);
    }

    void asset(Canvas c, Bitmap bitmap, float l, float t, float r, float b) {
        if (bitmap == null) return;
        p.setAlpha(255);
        c.drawBitmap(bitmap, null, new RectF(l, t, r, b), p);
    }

    // Kendaraan gameplay dikembalikan ke orientasi aset semula (menyamping).
    void assetFacingForward(Canvas c, Bitmap bitmap, float cx, float cy,
                            float l, float t, float r, float b) {
        if (bitmap == null) return;
        asset(c, bitmap, l, t, r, b);
    }

    void assetSoft(Canvas c, Bitmap bitmap, float l, float t, float r, float b, int alpha) {
        if (bitmap == null) return;
        p.setAlpha(alpha);
        c.drawBitmap(bitmap, null, new RectF(l, t, r, b), p);
        p.setAlpha(255);
    }

    void button(Canvas c, String s, float l, float t, float r, float b, int color) {
        round(c, l + 2, t + 4, r + 2, b + 4, 15, 0x22000000);
        round(c, l, t, r, b, 15, color);
        label(c, s, (l + r) / 2, (t + b) / 2 + 7, 14, Color.WHITE, true);
    }

    boolean hit(float x, float y, float l, float t, float r, float b) {
        return x >= l && x <= r && y >= t && y <= b;
    }

    void topBar(Canvas c, String title) {
        // Header diturunkan dari status bar dan diberi ruang yang cukup
        // antara judul header dengan konten putih di bawahnya.
        p.setColor(Color.rgb(15, 23, 42));
        c.drawRoundRect(0, 48, 360, 142, 0, 0, p);
        label(c, title, 180, 108, 23, Color.WHITE, true);
    }

    // Latar gameplay berbeda untuk setiap level agar permainan tidak monoton.
    // Survival memakai tema berganti berdasarkan nomor putaran soal.
    void drawLevelBackground(Canvas c) {
        int theme = survivalMode ? ((question / 10) % 10) + 1 : Math.max(1, Math.min(10, level));

        int[] skyTop = {
                Color.rgb(186, 230, 253), Color.rgb(191, 219, 254),
                Color.rgb(254, 215, 170), Color.rgb(254, 202, 202),
                Color.rgb(221, 214, 254), Color.rgb(199, 210, 254),
                Color.rgb(186, 230, 253), Color.rgb(187, 247, 208),
                Color.rgb(254, 240, 138), Color.rgb(203, 213, 225)
        };
        int[] skyBottom = {
                Color.rgb(224, 242, 254), Color.rgb(219, 234, 254),
                Color.rgb(254, 237, 213), Color.rgb(254, 226, 226),
                Color.rgb(237, 233, 254), Color.rgb(224, 231, 255),
                Color.rgb(207, 250, 254), Color.rgb(220, 252, 231),
                Color.rgb(254, 249, 195), Color.rgb(226, 232, 240)
        };
        int[] ground = {
                Color.rgb(34, 197, 94), Color.rgb(22, 163, 74),
                Color.rgb(217, 119, 6), Color.rgb(190, 24, 93),
                Color.rgb(124, 58, 237), Color.rgb(37, 99, 235),
                Color.rgb(8, 145, 178), Color.rgb(5, 150, 105),
                Color.rgb(132, 204, 22), Color.rgb(71, 85, 105)
        };

        int index = theme - 1;
        p.setShader(new LinearGradient(0, 0, 0, 800,
                skyTop[index], skyBottom[index], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 360, 800, p);
        p.setShader(null);

        p.setColor(ground[index]);
        c.drawRect(0, 340, 360, 800, p);

        // Dekorasi utama setiap tema dibuat berbeda.
        if (theme == 1 || theme == 7) {
            p.setColor(0x55FFFFFF);
            c.drawCircle(65, 112, 34, p);
            c.drawCircle(105, 105, 25, p);
            c.drawCircle(285, 132, 42, p);
        } else if (theme == 2 || theme == 6) {
            p.setColor(0x558B5E3C);
            Path mountain = new Path();
            mountain.moveTo(0, 340); mountain.lineTo(75, 220); mountain.lineTo(145, 340);
            mountain.lineTo(230, 190); mountain.lineTo(360, 340); mountain.close();
            c.drawPath(mountain, p);
        } else if (theme == 3 || theme == 9) {
            p.setColor(0x66FDE68A);
            c.drawCircle(285, 105, 38, p);
            for (int i = 0; i < 7; i++) {
                p.setColor(0x55FFFFFF);
                c.drawRect(12 + i * 52, 300 - (i % 3) * 20,
                        42 + i * 52, 340, p);
            }
        } else if (theme == 4 || theme == 5) {
            p.setColor(0x44FFFFFF);
            for (int i = 0; i < 5; i++) {
                c.drawCircle(35 + i * 78, 115 + (i % 2) * 35, 25, p);
            }
        } else if (theme == 8) {
            p.setColor(0x5599F6E4);
            c.drawRect(0, 295, 360, 340, p);
            p.setColor(0x55FFFFFF);
            for (int i = 0; i < 6; i++) {
                c.drawCircle(30 + i * 65, 120 + (i % 2) * 30, 20, p);
            }
        } else {
            p.setColor(0x44747569);
            for (int i = 0; i < 6; i++) {
                c.drawRect(8 + i * 62, 250 - (i % 2) * 35,
                        42 + i * 62, 340, p);
            }
        }
    }

    void drawBackground(Canvas c) {
        p.setShader(new LinearGradient(0, 0, 0, 800,
            Color.rgb(224, 242, 254), Color.rgb(191, 219, 254), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 360, 800, p);
        p.setShader(null);
        p.setColor(Color.rgb(34, 197, 94));
        c.drawRect(0, 340, 360, 800, p);
        p.setColor(0x33FFFFFF);
        c.drawCircle(55, 125, 38, p);
        c.drawCircle(95, 112, 28, p);
        c.drawCircle(292, 150, 45, p);
    }

    void drawCoinBadge(Canvas c, float centerX, float centerY) {
        // Badge koin berada di pojok kanan atas kartu putih, dengan jarak aman dari tepi.
        round(c, centerX - 48, centerY - 17, centerX + 48, centerY + 17,
                17, Color.rgb(255, 247, 214));
        p.setColor(Color.rgb(245, 158, 11));
        c.drawCircle(centerX - 29, centerY, 10, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(Color.rgb(180, 83, 9));
        c.drawCircle(centerX - 29, centerY, 10, p);
        p.setStyle(Paint.Style.FILL);
        label(c, "C", centerX - 29, centerY + 5, 11, Color.rgb(180, 83, 9), true);
        label(c, String.valueOf(coins), centerX + 10, centerY + 5, 14,
                Color.rgb(146, 64, 14), true);
    }

    void drawMenu(Canvas c) {
        drawBackground(c);

        // Kartu utama dengan margin yang lebih seimbang
        round(c, 14, 42, 346, 770, 28, 0xEEFFFFFF);

        // Header
        label(c, "MATH",
                180, 92, 19,
                Color.rgb(37, 99, 235), true);

        label(c, "DRIVE",
                180, 123, 30,
                Color.rgb(15, 23, 42), true);

        label(c, "Belajar Matematika sambil melaju!",
                180, 151, 11,
                Color.rgb(71, 85, 105), true);

        // Badge koin
        drawCoinBadge(c, 290, 68);

        // Maskot diberi jarak dari subtitle
        if (correctMonkey != null) {
            c.drawBitmap(
                    correctMonkey,
                    null,
                    new RectF(65, 170, 295, 325),
                    p
            );
        }

        // Tombol utama dengan jarak lebih rapi
        button(
                c,
                "MULAI BERMAIN",
                42, 390, 318, 445,
                Color.rgb(37, 99, 235)
        );

        button(
                c,
                "PILIH KENDARAAN",
                42, 463, 318, 518,
                Color.rgb(14, 116, 144)
        );

        button(
                c,
                "PENGATURAN",
                42, 536, 318, 591,
                Color.rgb(71, 85, 105)
        );

        // Misi harian diberi jarak dari tombol terakhir
        round(
                c,
                42, 630, 318, 716,
                18,
                Color.rgb(248, 250, 252)
        );

        label(
                c,
                "MISI HARI INI",
                180, 656, 11,
                Color.rgb(100, 116, 139), true
        );

        label(
                c,
                "Jadilah pengemudi matematika!",
                180, 684, 13,
                Color.rgb(15, 23, 42), true
        );

        // Footer
        label(
                c,
                "© Math Drive",
                180, 744, 10,
                Color.rgb(100, 116, 139), true
        );
    }

    void drawMaterials(Canvas c) {
        drawBackground(c);
        round(c, 14, 34, 346, 766, 28, 0xEEFFFFFF);

        label(c, "MATERI PEMBELAJARAN", 180, 78, 21, Color.rgb(15, 23, 42), true);
        label(c, "Pilih materi sebelum membuka level", 180, 105, 11,
                Color.rgb(71, 85, 105), true);

        // Materi yang tersedia: perkalian.
        round(c, 28, 132, 332, 285, 22, Color.rgb(239, 246, 255));
        round(c, 42, 147, 104, 251, 18, Color.rgb(37, 99, 235));
        label(c, "×", 73, 210, 42, Color.WHITE, true);
        label(c, "PERKALIAN", 122, 177, 17, Color.rgb(15, 23, 42), false);
        label(c, "Menghadapi Perkalian 1 Sampai 10", 122, 204, 10,
                Color.rgb(71, 85, 105), false);
        label(c, "TERSEDIA", 122, 235, 12, Color.rgb(22, 163, 74), false);
        button(c, "MULAI BERAKSI", 122, 247, 316, 277, Color.rgb(37, 99, 235));

        // Materi yang belum tersedia.
        String[] soonNames = {"PEMBAGIAN", "PENJUMLAHAN", "PENGURANGAN"};
        String[] soonSymbols = {"÷", "+", "−"};
        for (int i = 0; i < soonNames.length; i++) {
            float top = 305 + i * 100;
            round(c, 28, top, 332, top + 82, 18, Color.rgb(248, 250, 252));
            round(c, 42, top + 12, 94, top + 70, 14, Color.rgb(148, 163, 184));
            label(c, soonSymbols[i], 68, top + 49, 28, Color.WHITE, true);
            label(c, soonNames[i], 110, top + 31, 14, Color.rgb(15, 23, 42), false);
            label(c, "COMING SOON", 110, top + 56, 11, Color.rgb(100, 116, 139), false);
        }

        button(c, "KEMBALI", 70, 635, 290, 689, Color.rgb(71, 85, 105));
    }

    boolean allLevelsCompleted() {
        // Jika Level 1-10 sudah terbuka, Survival juga dianggap terbuka.
        // Flag completed_* tetap diperiksa untuk penyimpanan progres normal.
        if (prefs.getInt("unlocked", 1) >= 10) return true;

        for (int i = 1; i <= 10; i++) {
            if (!prefs.getBoolean("completed_" + i, false)) return false;
        }
        return true;
    }

    void drawLevels(Canvas c) {
        topBar(c, "PILIH LEVEL");
        label(c, "Selesaikan level untuk membuka level berikutnya", 180, 190, 11, Color.DKGRAY, true);
        int unlocked = prefs.getInt("unlocked", 1);

        for (int i = 1; i <= 10; i++) {
            int col = (i - 1) % 2, row = (i - 1) / 2;
            // Grid dibuat sedikit lebih rapat agar mode Survival bisa tampil
            // tepat di bawah Level 10 tanpa keluar dari layar.
            float l = 24 + col * 162, t = 210 + row * 84, r = l + 150, b = t + 62;
            float centerX = (l + r) / 2f;
            boolean open = i <= unlocked;

            round(c, l + 2, t + 4, r + 2, b + 4, 16, 0x22000000);
            round(c, l, t, r, b, 16,
                    open ? Color.rgb(37, 99, 235) : Color.rgb(148, 163, 184));
            label(c, "LEVEL " + i, centerX, t + 27, 14, Color.WHITE, true);
            label(c, open ? "× " + i : "TERKUNCI", centerX, t + 46, 10, 0xEEFFFFFF, true);

            String starKey = "stars_" + i;
            if (prefs.contains(starKey)) {
                int earnedStars = Math.max(0, Math.min(3, prefs.getInt(starKey, 0)));
                String starsText = "★".repeat(earnedStars) + "☆".repeat(3 - earnedStars);
                int starColor = earnedStars == 0
                        ? Color.rgb(148, 163, 184)
                        : Color.rgb(245, 158, 11);
                label(c, starsText, centerX, t + 75, 14, starColor, true);
            }
        }

        // Mode Survival hanya terbuka setelah Level 1-10 selesai.
        boolean survivalUnlocked = allLevelsCompleted();
        button(c, survivalUnlocked ? "MODE SURVIVAL" : "SURVIVAL • TERKUNCI",
                32, 632, 328, 684,
                survivalUnlocked ? Color.rgb(124, 58, 237) : Color.rgb(148, 163, 184));

        button(c, "KEMBALI", 70, 694, 290, 746, Color.rgb(71, 85, 105));
    }

    void drawVehicles(Canvas c) {
        topBar(c, "PILIH KENDARAAN");
        label(c, "Pilih kendaraan favoritmu", 180, 192, 13, Color.rgb(71, 85, 105), true);
        for (int i = 0; i < 3; i++) {
            float top = 218 + i * 155;
            boolean selected = vehicle == i;
            round(c, 20, top + 5, 340, top + 142, 22, 0x22000000);
            round(c, 16, top, 336, top + 137, 22, Color.WHITE);
            if (selected) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(5);
                p.setColor(vehicleColors[i]);
                c.drawRoundRect(16, top, 336, top + 137, 22, 22, p);
                p.setStyle(Paint.Style.FILL);
            }
            round(c, 30, top + 14, 148, top + 123, 18, Color.rgb(226, 232, 240));
            asset(c, vehicleImages[i], 34, top + 18, 144, top + 119);
            label(c, vehicleNames[i], 170, top + 55, 19, Color.rgb(15, 23, 42), false);
            label(c, i == 0 ? "Kendaraan cepat dan stabil" : i == 1 ? "Lincah melewati rintangan" : "Santai dan hemat energi", 170, top + 80, 10, Color.rgb(100, 116, 139), false);
            label(c, selected ? "DIPILIH ✓" : "PILIH", 170, top + 108, 13, vehicleColors[i], false);
        }
        button(c, "KEMBALI", 70, 704, 290, 758, Color.rgb(71, 85, 105));
    }

    void drawSettings(Canvas c) {
        topBar(c, "PENGATURAN");
        settingRow(c, "SUARA", sound, 190, Color.rgb(37, 99, 235));
        settingRow(c, "MUSIK", music, 315, Color.rgb(14, 116, 144));
        settingRow(c, "IKLAN", ads, 440, Color.rgb(71, 85, 105));
        button(c, "KEMBALI", 70, 650, 290, 704, Color.rgb(71, 85, 105));
    }

    void settingRow(Canvas c, String name, boolean enabled, float top, int color) {
        round(c, 28, top, 332, top + 91, 18, Color.WHITE);
        label(c, name, 47, top + 34, 14, Color.rgb(15, 23, 42), false);
        label(c, enabled ? "AKTIF" : "NONAKTIF", 47, top + 59, 11, Color.rgb(100, 116, 139), false);
        button(c, enabled ? "ON" : "OFF", 250, top + 23, 310, top + 65, color);
    }

    void drawVehicleIcon(Canvas c, int type, float cx, float cy, float scale) {
        int iconColor = Color.WHITE;
        p.setColor(iconColor);
        if (type == 0) { // mobil
            round(c, cx - 43 * scale, cy - 25 * scale, cx + 43 * scale, cy + 24 * scale, 12, vehicleColors[type]);
            round(c, cx - 27 * scale, cy - 19 * scale, cx + 27 * scale, cy - 2 * scale, 6, Color.WHITE);
            p.setColor(Color.rgb(15, 23, 42));
            c.drawCircle(cx - 29 * scale, cy + 25 * scale, 11 * scale, p);
            c.drawCircle(cx + 29 * scale, cy + 25 * scale, 11 * scale, p);
        } else if (type == 1) { // motor
            p.setStrokeWidth(8 * scale); p.setStyle(Paint.Style.STROKE); p.setColor(iconColor);
            c.drawLine(cx - 26 * scale, cy + 20 * scale, cx + 24 * scale, cy + 20 * scale, p);
            c.drawLine(cx - 4 * scale, cy + 20 * scale, cx + 10 * scale, cy - 15 * scale, p);
            c.drawLine(cx + 10 * scale, cy - 15 * scale, cx + 28 * scale, cy + 20 * scale, p);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(15, 23, 42));
            c.drawCircle(cx - 28 * scale, cy + 22 * scale, 10 * scale, p); c.drawCircle(cx + 28 * scale, cy + 22 * scale, 10 * scale, p);
            round(c, cx - 9 * scale, cy - 25 * scale, cx + 17 * scale, cy - 10 * scale, 6, vehicleColors[type]);
        } else { // sepeda
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5 * scale); p.setColor(iconColor);
            c.drawCircle(cx - 27 * scale, cy + 20 * scale, 15 * scale, p); c.drawCircle(cx + 27 * scale, cy + 20 * scale, 15 * scale, p);
            c.drawLine(cx - 27 * scale, cy + 20 * scale, cx - 5 * scale, cy - 4 * scale, p); c.drawLine(cx - 5 * scale, cy - 4 * scale, cx + 27 * scale, cy + 20 * scale, p); c.drawLine(cx - 5 * scale, cy - 4 * scale, cx + 8 * scale, cy + 20 * scale, p); c.drawLine(cx + 8 * scale, cy + 20 * scale, cx - 27 * scale, cy + 20 * scale, p);
            p.setStyle(Paint.Style.FILL); p.setColor(iconColor); c.drawCircle(cx - 5 * scale, cy - 4 * scale, 5 * scale, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    void drawEnvironmentDecor(Canvas c) {
        // Setiap level mempunyai dekorasi lingkungan yang berbeda.
        // Pada Survival, tema berputar kembali setiap 10 soal.
        int theme = survivalMode
                ? ((question / 10) % 10) + 1
                : Math.max(1, Math.min(10, level));

        for (int i = 0; i < 4; i++) {
            float y = 145 + i * 175 - (roadOffset * 1.5f) % 175;
            float leftX = 30;
            float rightX = 330;

            if (theme == 1) {
                // Hutan hijau.
                drawTree(c, leftX, y, 1.0f);
                drawTree(c, rightX, y + 32, 0.9f);

            } else if (theme == 2) {
                // Pegunungan dan pohon pinus.
                drawMountain(c, leftX + 8, y + 45, 42, 78);
                drawMountain(c, rightX - 8, y + 18, 48, 92);
                drawPine(c, leftX, y + 78, 1.0f);
                drawPine(c, rightX, y + 62, 0.85f);

            } else if (theme == 3) {
                // Gurun dengan kaktus.
                drawCactus(c, leftX, y + 32, 1.0f);
                drawCactus(c, rightX, y + 8, 0.85f);
                p.setColor(0x66FDE68A);
                c.drawCircle(leftX + 18, y + 5, 17, p);

            } else if (theme == 4) {
                // Kota dengan gedung-gedung.
                drawBuilding(c, leftX, y + 12, 38, 115);
                drawBuilding(c, rightX, y + 35, 42, 92);

            } else if (theme == 5) {
                // Hutan musim gugur.
                drawAutumnTree(c, leftX, y + 8, 1.0f);
                drawAutumnTree(c, rightX, y + 35, 0.9f);

            } else if (theme == 6) {
                // Pegunungan bersalju.
                drawMountain(c, leftX + 6, y + 55, 55, 105);
                drawMountain(c, rightX - 8, y + 28, 48, 90);
                p.setColor(0xCCFFFFFF);
                Path snow = new Path();
                snow.moveTo(leftX - 25, y + 30);
                snow.lineTo(leftX + 6, y - 18);
                snow.lineTo(leftX + 36, y + 30);
                snow.close();
                c.drawPath(snow, p);

            } else if (theme == 7) {
                // Pantai dengan pohon kelapa.
                drawPalm(c, leftX, y + 8, 1.0f);
                drawPalm(c, rightX, y + 28, 0.9f);

            } else if (theme == 8) {
                // Taman bambu.
                drawBamboo(c, leftX, y + 5, 1.0f);
                drawBamboo(c, rightX, y + 25, 0.9f);

            } else if (theme == 9) {
                // Padang bunga.
                drawFlowerPatch(c, leftX, y + 30);
                drawFlowerPatch(c, rightX, y + 8);

            } else {
                // Kota malam dengan lampu.
                drawBuilding(c, leftX, y + 20, 40, 108);
                drawBuilding(c, rightX, y + 4, 45, 125);
                p.setColor(Color.rgb(250, 204, 21));
                c.drawCircle(leftX + 4, y + 44, 3, p);
                c.drawCircle(leftX + 4, y + 65, 3, p);
                c.drawCircle(rightX - 5, y + 34, 3, p);
                c.drawCircle(rightX - 5, y + 55, 3, p);
            }
        }
    }

    void drawTree(Canvas c, float x, float y, float scale) {
        p.setColor(Color.rgb(120, 84, 45));
        c.drawRect(x - 4 * scale, y + 22 * scale,
                x + 4 * scale, y + 58 * scale, p);
        p.setColor(Color.rgb(34, 139, 70));
        c.drawCircle(x, y + 12 * scale, 25 * scale, p);
        p.setColor(Color.rgb(74, 170, 70));
        c.drawCircle(x - 15 * scale, y + 20 * scale, 18 * scale, p);
        c.drawCircle(x + 15 * scale, y + 20 * scale, 18 * scale, p);
    }

    void drawPine(Canvas c, float x, float y, float scale) {
        p.setColor(Color.rgb(101, 67, 33));
        c.drawRect(x - 3 * scale, y + 12 * scale,
                x + 3 * scale, y + 48 * scale, p);
        p.setColor(Color.rgb(22, 101, 52));
        Path pine = new Path();
        pine.moveTo(x, y - 28 * scale);
        pine.lineTo(x - 27 * scale, y + 25 * scale);
        pine.lineTo(x + 27 * scale, y + 25 * scale);
        pine.close();
        c.drawPath(pine, p);
        Path pine2 = new Path();
        pine2.moveTo(x, y - 5 * scale);
        pine2.lineTo(x - 34 * scale, y + 43 * scale);
        pine2.lineTo(x + 34 * scale, y + 43 * scale);
        pine2.close();
        c.drawPath(pine2, p);
    }

    void drawMountain(Canvas c, float x, float y, float width, float height) {
        p.setColor(0x665B6475);
        Path mountain = new Path();
        mountain.moveTo(x - width, y + height);
        mountain.lineTo(x, y - height);
        mountain.lineTo(x + width, y + height);
        mountain.close();
        c.drawPath(mountain, p);
    }

    void drawCactus(Canvas c, float x, float y, float scale) {
        p.setColor(Color.rgb(22, 163, 74));
        round(c, x - 7 * scale, y, x + 7 * scale,
                y + 64 * scale, 7 * scale, Color.rgb(22, 163, 74));
        round(c, x - 27 * scale, y + 18 * scale,
                x - 8 * scale, y + 30 * scale, 6 * scale, Color.rgb(22, 163, 74));
        round(c, x - 27 * scale, y + 8 * scale,
                x - 15 * scale, y + 24 * scale, 6 * scale, Color.rgb(22, 163, 74));
        round(c, x + 8 * scale, y + 35 * scale,
                x + 27 * scale, y + 47 * scale, 6 * scale, Color.rgb(22, 163, 74));
        round(c, x + 15 * scale, y + 25 * scale,
                x + 27 * scale, y + 42 * scale, 6 * scale, Color.rgb(22, 163, 74));
    }

    void drawBuilding(Canvas c, float x, float y, float width, float height) {
        p.setColor(0x88515B75);
        c.drawRect(x - width / 2, y, x + width / 2, y + height, p);
        p.setColor(0xCCFDE68A);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                float wx = x - width / 2 + 9 + col * (width / 2 - 3);
                float wy = y + 12 + row * 22;
                c.drawRect(wx, wy, wx + 7, wy + 10, p);
            }
        }
    }

    void drawAutumnTree(Canvas c, float x, float y, float scale) {
        p.setColor(Color.rgb(120, 84, 45));
        c.drawRect(x - 4 * scale, y + 20 * scale,
                x + 4 * scale, y + 62 * scale, p);
        p.setColor(Color.rgb(234, 88, 12));
        c.drawCircle(x, y + 8 * scale, 24 * scale, p);
        p.setColor(Color.rgb(245, 158, 11));
        c.drawCircle(x - 18 * scale, y + 20 * scale, 15 * scale, p);
        c.drawCircle(x + 17 * scale, y + 19 * scale, 16 * scale, p);
    }

    void drawPalm(Canvas c, float x, float y, float scale) {
        p.setColor(Color.rgb(146, 93, 52));
        p.setStrokeWidth(6 * scale);
        c.drawLine(x, y + 65 * scale, x - 5 * scale, y - 5 * scale, p);
        p.setColor(Color.rgb(22, 163, 74));
        p.setStrokeWidth(4 * scale);
        for (int i = 0; i < 5; i++) {
            double angle = -2.7 + i * 0.65;
            c.drawLine(x - 5 * scale, y - 5 * scale,
                    x - 5 * scale + (float) Math.cos(angle) * 32 * scale,
                    y - 5 * scale + (float) Math.sin(angle) * 25 * scale, p);
        }
    }

    void drawBamboo(Canvas c, float x, float y, float scale) {
        p.setStrokeWidth(5 * scale);
        p.setColor(Color.rgb(21, 128, 61));
        for (int i = -1; i <= 1; i++) {
            float bx = x + i * 13 * scale;
            c.drawLine(bx, y + 65 * scale, bx + 3 * scale,
                    y - 18 * scale, p);
            p.setColor(Color.rgb(134, 239, 172));
            for (int j = 0; j < 4; j++) {
                c.drawRect(bx - 4, y + 5 + j * 17,
                        bx + 5, y + 9 + j * 17, p);
            }
            p.setColor(Color.rgb(21, 128, 61));
        }
    }

    void drawFlowerPatch(Canvas c, float x, float y) {
        p.setStrokeWidth(2);
        for (int i = 0; i < 5; i++) {
            float fx = x - 22 + i * 11;
            float fy = y + (i % 2) * 10;
            p.setColor(Color.rgb(22, 163, 74));
            c.drawLine(fx, fy + 18, fx, fy, p);
            p.setColor(i % 2 == 0 ? Color.rgb(244, 63, 94) : Color.rgb(168, 85, 247));
            c.drawCircle(fx, fy, 5, p);
            p.setColor(Color.rgb(250, 204, 21));
            c.drawCircle(fx, fy, 2, p);
        }
    }

    void drawPlaying(Canvas c) {
        drawLevelBackground(c);

        // Batasi lingkungan dan jalan agar tidak pernah melewati area header.
        c.save();
        c.clipRect(0, 150, 360, 800);
        drawEnvironmentDecor(c);
        p.setColor(Color.rgb(71, 85, 105));
        Path road = new Path();
        road.moveTo(68, 82); road.lineTo(292, 82); road.lineTo(360, 800); road.lineTo(0, 800); road.close();
        c.drawPath(road, p);

        // Dua garis pembatas sehingga jalan benar-benar memiliki 3 lajur:
        // kiri, tengah, dan kanan. Garis mengikuti perspektif jalan.
        p.setColor(0xCCFFFFFF);
        for (int y = -80 + (int) roadOffset; y < 800; y += 105) {
            float progress = Math.max(0f, Math.min(1f, (y - 82f) / 718f));
            float leftEdge = 68f + (0f - 68f) * progress;
            float rightEdge = 292f + (360f - 292f) * progress;
            float laneOne = leftEdge + (rightEdge - leftEdge) / 3f;
            float laneTwo = leftEdge + 2f * (rightEdge - leftEdge) / 3f;
            float nextY = Math.min(800f, y + 52f);
            float nextProgress = Math.max(0f, Math.min(1f, (nextY - 82f) / 718f));
            float nextLeft = 68f + (0f - 68f) * nextProgress;
            float nextRight = 292f + (360f - 292f) * nextProgress;
            float nextLaneOne = nextLeft + (nextRight - nextLeft) / 3f;
            float nextLaneTwo = nextLeft + 2f * (nextRight - nextLeft) / 3f;
            p.setStrokeWidth(4f);
            c.drawLine(laneOne, y, nextLaneOne, nextY, p);
            c.drawLine(laneTwo, y, nextLaneTwo, nextY, p);
        }
        c.restore();

        // Header dipisahkan dari status bar dan area permainan.
        round(c, 0, 52, 360, 150, 0, Color.rgb(15, 23, 42));
        label(c, survivalMode ? "SURVIVAL  •  SOAL " + (question + 1) : "LEVEL " + level + "  •  SOAL " + (question + 1) + "/10", 18, 78, 11, Color.WHITE, false);
        label(c, "♥".repeat(lives) + "♡".repeat(3 - lives), 135, 126, 18, Color.rgb(248, 113, 113), true);
        label(c, "🪙 " + coins, 255, 126, 15, Color.rgb(250, 204, 21), true);
        // Tombol kembali ke halaman level berada di samping tombol pause.
        button(c, "←", 265, 60, 303, 98, Color.rgb(30, 41, 59));
        button(c, paused ? "▶" : "Ⅱ", 309, 60, 347, 98, Color.rgb(30, 41, 59));

        // Garis progres hanya ditampilkan pada level biasa.
        // Mode Survival tidak memiliki batas soal, sehingga garis progres dihilangkan.
        if (!survivalMode) {
            round(c, 18, 158, 342, 168, 5, 0x55334155);
            round(c, 18, 158, 18 + 324 * Math.min(1f, question / 10f), 168, 5, Color.rgb(250, 204, 21));
        }

        // Rintangan visual dihilangkan sesuai permintaan.
        // Jalan tetap memiliki 3 lajur dan kotak jawaban tetap tampil.

        // Panel soal mengikuti lebar jalan agar pemandangan kiri/kanan tetap terlihat.
        round(c, 74, 188, 286, 350, 18, Color.WHITE);
        label(c, a + " × " + b + " = ?", 180, 246, 31, Color.rgb(15, 23, 42), true);
        label(c, "WAKTU: " + Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000) + " DETIK",
            180, 284, 12, Color.rgb(220, 38, 38), true);
        label(c, "Pilih jawaban", 180, 318, 11, Color.rgb(100, 116, 139), true);

        // Tiga kotak jawaban mengikuti lebar masing-masing lajur jalan.
        // Dengan begitu kotak tidak melebar ke pemandangan kiri/kanan.
        float answerY = 371f;
        float answerBottom = 426f;
        float roadProgress = Math.max(0f, Math.min(1f, (answerY - 82f) / 718f));
        float roadLeft = 68f + (0f - 68f) * roadProgress;
        float roadRight = 292f + (360f - 292f) * roadProgress;
        float laneWidthAtAnswer = (roadRight - roadLeft) / 3f;
        for (int i = 0; i < 3; i++) {
            float l = roadLeft + i * laneWidthAtAnswer + 3f;
            float r = roadLeft + (i + 1) * laneWidthAtAnswer - 3f;
            button(c, "" + currentAnswers[i], l, answerY, r, answerBottom,
                    Color.rgb(37, 99, 235));
        }

        // Lebar dasar jalan untuk posisi kendaraan pada 3 lajur.
        // Nilai ini dipakai untuk menghitung pusat lajur kiri, tengah, dan kanan.
        float laneWidth = 120f;
        float cx = (lane + 0.5f) * laneWidth, cy = 692;
        if (vehicle >= 0 && vehicle < vehicleImages.length && vehicleImages[vehicle] != null) {
            assetFacingForward(c, vehicleImages[vehicle], cx, cy, cx - 58, cy - 42, cx + 58, cy + 42);
        } else {
            drawVehicleIcon(c, vehicle, cx, cy, 1.0f);
        }


        // Feedback diberikan melalui suara dan getaran, tanpa pop-up. 

        if (paused) {
            p.setColor(0xAA000000);
            c.drawRect(0, 0, 360, 800, p);
            round(c, 40, 260, 320, 455, 24, Color.WHITE);
            label(c, "GAME DIJEDA", 180, 335, 24, Color.rgb(15, 23, 42), true);
            button(c, "LANJUT", 70, 365, 290, 419, Color.rgb(22, 163, 74));
        }
    }

    // Aturan bintang berdasarkan SKOR (10 poin untuk setiap jawaban benar):
    // 100 = 3 bintang, 70-90 = 2 bintang, 60 = 1 bintang, 0-50 = 0 bintang.
    int calculateStarsFromScore(int totalScore) {
        // 100 poin = 3 bintang
        // 70-90 poin = 2 bintang
        // 60 poin = 1 bintang
        // Di bawah 60 poin = 0 bintang
        if (totalScore >= 100) return 3;
        if (totalScore >= 70) return 2;
        if (totalScore >= 60) return 1;
        return 0;
    }

    void drawStarRules(Canvas c, float startY) {
        label(c, "PERATURAN GAME", 180, startY, 10,
                Color.rgb(71, 85, 105), true);
        label(c, "10 benar = 3 ★   |   9-7 benar = 2 ★", 180, startY + 17, 9,
                Color.rgb(71, 85, 105), true);
        label(c, "6 benar = 1 ★   |   5-0 benar = 0 ★", 180, startY + 32, 9,
                Color.rgb(71, 85, 105), true);
        label(c, "Minimal 1 ★ untuk lanjut ke level berikutnya", 180, startY + 49, 9,
                Color.rgb(220, 38, 38), true);
    }

    void saveCurrentLevelStars() {
        int completedStars = calculateStarsFromScore(score);
        prefs.edit().putInt("stars_" + level, completedStars).apply();
    }

    void drawResult(Canvas c) {
        topBar(c, "LEVEL SELESAI!");
        label(c, "Hebat! Kamu berhasil!", 180, 184, 22, Color.rgb(22, 163, 74), true);
        label(c, "LEVEL " + level, 180, 226, 30, Color.rgb(15, 23, 42), true);
        label(c, "SKOR: " + score, 180, 265, 20, Color.rgb(37, 99, 235), true);
        int levelCoins = correctCount * 10;
        label(c, "KOIN LEVEL INI: " + levelCoins, 180, 293, 17, Color.rgb(217, 119, 6), true);
        int stars = calculateStarsFromScore(score);
        saveCurrentLevelStars();
        label(c, "BINTANG: " + "★".repeat(stars) + "☆".repeat(3 - stars), 180, 335, 24, Color.rgb(245, 158, 11), true);
        label(c, correctCount + "/10 jawaban benar", 180, 365, 13, Color.rgb(71, 85, 105), true);
        drawStarRules(c, 389);
        // Maskot diturunkan agar tidak menempel dengan tulisan keterangan.
        if (correctMonkey != null) {
            c.drawBitmap(
                    correctMonkey,
                    null,
                    new RectF(72, 455, 288, 545),
                    p
            );
        }

        // Tombol diturunkan dan diberi jarak dari maskot.
        button(
                c,
                "ULANGI LAGI",
                42, 575, 318, 631,
                Color.rgb(37, 99, 235)
        );

        if (stars >= 1 && level < 10) {
            button(
                    c,
                    "LEVEL BERIKUTNYA",
                    42, 645, 318, 701,
                    Color.rgb(22, 163, 74)
            );
        } else {
            button(
                    c,
                    "MATERI PEMBELAJARAN",
                    42, 645, 318, 701,
                    Color.rgb(71, 85, 105)
            );
        }
    }


    void drawGameOver(Canvas c) {
        topBar(c, "GAME OVER");

        if (survivalMode) {
            // Tampilan khusus Mode Survival
            label(c, "SURVIVAL SELESAI", 180, 184, 24,
                    Color.rgb(220, 38, 38), true);

            label(c, "SKOR: " + score, 180, 230, 22,
                    Color.rgb(15, 23, 42), true);

            label(c, "KOIN DIDAPAT: " + (correctCount * 10), 180, 265, 18,
                    Color.rgb(217, 119, 6), true);

            label(c, "JAWABAN BENAR: " + correctCount, 180, 300, 15,
                    Color.rgb(71, 85, 105), true);

            label(c, "Faktor perkalian 1–10 secara acak", 180, 345, 11,
                    Color.rgb(71, 85, 105), true);

            label(c, "Soal tidak terbatas sampai nyawa habis", 180, 365, 10,
                    Color.rgb(71, 85, 105), true);

            if (wrongMonkey != null) {
                c.drawBitmap(
                        wrongMonkey,
                        null,
                        new RectF(72, 405, 288, 520),
                        p
                );
            }

            button(c, "ULANGI SURVIVAL",
                    42, 560, 318, 616,
                    Color.rgb(37, 99, 235));

            button(c, "MATERI PEMBELAJARAN",
                    42, 630, 318, 686,
                    Color.rgb(71, 85, 105));

            return;
        }

        // Tampilan Game Over untuk level biasa
        saveCurrentLevelStars();

        int gameOverStars = calculateStarsFromScore(score);
        boolean eligibleToContinue =
                gameOverStars >= 1 &&
                        correctCount >= 6 &&
                        score >= 60;

        if (eligibleToContinue) {
            label(c, "Hebat! Kamu berhasil!", 180, 184, 22,
                    Color.rgb(22, 163, 74), true);
        } else {
            label(c, "Jangan menyerah!", 180, 184, 25,
                    Color.rgb(220, 38, 38), true);
        }

        label(c, "SKOR: " + score, 180, 229, 20,
                Color.rgb(15, 23, 42), true);

        label(c, "KOIN LEVEL INI: " + (correctCount * 10),
                180, 259, 17,
                Color.rgb(217, 119, 6), true);

        label(c, "BINTANG: " +
                        "★".repeat(gameOverStars) +
                        "☆".repeat(3 - gameOverStars),
                180, 293, 21,
                Color.rgb(245, 158, 11), true);

        label(c,
                eligibleToContinue
                        ? "Level berikutnya terbuka: minimal 6 benar (skor 60)"
                        : "Minimal 6 jawaban benar (skor 60) untuk membuka level",
                180, 315, 9,
                Color.rgb(71, 85, 105), true);

        drawStarRules(c, 338);

        Bitmap resultMonkey =
                eligibleToContinue ? correctMonkey : wrongMonkey;

        if (resultMonkey != null) {
            c.drawBitmap(
                    resultMonkey,
                    null,
                    new RectF(72, 405, 288, 520),
                    p
            );
        }

        if (eligibleToContinue && level < 10) {
            button(c, "ULANGI LAGI",
                    42, 560, 318, 616,
                    Color.rgb(37, 99, 235));

            button(c, "LEVEL BERIKUTNYA",
                    42, 630, 318, 686,
                    Color.rgb(22, 163, 74));
        } else {
            button(c, "COBA LAGI",
                    42, 560, 318, 616,
                    Color.rgb(37, 99, 235));

            button(c, "MATERI PEMBELAJARAN",
                    42, 630, 318, 686,
                    Color.rgb(71, 85, 105));
        }
    }

    void newQuestion() {
        // Survival menggunakan soal perkalian 1-10 tanpa batas.
        // Setelah semua faktor terpakai, daftar diulang kembali.
        boolean anyUnused = false;
        for (int i = 1; i <= 10; i++) {
            if (!usedMultipliers[i]) {
                anyUnused = true;
                break;
            }
        }
        if (!anyUnused) Arrays.fill(usedMultipliers, false);

        int candidate;
        do {
            candidate = 1 + random.nextInt(10);
        } while (usedMultipliers[candidate]);
        usedMultipliers[candidate] = true;

        a = candidate;
        b = survivalMode ? (1 + random.nextInt(10)) : level;
        correct = a * b;

        // Pengecoh selalu merupakan hasil perkalian level yang sama,
        // bukan angka acak dari luar materi. Posisi pilihan tetap kiri-tengah-kanan.
        int factor = survivalMode ? b : level;
        int d1 = correct - factor;
        int d2 = correct + factor;
        if (d1 <= 0) d1 = correct + (2 * factor);
        if (d2 == correct || d2 == d1) d2 = correct + (3 * factor);
        int[] answers = new int[]{correct, d1, d2};
        // Urutan lajur berubah, tetapi semua pengecoh tetap kelipatan dari level soal.
        for (int i = answers.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = answers[i]; answers[i] = answers[j]; answers[j] = tmp;
        }
        currentAnswers = answers;
        answersReady = true;
        deadline = System.currentTimeMillis() + 5000;
        invalidate();
    }

    void startLevel(int lv) {
        survivalMode = false;
        level = lv; question = 0; lives = 3; score = 0; correctCount = 0; lane = 1;
        Arrays.fill(usedMultipliers, false);
        obstacleOffset = 0; paused = false; screen = Screen.PLAYING;
        newQuestion();
    }

    void startSurvival() {
        if (!allLevelsCompleted()) return;
        survivalMode = true;
        level = 0;
        question = 0;
        lives = 3;
        score = 0;
        correctCount = 0;
        lane = 1;
        Arrays.fill(usedMultipliers, false);
        obstacleOffset = 0;
        paused = false;
        screen = Screen.PLAYING;
        newQuestion();
    }

    // Pembukaan level berikutnya: minimal 6 jawaban benar (skor minimal 60 / 1 bintang).
    // Pembukaan disimpan segera setelah syarat tercapai, termasuk jika pemain kemudian Game Over.
    void unlockNextLevelIfEligible() {
        if (correctCount >= 6 && score >= 60 && level < 10) {
            int unlocked = prefs.getInt("unlocked", 1);
            if (level >= unlocked) {
                prefs.edit().putInt("unlocked", level + 1).apply();
            }
        }
    }

    void answer(int value) {
        if (paused || screen != Screen.PLAYING || showAnswerFeedback) return;
        boolean timeout = System.currentTimeMillis() >= deadline;
        boolean isCorrect = !timeout && value == correct;

        if (timeout) {
            // Waktu habis = nyawa berkurang 1
            lives--;
            feedbackWrong();
            speakFeedback("Waktu habis!");

        } else if (isCorrect) {
            // Jawaban benar
            correctCount++;
            score += 10;
            coins += 10;

            prefs.edit().putInt("total_coins", coins).apply();
            feedbackCorrect();
            speakFeedback("Benar!");

        } else {
            // Jawaban salah = nyawa berkurang 1
            lives--;
            feedbackWrong();
            speakFeedback("Salah!");
        }

        // Buka level berikutnya segera setelah minimal 6 jawaban benar (skor 60).
        unlockNextLevelIfEligible();

        // Kendaraan berpindah ke lajur pilihan yang ditekan.
        if (!timeout && isCorrect) {
            for (int i = 0; i < currentAnswers.length; i++) {
                if (currentAnswers[i] == correct) {
                    lane = i;
                    break;
                }
            }
        }

        if (lives <= 0) {
            screen = Screen.GAME_OVER;
            invalidate();
            return;
        }

        question++;
        if (!survivalMode && question >= 10) {
            int stars = calculateStarsFromScore(score);
            // Tandai level benar-benar selesai agar Survival dapat dibuka
            // setelah seluruh Level 1-10 diselesaikan.
            prefs.edit().putBoolean("completed_" + level, true).apply();
            unlockNextLevelIfEligible();
            screen = Screen.RESULT;
            invalidate();
            return;
        }

        // Survival tidak memiliki batas 10 soal; permainan terus berjalan
        // sampai nyawa habis.
        newQuestion();
    }


    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float x = e.getX() / sx(), y = e.getY() / sy();

        if (screen == Screen.MENU) {
            if (hit(x, y, 42, 420, 318, 474)) {
                playButtonSound();
                screen = Screen.MATERIALS;
            } else if (hit(x, y, 42, 487, 318, 541)) {
                playButtonSound();
                screen = Screen.VEHICLES;
            } else if (hit(x, y, 42, 554, 318, 608)) {
                playButtonSound();
                screen = Screen.SETTINGS;
            }
        } else if (screen == Screen.MATERIALS) {
            if (hit(x, y, 70, 635, 290, 689)) {
                playButtonSound();
                screen = Screen.MENU;
            } else if (hit(x, y, 122, 247, 316, 277)) {
                playButtonSound();
                // Hanya materi perkalian yang aktif saat ini.
                // Level 1 selalu menjadi level awal; level berikutnya mengikuti progres.
                screen = Screen.LEVELS;
            }
        } else if (screen == Screen.LEVELS) {
            if (hit(x, y, 70, 694, 290, 746)) {
                playButtonSound();
                screen = Screen.MATERIALS;
            } else if (hit(x, y, 32, 632, 328, 684) && allLevelsCompleted()) {
                playButtonSound();
                startSurvival();
            } else {
                int col = x < 180 ? 0 : 1;
                int row = (int) ((y - 210) / 84);
                int lv = row * 2 + col + 1;
                if (lv >= 1 && lv <= 10 && lv <= prefs.getInt("unlocked", 1)) {
                    playButtonSound();
                    startLevel(lv);
                }
            }
        } else if (screen == Screen.VEHICLES) {
            if (hit(x, y, 70, 690, 290, 744)) {
                playButtonSound();
                screen = Screen.MENU;
            }
            else for (int i = 0; i < 3; i++) {
                float top = 218 + i * 155;
                if (hit(x, y, 16, top, 336, top + 137)) {
                    vehicle = i; playButtonSound(); screen = Screen.MENU; break;
                }
            }
        } else if (screen == Screen.SETTINGS) {
            if (hit(x, y, 28, 172, 332, 263)) {
                playSound(ToneGenerator.TONE_PROP_BEEP);
                sound = !sound;
                if (sound) playSound(ToneGenerator.TONE_PROP_BEEP);
            } else if (hit(x, y, 28, 292, 332, 383)) {
                playButtonSound();
                music = !music;
                if (music) startMusic(); else stopMusic();
                playSound(ToneGenerator.TONE_PROP_BEEP);
            } else if (hit(x, y, 28, 412, 332, 503)) {
                playButtonSound();
                ads = !ads;
                playSound(ToneGenerator.TONE_PROP_BEEP);
            } else if (hit(x, y, 70, 620, 290, 674)) {
                playButtonSound();
                screen = Screen.MENU;
            }
            prefs.edit().putBoolean("sound", sound).putBoolean("music", music).putBoolean("ads", ads).apply();
        } else if (screen == Screen.PLAYING) {
            if (hit(x, y, 265, 64, 303, 102)) {
                playButtonSound();
                paused = false;
                screen = Screen.LEVELS;
            } else if (hit(x, y, 309, 64, 347, 102)) {
                playButtonSound();
                paused = !paused;
            } else if (paused && hit(x, y, 70, 365, 290, 419)) {
                paused = false;
            }
            else if (!paused && y > 640 && x < 120) lane = Math.max(0, lane - 1);
            else if (!paused && y > 640 && x > 240) lane = Math.min(2, lane + 1);
            else if (!paused) {
                // Area sentuh dibuat sama persis dengan posisi dan lebar kotak jawaban.
                float answerY = 371f;
                float roadProgress = Math.max(0f, Math.min(1f, (answerY - 82f) / 718f));
                float roadLeft = 68f + (0f - 68f) * roadProgress;
                float roadRight = 292f + (360f - 292f) * roadProgress;
                float laneWidthAtAnswer = (roadRight - roadLeft) / 3f;
                for (int i = 0; i < 3; i++) {
                    float l = roadLeft + i * laneWidthAtAnswer + 3f;
                    float r = roadLeft + (i + 1) * laneWidthAtAnswer - 3f;
                    if (hit(x, y, l, answerY, r, 430f)) {
                        answer(currentAnswers[i]);
                        break;
                    }
                }
            }
        } else if (screen == Screen.RESULT) {
            int resultStars = calculateStarsFromScore(score);
            if (hit(x, y, 42, 575, 318, 631)) {
                playButtonSound();
                // Ulangi level yang baru selesai.
                startLevel(level);

            } else if (
                    resultStars >= 1 &&
                            level < 10 &&
                            hit(x, y, 42, 645, 318, 701)
            ) {
                playButtonSound();
                // Lanjut ke level berikutnya.
                unlockNextLevelIfEligible();
                startLevel(level + 1);

            } else if (hit(x, y, 42, 645, 318, 701)) {
                playButtonSound();
                // Kembali ke materi pembelajaran.
                screen = Screen.MATERIALS;
            }
        } else if (screen == Screen.GAME_OVER) {
            if (survivalMode) {
                if (hit(x, y, 42, 560, 318, 616)) {
                    playButtonSound();
                    startSurvival();
                } else if (hit(x, y, 42, 630, 318, 686)) {
                    playButtonSound();
                    screen = Screen.MATERIALS;
                }
            } else {
                int gameOverStars = calculateStarsFromScore(score);
                if (gameOverStars >= 1 && level < 10 && hit(x, y, 42, 560, 318, 616)) {
                    playButtonSound();
                    // Ulangi level dari layar Game Over.
                    startLevel(level);
                } else if (gameOverStars >= 1 && level < 10 && hit(x, y, 42, 630, 318, 686)) {
                    playButtonSound();
                    unlockNextLevelIfEligible();
                    startLevel(level + 1);
                } else if (hit(x, y, 42, 560, 318, 616)) {
                    playButtonSound();
                    startLevel(level);
                } else if (hit(x, y, 42, 630, 318, 686)) {
                    playButtonSound();
                    screen = Screen.MATERIALS;
                }
            }
        }
        invalidate();
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        stopMusic();
        if (toneGenerator != null) {
            try { toneGenerator.release(); } catch (Exception ignored) {}
            toneGenerator = null;
        }
        if (voice != null) {
            try { voice.stop(); } catch (Exception ignored) {}
            try { voice.shutdown(); } catch (Exception ignored) {}
            voice = null;
            voiceReady = false;
        }
        super.onDetachedFromWindow();
    }
}
