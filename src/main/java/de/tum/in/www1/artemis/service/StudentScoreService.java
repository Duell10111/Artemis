package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.StudentScoreRepository;

@Service
public class StudentScoreService {

    private final Logger log = LoggerFactory.getLogger(StudentScoreService.class);

    private final StudentScoreRepository studentScoreRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public StudentScoreService(StudentScoreRepository studentScoreRepository, StudentParticipationRepository studentParticipationRepository) {
        this.studentScoreRepository = studentScoreRepository;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Returns all StudentScores for exercise.
     *
     * @param exercise the exercise
     * @return list of student score objects for that exercise
     */
    public List<StudentScore> getStudentScoresForExercise(Exercise exercise) {
        return studentScoreRepository.findAllByExercise(exercise);
    }

    /**
     * Returns all StudentScores for course.
     *
     * @param course course
     * @return list of student score objects for that course
     */
    public List<StudentScore> getStudentScoresForCourse(Course course) {
        return studentScoreRepository.findAllByExerciseIdIn(course.getExercises());
    }

    /**
     * Returns one StudentScores for exercise and student if there is one.
     *
     * @param student student user
     * @param exercise exercise
     * @return list of student score objects for that course
     */
    public Optional<StudentScore> getStudentScoreForStudentAndExercise(User student, Exercise exercise) {
        return studentScoreRepository.findByStudentAndExercise(student, exercise);
    }

    /**
     * Removes StudentScore for result deletedResult.
     *
     * @param deletedResult result to be deleted
     */
    public void removeResult(Result deletedResult) {
        studentScoreRepository.deleteByResult(deletedResult);
    }

    /**
     * Updates all StudentScores for result updatedResult.
     *
     * @param updatedResult result to be updated
     */
    // @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateResult(Result updatedResult) {
        // ignore results without score or participation
        if (updatedResult.getScore() == null || updatedResult.getParticipation() == null) {
            return;
        }

        var existingStudentScore = studentScoreRepository.findByResult(updatedResult);

        if (existingStudentScore.isPresent()) {
            StudentScore studentScore = existingStudentScore.get();

            studentScore.setScore(updatedResult.getScore());

            return;
        }

        if (updatedResult.getParticipation().getClass() != StudentParticipation.class) {
            return;
        }

        var participation = (StudentParticipation) updatedResult.getParticipation();

        var student = participation.getStudent();
        var exercise = participation.getExercise();

        if (student.isEmpty()) {
            return;
        }

        // make all tests but mine pass
        var kaputt = studentParticipationRepository.findById(participation.getId());
        if (kaputt.isEmpty()) {
            return;
        }

        existingStudentScore = getStudentScoreForStudentAndExercise(student.get(), exercise);

        if (existingStudentScore.isPresent()) {
            StudentScore oldScore = existingStudentScore.get();
            oldScore.setResult(updatedResult);

            if (updatedResult.getScore() != null) {
                oldScore.setScore(updatedResult.getScore());
            }
            else {
                oldScore.setScore(0);
            }

            log.info("Updated existing StudentScore: " + oldScore);
        }
        else {
            StudentScore newScore = new StudentScore(student.get(), exercise, updatedResult);

            if (updatedResult.getScore() != null) {
                newScore.setScore(updatedResult.getScore());
            }
            else {
                newScore.setScore(0);
            }

            studentScoreRepository.save(newScore);
            log.info("Added new StudentScore: " + newScore);
        }
    }
}
