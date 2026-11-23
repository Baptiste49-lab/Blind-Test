package fr.esaip.blindtest;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import fr.esaip.blindtest.models.Difficulty;

public class ConfigActivity extends AppCompatActivity {

    // Composants de l'interface
    private TextView tvUserEmail, tvTrackCount, tvValidationStatus;
    private RadioGroup rgSearchType, rgGuessType, rgDifficulty;
    private Spinner spinnerGenre;
    private AutoCompleteTextView etArtistSearch;
    private SeekBar seekBarTracks;
    private Button btnStartGame, btnLeaderboard, btnLogout;

    // Outils réseau et temporisation pour l'autocomplétion Deezer
    private RequestQueue requestQueue;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // État de validation du thème saisi
    private boolean isThemeValidated = false;
    private boolean isSelectingFromDropdown = false;
    private String validatedThemeQuery = "";
    private final List<SuggestionItem> currentSuggestions = new ArrayList<>();

    // Liste des genres musicaux prédéfinis
    private final String[] genres = {"Pop", "Rap", "Rock", "Electro", "Jazz", "Classique", "R&B", "Reggae"};
    private int selectedTrackCount = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Désactivation de l'animation d'ouverture
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        requestQueue = Volley.newRequestQueue(this);

        // Liaison des vues avec le layout XML
        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvTrackCount = findViewById(R.id.tvTrackCount);
        tvValidationStatus = findViewById(R.id.tvValidationStatus);
        rgSearchType = findViewById(R.id.rgSearchType);
        rgGuessType = findViewById(R.id.rgGuessType);
        rgDifficulty = findViewById(R.id.rgDifficulty);
        spinnerGenre = findViewById(R.id.spinnerGenre);
        etArtistSearch = findViewById(R.id.etArtistSearch);
        seekBarTracks = findViewById(R.id.seekBarTracks);
        btnStartGame = findViewById(R.id.btnStartGame);
        btnLeaderboard = findViewById(R.id.btnLeaderboard);
        btnLogout = findViewById(R.id.btnLogout);

        // Affichage de l'adresse e-mail de l'utilisateur connecté
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            tvUserEmail.setText(user.getEmail());
        }

        setupGenreSpinner();
        setupAutoCompleteSearch();
        updateSearchHint();

        // Bascule entre la recherche par Genre et la recherche par Artiste / Musique
        rgSearchType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbByArtist) {
                spinnerGenre.setVisibility(View.GONE);
                etArtistSearch.setVisibility(View.VISIBLE);
                tvValidationStatus.setVisibility(View.VISIBLE);
                updateSearchHint();
            } else {
                spinnerGenre.setVisibility(View.VISIBLE);
                etArtistSearch.setVisibility(View.GONE);
                tvValidationStatus.setVisibility(View.GONE);
            }
        });

        // Adapte le tri de la recherche quand on change entre "Nom du titre" et "Nom de l'artiste"
        rgGuessType.setOnCheckedChangeListener((group, checkedId) -> {
            resetSearchValidation();
            updateSearchHint();
        });

        // Gestion de la barre de sélection du nombre de pistes (de 5 à 20)
        seekBarTracks.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedTrackCount = progress + 5;
                tvTrackCount.setText(selectedTrackCount + " pistes");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Lancement de la partie (bloqué si l'artiste/titre n'a pas été cliqué dans la liste)
        btnStartGame.setOnClickListener(v -> validateAndStartGame());

        // Ouverture du classement Top 10
        btnLeaderboard.setOnClickListener(v -> {
            Intent intent = new Intent(ConfigActivity.this, LeaderboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        // Déconnexion de Firebase
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(ConfigActivity.this, AuthActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });
    }

    /**
     * Met à jour le texte indicatif du champ selon ce que le joueur veut deviner.
     */
    private void updateSearchHint() {
        boolean guessArtist = rgGuessType.getCheckedRadioButtonId() == R.id.rbGuessArtist;
        if (guessArtist) {
            etArtistSearch.setHint("Tapez un titre de musique...");
        } else {
            etArtistSearch.setHint("Tapez un nom d'artiste...");
        }
    }

    /**
     * Réinitialise le champ de recherche et son statut de validation.
     */
    private void resetSearchValidation() {
        isSelectingFromDropdown = true;
        etArtistSearch.setText("");
        isSelectingFromDropdown = false;
        isThemeValidated = false;
        validatedThemeQuery = "";
        currentSuggestions.clear();
        tvValidationStatus.setText("Sélectionnez une proposition dans la liste");
        tvValidationStatus.setTextColor(Color.parseColor("#FF9800"));
    }

    /**
     * Configure la recherche en direct sur Deezer avec obligation de cliquer sur un résultat.
     */
    private void setupAutoCompleteSearch() {
        etArtistSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isSelectingFromDropdown) return;

                isThemeValidated = false;
                validatedThemeQuery = "";
                tvValidationStatus.setText("Sélectionnez une proposition dans la liste");
                tvValidationStatus.setTextColor(Color.parseColor("#FF9800"));

                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                String query = s.toString().trim();
                if (query.length() >= 2) {
                    searchRunnable = () -> fetchDeezerSuggestions(query);
                    searchHandler.postDelayed(searchRunnable, 350);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Validation obligatoire lors du clic sur une suggestion de la liste déroulante
        etArtistSearch.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < currentSuggestions.size()) {
                SuggestionItem selected = currentSuggestions.get(position);
                isSelectingFromDropdown = true;
                etArtistSearch.setText(selected.cleanQuery);
                etArtistSearch.setSelection(selected.cleanQuery.length());
                isSelectingFromDropdown = false;

                isThemeValidated = true;
                validatedThemeQuery = selected.cleanQuery;
                etArtistSearch.setError(null);
                tvValidationStatus.setText("✓ Validé sur Deezer : " + selected.displayLabel);
                tvValidationStatus.setTextColor(Color.parseColor("#4CAF50"));
            }
        });
    }

    /**
     * Interroge Deezer en triant strictement :
     * - Si on devine le TITRE -> recherche d'ARTISTES (/search/artist)
     * - Si on devine l'ARTISTE -> recherche de TITRES (/search/track)
     */
    private void fetchDeezerSuggestions(String query) {
        String encodedQuery;
        try {
            encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            encodedQuery = query.replace(" ", "%20");
        }

        boolean guessArtist = rgGuessType.getCheckedRadioButtonId() == R.id.rbGuessArtist;
        String endpoint = guessArtist ? "https://api.deezer.com/search/track?q=" : "https://api.deezer.com/search/artist?q=";
        String url = endpoint + encodedQuery + "&limit=10";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    JSONArray data = response.optJSONArray("data");
                    if (data == null) return;

                    currentSuggestions.clear();
                    List<String> labels = new ArrayList<>();
                    Set<String> addedKeys = new LinkedHashSet<>();

                    for (int i = 0; i < data.length(); i++) {
                        JSONObject obj = data.optJSONObject(i);
                        if (obj == null) continue;

                        if (guessArtist) {
                            // Mode "Deviner l'artiste" -> on propose uniquement des Titres
                            String title = obj.optString("title_short", obj.optString("title", "")).trim();
                            if (!title.isEmpty() && addedKeys.add(title.toLowerCase())) {
                                String label = "Titre : " + title;
                                currentSuggestions.add(new SuggestionItem(label, title));
                                labels.add(label);
                            }
                        } else {
                            // Mode "Deviner le titre" -> on propose uniquement des Artistes
                            String artistName = obj.optString("name", "").trim();
                            if (!artistName.isEmpty() && addedKeys.add(artistName.toLowerCase())) {
                                String label = "Artiste : " + artistName;
                                currentSuggestions.add(new SuggestionItem(label, artistName));
                                labels.add(label);
                            }
                        }

                        if (labels.size() >= 8) break;
                    }

                    // Mise à jour de la liste déroulante sombre
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            labels
                    ) {
                        @NonNull
                        @Override
                        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                            View v = super.getView(position, convertView, parent);
                            TextView tv = (TextView) v;
                            tv.setTextColor(Color.WHITE);
                            tv.setBackgroundColor(Color.parseColor("#161B22"));
                            return v;
                        }
                    };

                    etArtistSearch.setAdapter(adapter);
                    if (!labels.isEmpty() && etArtistSearch.hasFocus()) {
                        etArtistSearch.showDropDown();
                    }
                },
                error -> {}
        );

        requestQueue.add(request);
    }

    /**
     * Configure l'adaptateur du Spinner pour afficher le texte en blanc sur fond sombre.
     */
    private void setupGenreSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                genres
        ) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(Color.WHITE);
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTextColor(Color.WHITE);
                view.setBackgroundColor(Color.parseColor("#161B22"));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGenre.setAdapter(adapter);
    }

    /**
     * Vérifie que l'utilisateur a bien cliqué sur une suggestion valide avant de lancer la partie.
     */
    private void validateAndStartGame() {
        boolean isArtistSearch = rgSearchType.getCheckedRadioButtonId() == R.id.rbByArtist;
        String theme;

        if (isArtistSearch) {
            if (!isThemeValidated || validatedThemeQuery.isEmpty()) {
                etArtistSearch.setError("Cliquez sur une proposition de la liste");
                Toast.makeText(this, "Obligatoire : cliquez sur une proposition dans la liste déroulante.", Toast.LENGTH_LONG).show();
                return;
            }
            theme = validatedThemeQuery;
        } else {
            theme = spinnerGenre.getSelectedItem().toString();
        }

        String guessMode = (rgGuessType.getCheckedRadioButtonId() == R.id.rbGuessArtist)
                ? "ARTIST"
                : "TITLE";

        Difficulty selectedDifficulty = Difficulty.FACILE;
        int checkedDiff = rgDifficulty.getCheckedRadioButtonId();
        if (checkedDiff == R.id.rbMoyen) {
            selectedDifficulty = Difficulty.MOYEN;
        } else if (checkedDiff == R.id.rbDifficile) {
            selectedDifficulty = Difficulty.DIFFICILE;
        }

        Intent intent = new Intent(ConfigActivity.this, GameActivity.class);
        intent.putExtra("DIFFICULTY_MODE", selectedDifficulty.name());
        intent.putExtra("GENRE", theme);
        intent.putExtra("IS_ARTIST_SEARCH", isArtistSearch);
        intent.putExtra("GUESS_MODE", guessMode);
        intent.putExtra("TRACK_COUNT", selectedTrackCount);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
    }

    /**
     * Modèle interne pour stocker une proposition Deezer dans la liste déroulante.
     */
    private static class SuggestionItem {
        String displayLabel;
        String cleanQuery;

        SuggestionItem(String displayLabel, String cleanQuery) {
            this.displayLabel = displayLabel;
            this.cleanQuery = cleanQuery;
        }
    }
}