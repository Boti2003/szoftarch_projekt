package hu.bme.szoftarch.identity.domain;

/**
 * Level formula: Level = floor(sqrt(totalPoints / 50)) + 1
 * Next level threshold: nextLevelPoints = currentLevel^2 * 50
 */
public final class LevelCalculator {

    private LevelCalculator() {
    }

    public static int calculateLevel(long totalPoints) {
        if (totalPoints <= 0) {
            return 1;
        }
        return (int) Math.floor(Math.sqrt((double) totalPoints / 50.0)) + 1;
    }

    public static long calculateNextLevelPoints(int currentLevel) {
        return (long) currentLevel * currentLevel * 50L;
    }
}
