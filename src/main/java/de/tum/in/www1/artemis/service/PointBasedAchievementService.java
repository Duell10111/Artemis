package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.repository.AchievementRepository;

@Service
public class PointBasedAchievementService {

    private final AchievementRepository achievementRepository;

    private final static long PERCENT_GOLD = 100L;

    private final static long PERCENT_SILVER = 80L;

    private final static long PERCENT_BRONZE = 60L;

    private final static long PERCENT_UNRANKED = 50L;

    public PointBasedAchievementService(AchievementRepository achievementRepository) {
        this.achievementRepository = achievementRepository;
    }

    /**
     * Generates all point based achievements for a course
     * @param course
     */
    public void generateAchievements(Course course) {
        Set<Achievement> achievementsToSave = new HashSet<>();
        achievementsToSave.add(new Achievement("Point Master", "Score " + PERCENT_GOLD + " percent of the points in an exercise", "award", AchievementRank.GOLD,
                AchievementType.POINT, PERCENT_GOLD, null, course, null));
        achievementsToSave.add(new Achievement("Point Intermediate", "Score at least" + PERCENT_SILVER + " percent of the points in an exercise", "award", AchievementRank.SILVER,
                AchievementType.POINT, PERCENT_SILVER, null, course, null));
        achievementsToSave.add(new Achievement("Point Beginner", "Score at least" + PERCENT_BRONZE + " percent of the points in an exercise", "award", AchievementRank.BRONZE,
                AchievementType.POINT, PERCENT_BRONZE, null, course, null));
        achievementsToSave.add(new Achievement("Point Amateur", "Score at least" + PERCENT_UNRANKED + " percent of the points in an exercise", "award", AchievementRank.UNRANKED,
                AchievementType.POINT, PERCENT_UNRANKED, null, course, null));

        achievementRepository.saveAll(achievementsToSave);
    }
}
