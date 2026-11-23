package fr.esaip.blindtest;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ResultActivity extends AppCompatActivity {

    // Composants de l'interface
    private TextView tvResultDetails, tvFinalScore, tvGoodAnswers, tvMaxCombo, tvSaveStatus;
    private Button btnLeaderboard, btnReplay, btnMenu;
    private LinearLayout containerPlayedTracks;

    // Données de la partie terminée
    private int score, goodAnswers, totalTracks, maxCombo;
    private String genre, difficulty, guessMode;
    private boolean isArtistSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Désactivation de l'animation d'ouverture
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // Liaison des vues avec le layout XML
        tvResultDetails = findViewById(R.id.tvResultDetails);
        tvFinalScore = findViewById(R.id.tvFinalScore);
        tvGoodAnswers = findViewById(R.id.tvGoodAnswers);
        tvMaxCombo = findViewById(R.id.tvMaxCombo);
        tvSaveStatus = findViewById(R.id.tvSaveStatus);
        btnLeaderboard = findViewById(R.id.btnLeaderboard);
        btnReplay = findViewById(R.id.btnReplay);
        btnMenu = findViewById(R.id.btnMenu);
        containerPlayedTracks = findViewById(R.id.containerPlayedTracks);

        // Récupération des résultats et paramètres envoyés par GameActivity
        Intent intent = getIntent();
        score = intent.getIntExtra("SCORE", 0);
        goodAnswers = intent.getIntExtra("GOOD_ANSWERS", 0);
        totalTracks = intent.getIntExtra("TOTAL_TRACKS", 10);
        maxCombo = intent.getIntExtra("MAX_COMBO", 0);
        genre = intent.getStringExtra("GENRE");
        difficulty = intent.getStringExtra("DIFFICULTY_MODE");
        isArtistSearch = intent.getBooleanExtra("IS_ARTIST_SEARCH", false);
        guessMode = intent.getStringExtra("GUESS_MODE");
        if (guessMode == null || guessMode.isEmpty()) {
            guessMode = "TITLE";
        }
        ArrayList<String> playedTracks = intent.getStringArrayListExtra("PLAYED_TRACKS");

        // Affichage des statistiques
        tvResultDetails.setText(genre + " • " + difficulty);
        tvFinalScore.setText(score + " pts");
        tvGoodAnswers.setText(goodAnswers + " / " + totalTracks + " réussies");
        tvMaxCombo.setText("Combo max : " + maxCombo);

        // Affichage de la liste cliquable des morceaux joués
        if (playedTracks != null) {
            displayPlayedTracks(playedTracks);
        }

        // Sauvegarde du meilleur score par difficulté dans Firebase Firestore
        saveScoreToFirestore();

        // Ouvrir le classement directement sur la difficulté de la partie
        btnLeaderboard.setOnClickListener(v -> {
            Intent lbIntent = new Intent(ResultActivity.this, LeaderboardActivity.class);
            lbIntent.putExtra("DIFFICULTY_MODE", difficulty);
            lbIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(lbIntent);
            overridePendingTransition(0, 0);
        });

        // Relancer une partie avec exactement les mêmes paramètres (y compris IS_ARTIST_SEARCH et GUESS_MODE)
        btnReplay.setOnClickListener(v -> {
            Intent replayIntent = new Intent(ResultActivity.this, GameActivity.class);
            replayIntent.putExtra("DIFFICULTY_MODE", difficulty);
            replayIntent.putExtra("GENRE", genre);
            replayIntent.putExtra("IS_ARTIST_SEARCH", isArtistSearch);
            replayIntent.putExtra("GUESS_MODE", guessMode);
            replayIntent.putExtra("TRACK_COUNT", totalTracks);
            replayIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(replayIntent);
            overridePendingTransition(0, 0);
            finish();
        });

        // Retourner à l'écran de configuration
        btnMenu.setOnClickListener(v -> {
            Intent menuIntent = new Intent(ResultActivity.this, ConfigActivity.class);
            menuIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(menuIntent);
            overridePendingTransition(0, 0);
            finish();
        });
    }

    /**
     * Affiche chaque morceau joué sous forme de carte cliquable ouvrant Deezer dans le navigateur.
     */
    private void displayPlayedTracks(ArrayList<String> playedTracks) {
        for (int i = 0; i < playedTracks.size(); i++) {
            String entry = playedTracks.get(i);
            String[] parts = entry.split("\\|", -1);
            String label = parts[0];
            String deezerLink = parts.length > 1 ? parts[1] : "";

            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, dpToPx(8));
            card.setLayoutParams(params);
            card.setCardBackgroundColor(Color.parseColor("#161B22"));
            card.setRadius(dpToPx(12));
            card.setCardElevation(0);
            card.setStrokeWidth(dpToPx(1));
            card.setStrokeColor(Color.parseColor("#30363D"));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = dpToPx(14);
            row.setPadding(pad, pad, pad, pad);

            TextView tvTrack = new TextView(this);
            tvTrack.setText((i + 1) + ". " + label);
            tvTrack.setTextColor(Color.WHITE);
            tvTrack.setTextSize(14);
            tvTrack.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvOpen = new TextView(this);
            tvOpen.setText("Écouter ↗");
            tvOpen.setTextColor(Color.parseColor("#58A6FF"));
            tvOpen.setTextSize(12);

            row.addView(tvTrack);
            row.addView(tvOpen);
            card.addView(row);

            card.setOnClickListener(v -> {
                if (!deezerLink.isEmpty()) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(deezerLink));
                    startActivity(browserIntent);
                } else {
                    Toast.makeText(this, "Lien Deezer indisponible pour ce titre.", Toast.LENGTH_SHORT).show();
                }
            });

            containerPlayedTracks.addView(card);
        }
    }

    /**
     * Enregistre le score dans Firestore par joueur et par difficulté uniquement si le record est battu.
     */
    private void saveScoreToFirestore() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            tvSaveStatus.setText("Non connecté : score non sauvegardé");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = user.getUid();
        String docId = uid + "_" + difficulty;

        db.collection("scores").document(docId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    Long bestScore = documentSnapshot.exists() ? documentSnapshot.getLong("score") : null;

                    if (bestScore == null || score > bestScore) {
                        Map<String, Object> scoreData = new HashMap<>();
                        scoreData.put("userId", uid);
                        scoreData.put("email", user.getEmail());
                        scoreData.put("score", score);
                        scoreData.put("goodAnswers", goodAnswers);
                        scoreData.put("totalTracks", totalTracks);
                        scoreData.put("maxCombo", maxCombo);
                        scoreData.put("genre", genre);
                        scoreData.put("difficulty", difficulty);
                        scoreData.put("date", Timestamp.now());

                        db.collection("scores").document(docId)
                                .set(scoreData)
                                .addOnSuccessListener(aVoid -> {
                                    tvSaveStatus.setText("Nouveau record (" + difficulty + ") enregistré !");
                                    tvSaveStatus.setTextColor(Color.parseColor("#4CAF50"));
                                })
                                .addOnFailureListener(e -> {
                                    tvSaveStatus.setText("Erreur lors de la sauvegarde");
                                    tvSaveStatus.setTextColor(Color.parseColor("#F85149"));
                                });
                    } else {
                        tvSaveStatus.setText("Record " + difficulty + " conservé : " + bestScore + " pts");
                        tvSaveStatus.setTextColor(Color.parseColor("#8B949E"));
                    }
                })
                .addOnFailureListener(e -> {
                    tvSaveStatus.setText("Erreur de lecture du record");
                    tvSaveStatus.setTextColor(Color.parseColor("#F85149"));
                });
    }

    /**
     * Convertit une valeur en dp en pixels selon la densité de l'écran.
     */
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}