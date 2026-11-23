package fr.esaip.blindtest;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardActivity extends AppCompatActivity {

    // Composants de l'interface
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private LinearLayout containerScores;
    private Button btnBack;
    private MaterialButton btnFilterFacile, btnFilterMoyen, btnFilterDifficile;

    // Difficulté actuellement affichée
    private String selectedDifficulty = "FACILE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Désactivation de l'animation d'ouverture
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        // Liaison des vues avec le layout XML
        progressBar = findViewById(R.id.progressBarLeaderboard);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        containerScores = findViewById(R.id.containerScores);
        btnBack = findViewById(R.id.btnBack);
        btnFilterFacile = findViewById(R.id.btnFilterFacile);
        btnFilterMoyen = findViewById(R.id.btnFilterMoyen);
        btnFilterDifficile = findViewById(R.id.btnFilterDifficile);

        // Si on arrive depuis ResultActivity, on présélectionne la difficulté jouée
        if (getIntent().hasExtra("DIFFICULTY_MODE")) {
            String diff = getIntent().getStringExtra("DIFFICULTY_MODE");
            if (diff != null && !diff.isEmpty()) {
                selectedDifficulty = diff;
            }
        }

        // Gestion des clics sur les boutons de filtre
        btnFilterFacile.setOnClickListener(v -> selectDifficulty("FACILE"));
        btnFilterMoyen.setOnClickListener(v -> selectDifficulty("MOYEN"));
        btnFilterDifficile.setOnClickListener(v -> selectDifficulty("DIFFICILE"));

        // Bouton retour sans animation
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        // Chargement initial du classement
        selectDifficulty(selectedDifficulty);
    }

    /**
     * Met à jour l'apparence des boutons de filtre et recharge les scores correspondants.
     */
    private void selectDifficulty(String difficulty) {
        selectedDifficulty = difficulty;

        updateFilterButtonStyle(btnFilterFacile, difficulty.equals("FACILE"));
        updateFilterButtonStyle(btnFilterMoyen, difficulty.equals("MOYEN"));
        updateFilterButtonStyle(btnFilterDifficile, difficulty.equals("DIFFICILE"));

        loadTopScores(difficulty);
    }

    /**
     * Applique le style actif (bleu) ou inactif (sombre) sur un bouton de filtre.
     */
    private void updateFilterButtonStyle(MaterialButton button, boolean isActive) {
        if (isActive) {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1F6FEB")));
            button.setTextColor(Color.WHITE);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#161B22")));
            button.setTextColor(Color.parseColor("#8B949E"));
        }
    }

    /**
     * Récupère les scores de la difficulté choisie depuis Firestore, les trie et affiche le Top 10.
     */
    private void loadTopScores(String difficulty) {
        containerScores.removeAllViews();
        tvEmptyState.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("scores")
                .whereEqualTo("difficulty", difficulty)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    progressBar.setVisibility(View.GONE);

                    if (queryDocumentSnapshots.isEmpty()) {
                        tvEmptyState.setText("Aucun score enregistré en mode " + difficulty + ".");
                        tvEmptyState.setVisibility(View.VISIBLE);
                        return;
                    }

                    // Extraction des scores dans une liste pour les trier par ordre décroissant
                    List<ScoreEntry> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String email = doc.getString("email");
                        String genre = doc.getString("genre");
                        Long scoreLong = doc.getLong("score");
                        Long comboLong = doc.getLong("maxCombo");

                        String playerName = (email != null && email.contains("@"))
                                ? email.substring(0, email.indexOf("@"))
                                : "Joueur";
                        int score = (scoreLong != null) ? scoreLong.intValue() : 0;
                        int maxCombo = (comboLong != null) ? comboLong.intValue() : 0;

                        String details = (genre != null ? genre : "Mix") + " • Combo max : " + maxCombo;
                        entries.add(new ScoreEntry(playerName, details, score));
                    }

                    // Tri décroissant par score
                    entries.sort((a, b) -> Integer.compare(b.score, a.score));

                    // Affichage limité aux 10 meilleurs joueurs
                    int limit = Math.min(10, entries.size());
                    for (int i = 0; i < limit; i++) {
                        ScoreEntry entry = entries.get(i);
                        addScoreCard(i + 1, entry.playerName, entry.details, entry.score);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Erreur lors du chargement du classement.");
                    tvEmptyState.setVisibility(View.VISIBLE);
                });
    }

    /**
     * Crée et ajoute une carte Material pour une ligne du classement.
     */
    private void addScoreCard(int rank, String playerName, String details, int score) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(10));
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(Color.parseColor("#161B22"));
        card.setRadius(dpToPx(14));
        card.setCardElevation(0);
        card.setStrokeWidth(dpToPx(1));
        card.setStrokeColor(Color.parseColor("#30363D"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padding = dpToPx(16);
        row.setPadding(padding, padding, padding, padding);

        // Rang avec couleurs podium (Or, Argent, Bronze)
        TextView tvRank = new TextView(this);
        tvRank.setText("#" + rank);
        tvRank.setTextSize(18);
        tvRank.setTypeface(null, Typeface.BOLD);
        tvRank.setWidth(dpToPx(42));
        if (rank == 1) {
            tvRank.setTextColor(Color.parseColor("#FFD700"));
        } else if (rank == 2) {
            tvRank.setTextColor(Color.parseColor("#C0C0C0"));
        } else if (rank == 3) {
            tvRank.setTextColor(Color.parseColor("#CD7F32"));
        } else {
            tvRank.setTextColor(Color.parseColor("#8B949E"));
        }

        // Colonne centrale : Nom du joueur + thème et combo
        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(this);
        tvName.setText(playerName);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(16);
        tvName.setTypeface(null, Typeface.BOLD);

        TextView tvDetails = new TextView(this);
        tvDetails.setText(details);
        tvDetails.setTextColor(Color.parseColor("#8B949E"));
        tvDetails.setTextSize(12);

        infoCol.addView(tvName);
        infoCol.addView(tvDetails);

        // Score à droite
        TextView tvScore = new TextView(this);
        tvScore.setText(score + " pts");
        tvScore.setTextColor(Color.parseColor("#58A6FF"));
        tvScore.setTextSize(16);
        tvScore.setTypeface(null, Typeface.BOLD);

        row.addView(tvRank);
        row.addView(infoCol);
        row.addView(tvScore);
        card.addView(row);

        containerScores.addView(card);
    }

    /**
     * Convertit une valeur en dp en pixels selon la densité de l'écran.
     */
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Modèle interne représentant une entrée du classement.
     */
    private static class ScoreEntry {
        String playerName;
        String details;
        int score;

        ScoreEntry(String playerName, String details, int score) {
            this.playerName = playerName;
            this.details = details;
            this.score = score;
        }
    }
}