package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.TutorScore;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TutorScoreRepository;

@Service
public class TutorScoreService {

    private final TutorScoreRepository tutorScoreRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    public TutorScoreService(TutorScoreRepository tutorScoreRepository, StudentParticipationRepository studentParticipationRepository, ComplaintRepository complaintRepository,
            ComplaintResponseRepository complaintResponseRepository) {
        this.tutorScoreRepository = tutorScoreRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
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

    /**
     * Delete all TutorScores for exercise.
     *
     * @param exercise exercise
     */
    public void deleteTutorScoresForExercise(Exercise exercise) {
        var scores = getTutorScoresForExercise(exercise);

        for (TutorScore score : scores) {
            tutorScoreRepository.delete(score);
        }
    }

    /**
     * Returns TutorScores for specific tutor and exercise.
     *
     * @param tutor tutor
     * @param exercise exercise
     * @return tutor score object for that tutor and exercise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<TutorScore> getTutorScoreForTutorAndExercise(User tutor, Exercise exercise) {
        return tutorScoreRepository.findByTutorAndExercise(tutor, exercise);
    }

    /**
     * Deletes all TutorScores for result deletedResult.
     *
     * @param deletedResult result to be deleted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeResult(Result deletedResult) {
        if (deletedResult.getParticipation() == null || deletedResult.getParticipation().getId() == null) {
            return;
        }

        if (deletedResult.getParticipation().getClass() != StudentParticipation.class) {
            return;
        }

        var participation = studentParticipationRepository.findById(deletedResult.getParticipation().getId());

        if (participation.isEmpty()) {
            return;
        }

        Exercise exercise = participation.get().getExercise();

        var existingTutorScore = tutorScoreRepository.findByTutorAndExercise(deletedResult.getAssessor(), exercise);

        if (existingTutorScore.isPresent()) {
            TutorScore tutorScore = existingTutorScore.get();

            if (tutorScore.getAssessments() > 0) {
                tutorScore.setAssessments(tutorScore.getAssessments() - 1);
                tutorScore.setAssessmentsPoints(tutorScore.getAssessmentsPoints() - exercise.getMaxScore());
            }

            // handle complaints and feedback requests
            if (deletedResult.hasComplaint() == Boolean.TRUE) {
                Complaint complaint = complaintRepository.findByResult_Id(deletedResult.getId()).get();

                // complaint
                if (complaint.getComplaintType() == ComplaintType.COMPLAINT) {
                    if (tutorScore.getAllComplaints() > 0) {
                        tutorScore.setAllComplaints(tutorScore.getAllComplaints() - 1);
                        tutorScore.setComplaintsPoints(tutorScore.getComplaintsPoints() - exercise.getMaxScore());
                    }

                    if (complaint.isAccepted() == Boolean.TRUE) {
                        tutorScore.setAcceptedComplaints(tutorScore.getAcceptedComplaints() - 1);
                    }

                    // complaint response
                    Optional<ComplaintResponse> complaintResponse = complaintResponseRepository.findByComplaint_Id(complaint.getId());

                    if (complaintResponse.isPresent()) {
                        var fromComplaintResponse = tutorScoreRepository.findByTutorAndExercise(complaintResponse.get().getReviewer(), exercise).get();

                        if (fromComplaintResponse.getComplaintResponses() > 0) {
                            fromComplaintResponse.setComplaintResponses(fromComplaintResponse.getComplaintResponses() - 1);
                            fromComplaintResponse.setComplaintResponsesPoints(fromComplaintResponse.getComplaintResponsesPoints() - exercise.getMaxScore());
                        }

                        tutorScoreRepository.save(fromComplaintResponse);
                    }
                }

                // feedback request
                if (complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK) {
                    if (tutorScore.getAllFeedbackRequests() > 0) {
                        tutorScore.setAllFeedbackRequests(tutorScore.getAllFeedbackRequests() - 1);
                        tutorScore.setFeedbackRequestsPoints(tutorScore.getFeedbackRequestsPoints() - exercise.getMaxScore());
                    }

                    // answered feedback request
                    Optional<ComplaintResponse> complaintResponse = complaintResponseRepository.findByComplaint_Id(complaint.getId());

                    if (complaintResponse.isPresent()) {
                        var fromComplaintResponse = tutorScoreRepository.findByTutorAndExercise(complaintResponse.get().getReviewer(), exercise).get();

                        if (fromComplaintResponse.getComplaintResponses() > 0 && fromComplaintResponse.getComplaintResponsesPoints() > 0) {
                            fromComplaintResponse.setComplaintResponses(fromComplaintResponse.getComplaintResponses() - 1);
                            fromComplaintResponse.setComplaintResponsesPoints(fromComplaintResponse.getComplaintResponsesPoints() - exercise.getMaxScore());
                        }

                        tutorScoreRepository.save(fromComplaintResponse);
                    }
                }
            }

            tutorScoreRepository.save(tutorScore);
        }
    }

    /**
     * Updates all TutorScores for result updatedResult.
     *
     * @param updatedResult result to be updated
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateResult(Result updatedResult) {
        if (updatedResult.getParticipation() == null || updatedResult.getParticipation().getId() == null
                || updatedResult.getParticipation().getClass() != StudentParticipation.class) {
            return;
        }

        var participation = studentParticipationRepository.findById(updatedResult.getParticipation().getId());

        if (participation.isEmpty()) {
            return;
        }

        Exercise exercise = participation.get().getExercise();
        Double maxScore = 0.0;

        if (exercise.getMaxScore() != null) {
            maxScore = exercise.getMaxScore();
        }

        var existingTutorScore = tutorScoreRepository.findByTutorAndExercise(updatedResult.getAssessor(), exercise);

        if (existingTutorScore.isPresent()) {
            TutorScore tutorScore = existingTutorScore.get();

            tutorScore.setAssessments(tutorScore.getAssessments() + 1);
            tutorScore.setAssessmentsPoints(tutorScore.getAssessmentsPoints() + maxScore);

            tutorScore = addComplaintsAndFeedbackRequests(updatedResult, tutorScore, exercise);

            tutorScoreRepository.save(tutorScore);
        }
        else {
            TutorScore newScore = new TutorScore(updatedResult.getAssessor(), exercise, 1, maxScore);

            newScore = addComplaintsAndFeedbackRequests(updatedResult, newScore, exercise);

            tutorScoreRepository.save(newScore);
        }
    }

    /**
     * Helper method for updating complaints and feedback requests in tutor scores.
     */
    private TutorScore addComplaintsAndFeedbackRequests(Result result, TutorScore tutorScore, Exercise exercise) {
        // add complaints and feedback requests
        if (result.hasComplaint() == Boolean.TRUE) {
            Complaint complaint = complaintRepository.findByResult_Id(result.getId()).get();

            // complaint
            if (complaint.getComplaintType() == ComplaintType.COMPLAINT) {
                tutorScore.setAllComplaints(tutorScore.getAllComplaints() + 1);
                tutorScore.setComplaintsPoints(tutorScore.getComplaintsPoints() + exercise.getMaxScore());

                if (complaint.isAccepted() == Boolean.TRUE) {
                    tutorScore.setAcceptedComplaints(tutorScore.getAcceptedComplaints() + 1);
                }

                // complaint response
                Optional<ComplaintResponse> complaintResponse = complaintResponseRepository.findByComplaint_Id(complaint.getId());

                if (complaintResponse.isPresent()) {
                    var fromComplaintResponse = tutorScoreRepository.findByTutorAndExercise(complaintResponse.get().getReviewer(), exercise).get();

                    fromComplaintResponse.setComplaintResponses(fromComplaintResponse.getComplaintResponses() + 1);
                    fromComplaintResponse.setComplaintResponsesPoints(fromComplaintResponse.getComplaintResponsesPoints() + exercise.getMaxScore());

                    tutorScoreRepository.save(fromComplaintResponse);
                }
            }

            // feedback request
            if (complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK) {
                tutorScore.setAllFeedbackRequests(tutorScore.getAllFeedbackRequests() + 1);
                tutorScore.setFeedbackRequestsPoints(tutorScore.getFeedbackRequestsPoints() + exercise.getMaxScore());

                // answered feedback request
                Optional<ComplaintResponse> complaintResponse = complaintResponseRepository.findByComplaint_Id(complaint.getId());

                if (complaintResponse.isPresent()) {
                    var fromComplaintResponse = tutorScoreRepository.findByTutorAndExercise(complaintResponse.get().getReviewer(), exercise).get();

                    fromComplaintResponse.setAnsweredFeedbackRequests(fromComplaintResponse.getAnsweredFeedbackRequests() + 1);
                    fromComplaintResponse.setAnsweredFeedbackRequestsPoints(fromComplaintResponse.getAnsweredFeedbackRequestsPoints() + exercise.getMaxScore());

                    tutorScoreRepository.save(fromComplaintResponse);
                }
            }
        }

        return tutorScore;
    }
}
