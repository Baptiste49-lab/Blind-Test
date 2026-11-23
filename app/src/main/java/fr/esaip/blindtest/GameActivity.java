package fr.esaip.blindtest;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import fr.esaip.blindtest.models.Difficulty;

public class GameActivity extends AppCompatActivity {

    private static final int SPEECH_REQUEST_CODE = 100;
    private static final int MIC_PERMISSION_CODE = 101;

    // Paramètres de la partie
    private Difficulty currentMode = Difficulty.FACILE;
    private String currentTheme = "Pop";
    private boolean isArtistSearch = false;
    private String guessMode = "TITLE"; // "TITLE" ou "ARTIST"
    private int maxTracks = 10;

    // État de la partie en cours
    private int currentTrackIndex = 0;
    private int goodAnswers = 0;
    private int score = 0;
    private int combo = 0;
    private int maxCombo = 0;
    private int jokersRemaining = 2;
    private boolean jokerUsedOnCurrentTrack = false;
    private long startTime;
    private boolean isAnsweringAllowed = false;

    // Liste des morceaux joués (envoyée à l'écran de bilan)
    private final ArrayList<String> playedTracksSummary = new ArrayList<>();

    // Composants de l'interface
    private TextView tvProgress, tvCombo, tvTimer, tvScore, tvStatus, tvHint;
    private ProgressBar progressBarLoading;
    private ImageView ivAlbumCover;
    private MaterialCardView cardStatus;
    private MaterialButton btnJoker;
    private LinearLayout layoutQCM, layoutInput;
    private ImageButton btnMic;
    private EditText etAnswer;
    private final MaterialButton[] btnReps = new MaterialButton[4];

    // Outils réseau, audio et temporisation
    private RequestQueue requestQueue;
    private MediaPlayer mediaPlayer;
    private CountDownTimer timer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Track> trackList = new ArrayList<>();
    private Track currentTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Désactivation de l'animation d'ouverture
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // Liaison des vues avec le layout XML
        tvProgress = findViewById(R.id.tvProgress);
        tvCombo = findViewById(R.id.tvCombo);
        tvTimer = findViewById(R.id.tvTimer);
        tvScore = findViewById(R.id.tvScore);
        tvStatus = findViewById(R.id.tvStatus);
        tvHint = findViewById(R.id.tvHint);
        progressBarLoading = findViewById(R.id.progressBarLoading);
        ivAlbumCover = findViewById(R.id.ivAlbumCover);
        cardStatus = findViewById(R.id.cardStatus);
        btnJoker = findViewById(R.id.btnJoker);
        layoutQCM = findViewById(R.id.layoutQCM);
        layoutInput = findViewById(R.id.layoutInput);
        btnMic = findViewById(R.id.btnMic);
        etAnswer = findViewById(R.id.etAnswer);

        btnReps[0] = findViewById(R.id.btnRep1);
        btnReps[1] = findViewById(R.id.btnRep2);
        btnReps[2] = findViewById(R.id.btnRep3);
        btnReps[3] = findViewById(R.id.btnRep4);

        // Récupération des paramètres envoyés par ConfigActivity ou ResultActivity
        Intent intent = getIntent();
        if (intent.hasExtra("DIFFICULTY_MODE")) {
            currentMode = Difficulty.valueOf(intent.getStringExtra("DIFFICULTY_MODE"));
        }
        if (intent.hasExtra("GENRE")) {
            currentTheme = intent.getStringExtra("GENRE");
        }
        isArtistSearch = intent.getBooleanExtra("IS_ARTIST_SEARCH", false);
        if (intent.hasExtra("GUESS_MODE")) {
            String mode = intent.getStringExtra("GUESS_MODE");
            if (mode != null && !mode.isEmpty()) {
                guessMode = mode;
            }
        }
        if (intent.hasExtra("TRACK_COUNT")) {
            maxTracks = intent.getIntExtra("TRACK_COUNT", 10);
        }

        // Adaptation du texte d'aide dans le champ de saisie
        etAnswer.setHint(guessMode.equals("ARTIST") ? "Nom de l'artiste..." : "Nom du titre...");

        // Initialisation de Volley, du lecteur audio, du micro et du bouton Joker
        requestQueue = Volley.newRequestQueue(this);
        setupMediaPlayer();
        setupMicButton();
        btnJoker.setOnClickListener(v -> useJoker());

        // Validation par la touche Entrée du clavier en mode Difficile
        etAnswer.setOnEditorActionListener((v, actionId, event) -> {
            if (isAnsweringAllowed) {
                checkTextAnswer(etAnswer.getText().toString());
            }
            return true;
        });

        // Lancement de la récupération des pistes sur l'API Deezer
        fetchDeezerTracks();
    }

    /**
     * Initialise le lecteur audio MediaPlayer pour la lecture en streaming.
     */
    private void setupMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
    }

    /**
     * Interroge l'API Deezer via Volley avec dédoublonnage des titres (studio/live/remix).
     */
    private void fetchDeezerTracks() {
        trackList.clear();

        if (currentTheme == null || currentTheme.trim().isEmpty()) {
            currentTheme = "Pop";
        }

        String queryParam;
        try {
            queryParam = URLEncoder.encode(currentTheme.trim(), StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            queryParam = currentTheme.trim().replace(" ", "%20");
        }

        String url = "https://api.deezer.com/search?q=" + queryParam + "&limit=50";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray data = response.optJSONArray("data");
                        if (data == null || data.length() == 0) {
                            Toast.makeText(this, "Aucun résultat trouvé sur Deezer pour : " + currentTheme, Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }

                        Set<String> seenTitles = new HashSet<>();

                        for (int i = 0; i < data.length(); i++) {
                            JSONObject trackObj = data.getJSONObject(i);
                            String title = trackObj.optString("title_short", trackObj.optString("title", ""));
                            String preview = trackObj.optString("preview", "");
                            String deezerLink = trackObj.optString("link", "");

                            String artistName = "Inconnu";
                            if (trackObj.has("artist") && !trackObj.isNull("artist")) {
                                artistName = trackObj.getJSONObject("artist").optString("name", "Inconnu");
                            }

                            String coverUrl = "";
                            if (trackObj.has("album") && !trackObj.isNull("album")) {
                                coverUrl = trackObj.getJSONObject("album").optString("cover_medium", "");
                            }

                            // Anti-doublon sécurisé
                            String normalizedKey = normalizeForDedup(title);
                            if (normalizedKey.isEmpty()) {
                                normalizedKey = title.toLowerCase(Locale.ROOT);
                            }

                            if (!title.isEmpty() && !preview.isEmpty() && !preview.equals("null") && !seenTitles.contains(normalizedKey)) {
                                seenTitles.add(normalizedKey);
                                trackList.add(new Track(title, artistName, preview, coverUrl, deezerLink));
                            }
                        }

                        if (trackList.size() < 4) {
                            Toast.makeText(this, "Seulement " + trackList.size() + " extrait(s) disponible(s) pour : " + currentTheme, Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }

                        Collections.shuffle(trackList);
                        progressBarLoading.setVisibility(View.GONE);
                        btnJoker.setVisibility(View.VISIBLE);
                        setupGameMode();
                        startNewTrack();
                    } catch (Exception e) {
                        Toast.makeText(this, "Erreur de lecture des données Deezer.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                },
                error -> {
                    Toast.makeText(this, "Erreur réseau : impossible de joindre Deezer.", Toast.LENGTH_LONG).show();
                    finish();
                }
        );

        requestQueue.add(request);
    }

    /**
     * Nettoie un titre pour éviter les doublons (ex: "Titre (Live)" == "Titre").
     */
    private String normalizeForDedup(String title) {
        if (title == null) return "";
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\[.*?\\]", "")
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }

    /**
     * Retourne la bonne réponse attendue pour une piste (Titre ou Artiste).
     */
    private String getExpectedAnswer(Track track) {
        return guessMode.equals("ARTIST") ? track.artist : track.title;
    }

    /**
     * Affiche le QCM ou le champ de saisie selon la difficulté sélectionnée.
     */
    private void setupGameMode() {
        if (currentMode == Difficulty.DIFFICILE) {
            layoutQCM.setVisibility(View.GONE);
            layoutInput.setVisibility(View.VISIBLE);
        } else {
            layoutQCM.setVisibility(View.VISIBLE);
            layoutInput.setVisibility(View.GONE);

            View.OnClickListener qcmListener = v -> {
                if (!isAnsweringAllowed) return;
                MaterialButton clickedBtn = (MaterialButton) v;
                boolean isCorrect = clickedBtn.getText().toString().equalsIgnoreCase(getExpectedAnswer(currentTrack));
                handleAnswer(isCorrect, clickedBtn);
            };

            for (MaterialButton b : btnReps) {
                b.setOnClickListener(qcmListener);
            }
        }
    }

    /**
     * Prépare et lance la piste suivante, ou ouvre l'écran de bilan en fin de partie.
     */
    private void startNewTrack() {
        int totalToPlay = Math.min(maxTracks, trackList.size());

        if (currentTrackIndex >= totalToPlay) {
            Intent resultIntent = new Intent(GameActivity.this, ResultActivity.class);
            resultIntent.putExtra("SCORE", score);
            resultIntent.putExtra("GOOD_ANSWERS", goodAnswers);
            resultIntent.putExtra("TOTAL_TRACKS", totalToPlay);
            resultIntent.putExtra("MAX_COMBO", maxCombo);
            resultIntent.putExtra("GENRE", currentTheme);
            resultIntent.putExtra("DIFFICULTY_MODE", currentMode.name());
            resultIntent.putExtra("IS_ARTIST_SEARCH", isArtistSearch);
            resultIntent.putExtra("GUESS_MODE", guessMode);
            resultIntent.putStringArrayListExtra("PLAYED_TRACKS", playedTracksSummary);
            resultIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(resultIntent);
            overridePendingTransition(0, 0);
            finish();
            return;
        }

        currentTrack = trackList.get(currentTrackIndex);
        jokerUsedOnCurrentTrack = false;
        tvHint.setVisibility(View.GONE);
        tvHint.setText("");
        tvProgress.setText("Piste " + (currentTrackIndex + 1) + " / " + totalToPlay);
        tvStatus.setText(guessMode.equals("ARTIST") ? "Devinez l'artiste !" : "Devinez le titre !");
        etAnswer.setText("");
        isAnsweringAllowed = false;

        // Applique le style normal ou "On Fire" selon le combo en cours
        applyComboTheme();

        // Génération des 4 choix uniques pour le QCM + réinitialisation des bordures
        if (currentMode != Difficulty.DIFFICILE) {
            List<String> choices = new ArrayList<>();
            choices.add(getExpectedAnswer(currentTrack));
            int attempts = 0;
            while (choices.size() < 4 && attempts < 100) {
                String candidate = getExpectedAnswer(trackList.get((int) (Math.random() * trackList.size())));
                if (!choices.contains(candidate)) {
                    choices.add(candidate);
                }
                attempts++;
            }
            Collections.shuffle(choices);
            for (int i = 0; i < 4; i++) {
                btnReps[i].setText(i < choices.size() ? choices.get(i) : "---");
                btnReps[i].setStrokeColor(ColorStateList.valueOf(Color.parseColor("#30363D")));
                btnReps[i].setStrokeWidth(dpToPx(1));
            }
        }

        // Affichage de la pochette d'album uniquement en mode Facile
        if (currentMode == Difficulty.FACILE && !currentTrack.coverUrl.isEmpty()) {
            ivAlbumCover.setVisibility(View.VISIBLE);
            ivAlbumCover.setImageDrawable(null);
            loadAlbumCover(currentTrack.coverUrl);
        } else {
            ivAlbumCover.setVisibility(View.GONE);
        }

        playMusic(currentTrack.previewUrl);
    }

    /**
     * Utilise un Joker pour interroger Wikipedia de manière ciblée selon le mode (Titre ou Artiste).
     */
    private void useJoker() {
        if (!isAnsweringAllowed || jokersRemaining <= 0 || jokerUsedOnCurrentTrack) {
            return;
        }

        jokersRemaining--;
        jokerUsedOnCurrentTrack = true;
        btnJoker.setText("Indice Wikipedia (" + jokersRemaining + " restant" + (jokersRemaining > 1 ? "s" : "") + ")");
        if (jokersRemaining == 0) {
            btnJoker.setEnabled(false);
            btnJoker.setAlpha(0.4f);
        }

        tvHint.setVisibility(View.VISIBLE);
        tvHint.setText("Recherche d'un indice pertinent...");

        if (guessMode.equals("ARTIST")) {
            fetchWikipediaHint(currentTrack.artist + " musique", false);
        } else {
            fetchWikipediaHint(currentTrack.title + " " + currentTrack.artist + " chanson", true);
        }
    }

    /**
     * Récupère les 2 premières phrases propres de l'article Wikipedia le plus pertinent
     * et masque automatiquement la réponse attendue.
     */
    private void fetchWikipediaHint(String searchQuery, boolean allowFallbackToArtist) {
        try {
            String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.name());
            String wikiUrl = "https://fr.wikipedia.org/w/api.php?action=query"
                    + "&generator=search&gsrsearch=" + encodedQuery
                    + "&gsrlimit=1&prop=extracts&exintro=1&explaintext=1&exsentences=2"
                    + "&format=json&utf8=1";

            JsonObjectRequest wikiRequest = new JsonObjectRequest(Request.Method.GET, wikiUrl, null,
                    response -> {
                        try {
                            JSONObject queryObj = response.optJSONObject("query");
                            JSONObject pages = (queryObj != null) ? queryObj.optJSONObject("pages") : null;

                            if (pages == null || pages.length() == 0) {
                                handleWikipediaFallback(allowFallbackToArtist);
                                return;
                            }

                            String firstKey = pages.keys().next();
                            JSONObject page = pages.getJSONObject(firstKey);
                            String extract = page.optString("extract", "").trim();

                            if (extract.isEmpty()) {
                                handleWikipediaFallback(allowFallbackToArtist);
                                return;
                            }

                            String maskedExtract = maskAnswerInHint(extract);
                            if (maskedExtract.length() > 240) {
                                maskedExtract = maskedExtract.substring(0, 240) + "...";
                            }

                            tvHint.setText("Indice : " + maskedExtract);
                        } catch (Exception e) {
                            handleWikipediaFallback(allowFallbackToArtist);
                        }
                    },
                    error -> tvHint.setText("Impossible de contacter Wikipedia.")
            );
            requestQueue.add(wikiRequest);
        } catch (Exception e) {
            tvHint.setText("Indice indisponible.");
        }
    }

    /**
     * Gère le repli vers la fiche de l'artiste si la chanson n'a pas d'article Wikipedia dédié.
     */
    private void handleWikipediaFallback(boolean allowFallbackToArtist) {
        if (allowFallbackToArtist) {
            fetchWikipediaHint(currentTrack.artist + " musique", false);
        } else {
            String fallback = guessMode.equals("ARTIST")
                    ? "Cet artiste est présent dans la sélection " + currentTheme + "."
                    : "Ce morceau est interprété par " + currentTrack.artist + ".";
            tvHint.setText("Indice : " + fallback);
        }
    }

    /**
     * Masque le titre de la chanson (et l'artiste si on doit le deviner) dans le texte de l'indice.
     */
    private String maskAnswerInHint(String text) {
        String result = text;
        if (currentTrack.title != null && !currentTrack.title.isEmpty()) {
            result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(currentTrack.title), "[TITRE]");
        }
        if (guessMode.equals("ARTIST") && currentTrack.artist != null && !currentTrack.artist.isEmpty()) {
            result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(currentTrack.artist), "[ARTISTE]");
        }
        return result;
    }

    /**
     * Télécharge et affiche la pochette d'album via Volley.
     */
    private void loadAlbumCover(String imageUrl) {
        ImageRequest imageRequest = new ImageRequest(
                imageUrl,
                bitmap -> ivAlbumCover.setImageBitmap(bitmap),
                0,
                0,
                ImageView.ScaleType.CENTER_CROP,
                Bitmap.Config.RGB_565,
                error -> ivAlbumCover.setVisibility(View.GONE)
        );
        requestQueue.add(imageRequest);
    }

    /**
     * Charge et joue l'extrait MP3 de manière asynchrone.
     */
    private void playMusic(String url) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                startTime = System.currentTimeMillis();
                isAnsweringAllowed = true;
                startTimer();
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                handleAnswer(false);
                return true;
            });
        } catch (Exception e) {
            handleAnswer(false);
        }
    }

    /**
     * Gère le chrono et la coupure audio : 30s (Facile, sans défaite auto), 15s (Moyen), 5s Flash (Difficile).
     */
    private void startTimer() {
        if (timer != null) timer.cancel();

        long durationMs;
        if (currentMode == Difficulty.FACILE) {
            durationMs = 30000;
        } else if (currentMode == Difficulty.MOYEN) {
            durationMs = 15000;
        } else {
            durationMs = 5000;
        }

        timer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText((millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0s");
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }

                if (currentMode == Difficulty.FACILE) {
                    tvStatus.setText("Extrait terminé — Choisissez votre réponse !");
                } else if (isAnsweringAllowed) {
                    handleAnswer(false);
                }
            }
        }.start();
    }

    /**
     * Vérifie la réponse saisie ou dictée avec tolérance aux fautes (Levenshtein).
     */
    private void checkTextAnswer(String input) {
        String cleanInput = input.trim().toLowerCase(Locale.ROOT).replaceAll("[éèêë]", "e");
        String cleanExpected = getExpectedAnswer(currentTrack).trim().toLowerCase(Locale.ROOT).replaceAll("[éèêë]", "e");
        boolean isCorrect = calculateLevenshteinDistance(cleanInput, cleanExpected) <= 2;
        handleAnswer(isCorrect);
    }

    /**
     * Surcharge pour les réponses sans bouton QCM (mode Difficile, timer écoulé, erreur audio).
     */
    private void handleAnswer(boolean isCorrect) {
        handleAnswer(isCorrect, null);
    }

    /**
     * Traite la réponse, applique les encadrés vert/rouge et passe à la piste suivante.
     */
    private void handleAnswer(boolean isCorrect, @Nullable MaterialButton clickedBtn) {
        isAnsweringAllowed = false;
        if (timer != null) timer.cancel();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }

        playedTracksSummary.add(currentTrack.title + " - " + currentTrack.artist + "|" + currentTrack.deezerLink);
        currentTrackIndex++;

        long delayMs;
        int greenColor = Color.parseColor("#4CAF50");
        int redColor = Color.parseColor("#F85149");

        if (isCorrect) {
            goodAnswers++;
            combo++;
            if (combo > maxCombo) maxCombo = combo;

            long timeTakenSec = (System.currentTimeMillis() - startTime) / 1000;
            int basePoints = Math.max(100, 1000 - (int) timeTakenSec * 50);

            double multiplier = 1.0;
            if (combo >= 5) {
                multiplier = 2.0;
            } else if (combo >= 3) {
                multiplier = 1.5;
            }
            score += (int) (basePoints * multiplier);

            // Encadré vert sur la carte centrale
            cardStatus.setCardBackgroundColor(Color.parseColor("#132A1A"));
            cardStatus.setStrokeColor(greenColor);
            cardStatus.setStrokeWidth(dpToPx(3));

            // Encadré vert sur le bouton cliqué
            if (clickedBtn != null) {
                clickedBtn.setStrokeColor(ColorStateList.valueOf(greenColor));
                clickedBtn.setStrokeWidth(dpToPx(3));
            }

            tvStatus.setText(combo >= 3 ? "Bonne réponse ! ON FIRE (x" + multiplier + ")" : "Bonne réponse !");
            delayMs = 2000;
        } else {
            combo = 0;

            // Encadré rouge sur la carte centrale
            cardStatus.setCardBackgroundColor(Color.parseColor("#2D1416"));
            cardStatus.setStrokeColor(redColor);
            cardStatus.setStrokeWidth(dpToPx(3));

            // Encadré rouge sur la mauvaise réponse cliquée
            if (clickedBtn != null) {
                clickedBtn.setStrokeColor(ColorStateList.valueOf(redColor));
                clickedBtn.setStrokeWidth(dpToPx(3));
            }

            // Encadré vert sur le bouton qui contenait la bonne réponse (en mode QCM)
            if (currentMode != Difficulty.DIFFICILE) {
                String expected = getExpectedAnswer(currentTrack);
                for (MaterialButton b : btnReps) {
                    if (b.getText().toString().equalsIgnoreCase(expected)) {
                        b.setStrokeColor(ColorStateList.valueOf(greenColor));
                        b.setStrokeWidth(dpToPx(3));
                    }
                }
            }

            tvStatus.setText("C'était : " + currentTrack.title + "\npar " + currentTrack.artist);

            if (!currentTrack.coverUrl.isEmpty()) {
                ivAlbumCover.setVisibility(View.VISIBLE);
                loadAlbumCover(currentTrack.coverUrl);
            }
            delayMs = 3000;
        }

        tvScore.setText("Score : " + score);
        handler.postDelayed(this::startNewTrack, delayMs);
    }

    /**
     * Active l'effet visuel "On Fire" lorsque le combo atteint 3 ou plus.
     */
    private void applyComboTheme() {
        cardStatus.setStrokeWidth(dpToPx(2));
        if (combo >= 3) {
            tvCombo.setVisibility(View.VISIBLE);
            tvCombo.setText("ON FIRE x" + (combo >= 5 ? "2" : "1.5") + " (" + combo + ")");
            cardStatus.setCardBackgroundColor(Color.parseColor("#2A1B0E"));
            cardStatus.setStrokeColor(Color.parseColor("#FF9800"));
        } else {
            tvCombo.setVisibility(View.GONE);
            cardStatus.setCardBackgroundColor(Color.parseColor("#161B22"));
            cardStatus.setStrokeColor(Color.parseColor("#30363D"));
        }
    }

    /**
     * Configure le bouton micro avec vérification de la permission RECORD_AUDIO.
     */
    private void setupMicButton() {
        btnMic.setOnClickListener(v -> {
            if (!isAnsweringAllowed) return;

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MIC_PERMISSION_CODE);
            } else {
                launchSpeechRecognizer();
            }
        });
    }

    /**
     * Ouvre l'interface native de reconnaissance vocale Android.
     */
    private void launchSpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Reconnaissance vocale non supportée sur cet appareil.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchSpeechRecognizer();
        } else {
            Toast.makeText(this, "Permission micro refusée.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && isAnsweringAllowed) {
                etAnswer.setText(results.get(0));
                checkTextAnswer(results.get(0));
            }
        }
    }

    /**
     * Calcule la distance de Levenshtein entre deux chaînes pour tolérer les fautes de frappe.
     */
    private int calculateLevenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                            dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1),
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1)
                    );
                }
            }
        }
        return dp[a.length()][b.length()];
    }

    /**
     * Convertit une valeur en dp en pixels.
     */
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (timer != null) timer.cancel();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (timer != null) timer.cancel();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (requestQueue != null) {
            requestQueue.cancelAll(tag -> true);
        }
    }

    /**
     * Modèle interne représentant une piste musicale récupérée depuis Deezer.
     */
    private static class Track {
        String title;
        String artist;
        String previewUrl;
        String coverUrl;
        String deezerLink;

        Track(String title, String artist, String previewUrl, String coverUrl, String deezerLink) {
            this.title = title;
            this.artist = artist;
            this.previewUrl = previewUrl;
            this.coverUrl = coverUrl;
            this.deezerLink = deezerLink;
        }
    }
}