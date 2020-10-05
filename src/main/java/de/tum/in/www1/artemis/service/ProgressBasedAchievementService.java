package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;

@Service
public class ProgressBasedAchievementService {

    private final StudentParticipationRepository studentParticipationRepository;

    private final AchievementRepository achievementRepository;

    private final static int exercisesAmountGold = 10;

    private final static int exercisesAmountSilver = 8;

    private final static int exercisesAmountBronze = 5;

    private final static int exercisesAmountUnranked = 1;

    private final static long minScoreToQualify = 50L;

    public ProgressBasedAchievementService(StudentParticipationRepository studentParticipationRepository, AchievementRepository achievementRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.achievementRepository = achievementRepository;
    }

    public AchievementRank checkForAchievement(Course course, User user) {
        var participations = studentParticipationRepository.findAllByCourseIdAndUserId(course.getId(), user.getId());
        var numberOfExercises = 0;
        for (var participation : participations) {
            var score = participation.findLatestResult().getScore();
            if (score != null && score >= minScoreToQualify) {
                numberOfExercises++;
            }
        }

        if (numberOfExercises >= exercisesAmountGold) {
            return AchievementRank.GOLD;
        }
        else if (numberOfExercises >= exercisesAmountSilver) {
            return AchievementRank.SILVER;
        }
        else if (numberOfExercises >= exercisesAmountBronze) {
            return AchievementRank.BRONZE;
        }
        else if (numberOfExercises == exercisesAmountUnranked) {
            return AchievementRank.UNRANKED;
        }
        return null;
    }

    /**
     * Generates all progress based achievements for a course
     * @param course
     */
    public void generateAchievements(Course course) {
        Set<Achievement> achievementsToSave = new HashSet<>();
        achievementsToSave.add(
                new Achievement("Course Master", "Solve at least " + exercisesAmountGold + " exercises", "tasks", AchievementRank.GOLD, AchievementType.PROGRESS, course, null));
        achievementsToSave.add(new Achievement("Course Intermediate", "Solve at least " + exercisesAmountSilver + " exercises", "tasks", AchievementRank.SILVER,
                AchievementType.PROGRESS, course, null));
        achievementsToSave.add(new Achievement("Course Beginner", "Solve at least " + exercisesAmountBronze + " exercises", "tasks", AchievementRank.BRONZE,
                AchievementType.PROGRESS, course, null));
        achievementsToSave.add(new Achievement("Course Amateur", "Solve your first exercise", "tasks", AchievementRank.UNRANKED, AchievementType.PROGRESS, course, null));

        achievementRepository.saveAll(achievementsToSave);
    }
}
