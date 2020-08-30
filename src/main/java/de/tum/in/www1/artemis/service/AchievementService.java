package de.tum.in.www1.artemis.service;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

@Service
public class AchievementService {

    private final AchievementRepository achievementRepository;

    private final UserRepository userRepository;

    public AchievementService(AchievementRepository achievementRepository, UserRepository userRepository) {
        this.achievementRepository = achievementRepository;
        this.userRepository = userRepository;
    }

    public Optional<Achievement> findById(Long achievementId) {
        return achievementRepository.findById(achievementId);
    }

    public Set<Achievement> findAllForCourse(Long courseId) {
        return achievementRepository.getAllByCourseId(courseId);
    }

    public Set<Achievement> findAllForUser(Long userId) {
        return achievementRepository.getAllByUserId(userId);
    }

    /**
     * Deletes an achievement by also removing it from all users
     * @param achievement achievement to be deleted
     */
    public void delete(Achievement achievement) {
        for (User user : achievement.getUsers()) {
            user.removeAchievement(achievement);
            userRepository.save(user);
        }
        achievementRepository.delete(achievement);
    }
}
