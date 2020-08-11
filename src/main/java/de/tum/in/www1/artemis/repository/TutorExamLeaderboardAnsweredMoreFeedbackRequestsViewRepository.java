package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorExamLeaderboardAnsweredMoreFeedbackRequestsView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TutorExamLeaderboardAnsweredMoreFeedbackRequestsViewRepository extends JpaRepository<TutorExamLeaderboardAnsweredMoreFeedbackRequestsView, Long> {

    List<TutorExamLeaderboardAnsweredMoreFeedbackRequestsView> findAllByExamId(long examId);

    List<TutorExamLeaderboardAnsweredMoreFeedbackRequestsView> findAllByLeaderboardId_ExerciseId(long exerciseId);
}
