package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.scores.TutorScore;
import de.tum.in.www1.artemis.repository.TutorScoreRepository;

@Service
public class TutorScoreService {

    private final TutorScoreRepository tutorScoreRepository;

    public TutorScoreService(TutorScoreRepository tutorScoreRepository) {
        this.tutorScoreRepository = tutorScoreRepository;
    }

    /**
     * Returns all TutorScores for exercise.
     *
     * @param exercise the exercise
     * @return list of tutor score objet for that exercise
     */
    public List<TutorScore> getTutorScoresForExercise(Exercise exercise) {
        return tutorScoreRepository.findAllByExercise(exercise);
    }

    /**
     * Returns all TutorScores for course.
     *
     * @param course course
     * @return list of tutor score objects for that course
     */
    public List<TutorScore> getTutorScoresForCourse(Course course) {
        return tutorScoreRepository.findAllByExerciseIn(course.getExercises());
    }

    public void removeResult(Result deletedResult) {
        // TODO: change the entry that is based on this result: find it based on the exercise id and subtract the max points from assessmentPoints, reduce assessments by one
        // in case, there has been a complaint, complaint response or feedback request, adjust those values as well
    }

    public void updateResult(Result updatedResult) {
        // TODO: change the entry that is based on this result. We assume that the exercise.maxPoints value stays the same
        // however we might need to handle different cases, e.g.
        // a) the assessor changes --> decrease the score for the previous assessor and increase the score for the new assessor
        // b) ...
    }

    public void addNewResult(Result newResult) {
        // TODO: change the entry that is based on this result: find it based on the exercise id and add the max points to assessmentPoints, add +1 to assessments
    }

    // TODO: also handle complaints, feedback requests and complaint responses
}
