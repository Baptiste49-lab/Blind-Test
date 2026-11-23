package fr.esaip.blindtest.models;

public enum Difficulty {
    FACILE(30000),      // 30 secondes
    MOYEN(15000),       // 15 secondes
    DIFFICILE(5000);    // 5 secondes

    private final long timeLimit;

    Difficulty(long timeLimit) {
        this.timeLimit = timeLimit;
    }

    public long getTimeLimit() {
        return timeLimit;
    }
}