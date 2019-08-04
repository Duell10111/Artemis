package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.EscalationState;
import de.tum.in.www1.artemis.domain.modeling.ConflictingResult;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ConflictingResultRepository;
import de.tum.in.www1.artemis.repository.ModelAssessmentConflictRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ModelAssessmentConflictService {

    private final Logger log = LoggerFactory.getLogger(ModelAssessmentConflictService.class);

    private final ModelAssessmentConflictRepository modelAssessmentConflictRepository;

    @Autowired
    private ModelingAssessmentService modelingAssessmentService;

    private final ConflictingResultService conflictingResultService;

    private final ConflictingResultRepository conflictingResultRepository;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    private final ResultRepository resultRepository;

    private final SingleUserNotificationService singleUserNotificationService;

    private final GroupNotificationService groupNotificationService;

    public ModelAssessmentConflictService(ModelAssessmentConflictRepository modelAssessmentConflictRepository, ConflictingResultService conflictingResultService,
            ConflictingResultRepository conflictingResultRepository, UserService userService, AuthorizationCheckService authCheckService, ResultRepository resultRepository,
            SingleUserNotificationService singleUserNotificationService, GroupNotificationService groupNotificationService) {
        this.modelAssessmentConflictRepository = modelAssessmentConflictRepository;
        this.conflictingResultService = conflictingResultService;
        this.conflictingResultRepository = conflictingResultRepository;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
        this.singleUserNotificationService = singleUserNotificationService;
        this.groupNotificationService = groupNotificationService;
    }

    public ModelAssessmentConflict findOne(Long conflictId) {
        return modelAssessmentConflictRepository.findById(conflictId).orElseThrow(() -> new EntityNotFoundException("Entity with id " + conflictId + "does not exist"));
    }

    public List<ModelAssessmentConflict> getConflictsForExercise(Long exerciseId) {
        return modelAssessmentConflictRepository.findAllConflictsOfExercise(exerciseId);
    }

    /**
     * @return List of conflicts, that exist with the given submissionId, the requesting user is responsible for handling based on the state of the conflict
     */
    @Transactional(readOnly = true)
    public List<ModelAssessmentConflict> getConflictsForCurrentUserForSubmission(Long submissionId) {
        List<ModelAssessmentConflict> conflictsForSubmission = getConflictsForSubmission(submissionId);
        User currentUser = userService.getUser();
        if (conflictsForSubmission.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        else {
            Exercise exercise = conflictsForSubmission.get(0).getCausingConflictingResult().getResult().getParticipation().getExercise();
            return conflictsForSubmission.stream().filter(conflict -> userIsResponsibleForHandling(conflict, exercise, currentUser)).collect(Collectors.toList());
        }
    }

    /**
     * @return List of conflicts that have been caused by the given submission
     */
    public List<ModelAssessmentConflict> getConflictsForSubmission(Long submissionId) {
        List<ModelAssessmentConflict> existingConflicts = modelAssessmentConflictRepository.findAllConflictsByCausingSubmission(submissionId);
        loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(existingConflicts);
        return existingConflicts;
    }

    /**
     * @return List of conflicts that have the given result as causing conflicting result
     */
    public List<ModelAssessmentConflict> getConflictsByCausingResult(Result result) {
        List<ModelAssessmentConflict> conflicts = modelAssessmentConflictRepository.findAllConflictsByCausingResult(result);
        return conflicts;
    }

    public List<ModelAssessmentConflict> getConflictsForResultInConflict(Result result) {
        List<ModelAssessmentConflict> conflicts = modelAssessmentConflictRepository.findAllConflictsByResultInConflict(result);
        loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(conflicts);
        return conflicts;
    }

    public List<ModelAssessmentConflict> getUnresolvedConflictsForResult(Result result) {
        List<ModelAssessmentConflict> existingConflicts = getConflictsByCausingResult(result);
        return existingConflicts.stream().filter(conflict -> !conflict.isResolved()).collect(Collectors.toList());
    }

    /**
     * @return exercise the given conflict belongs to
     */
    @Transactional
    public Exercise getExerciseOfConflict(Long conflictId) {
        ModelAssessmentConflict conflict = findOne(conflictId);
        return conflict.getCausingConflictingResult().getResult().getParticipation().getExercise();
    }

    /**
     * Updates all conflicts that involve the given result. Conflicts with the given result as causing conflicting result are deleted.
     * The given result is also removed from all resultsInConflict lists. Conflicts that have an empty resultsInConflict list after the update
     * are getting deleted after the causing conflicting result is submitted.
     *
     * @param result result that is about to be deleted
     */
    public void updateConflictsOnResultRemoval(Result result) {
        List<ModelAssessmentConflict> existingConflicts = modelAssessmentConflictRepository.findAllConflictsByCausingResult(result);
        modelAssessmentConflictRepository.deleteAll(existingConflicts);
        existingConflicts = modelAssessmentConflictRepository.findAllConflictsByResultInConflict(result);
        existingConflicts.forEach(conflict -> conflict.getResultsInConflict().forEach(conflictingResult -> {
            if (conflictingResult.getResult().getId().equals(result.getId())) {
                conflict.getResultsInConflict().remove(conflictingResult);
                conflictingResultRepository.deleteById(conflictingResult.getId());
            }
        }));
        existingConflicts.stream().filter(conflict -> conflict.getResultsInConflict().isEmpty()).forEach(conflict -> {
            submitCausingConflictingResult(conflict);
            modelAssessmentConflictRepository.delete(conflict);
        });
    }

    /**
     * Loads for each given conflict the properties that are needed by the conflict resolution view of the client
     */
    public void loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(List<ModelAssessmentConflict> conflicts) {
        conflicts.forEach(this::loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults);
    }

    /**
     * Loads the given conflict all properties that are needed by the conflict resolution view of the client
     */
    public void loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(ModelAssessmentConflict conflict) {
        conflict.getCausingConflictingResult()
                .setResult(resultRepository.findByIdWithEagerSubmissionAndFeedbacksAndAssessor(conflict.getCausingConflictingResult().getResult().getId()).get());
        conflict.getResultsInConflict().forEach(
                conflictingResult -> conflictingResult.setResult(resultRepository.findByIdWithEagerSubmissionAndFeedbacksAndAssessor(conflictingResult.getResult().getId()).get()));
    }

    /**
     * Sets the conflict attribute of each conflictingResult in the resultsInConflict list before saving the given conflicts
     * to make sure conflicting results do not get duplicated in the database when saved.
     */
    public void saveConflicts(List<ModelAssessmentConflict> conflicts) {
        conflicts.forEach(this::saveConflict);
    }

    @Transactional
    public void resolveConflictByInstructor(Long conflictId, Feedback decision) {
        ModelAssessmentConflict conflict = findOne(conflictId);
        verifyNotResolved(conflict);
        applyInstructorDecisionToCompass(conflict.getCausingConflictingResult(), decision);
        conflict.getResultsInConflict().forEach(conflictingResult -> applyInstructorDecisionToCompass(conflictingResult, decision));
        conflict.setState(EscalationState.RESOLVED_BY_INSTRUCTOR);
        conflict.setResolutionDate(ZonedDateTime.now());
        submitCausingConflictingResult(conflict);
    }

    /**
     * Updates the state of the given conflict to resolved depending on the previous state of the conflict and sets the resolution date
     */
    private void resolveConflictByTutor(ModelAssessmentConflict conflict) {
        switch (conflict.getState()) {
        case UNHANDLED:
            conflict.setState(EscalationState.RESOLVED_BY_CAUSER);
            conflict.setResolutionDate(ZonedDateTime.now());
            submitCausingConflictingResult(conflict);
            break;
        case ESCALATED_TO_TUTORS_IN_CONFLICT:
            applyTutorsDecisionToCompass(conflict);
            conflict.setState(EscalationState.RESOLVED_BY_OTHER_TUTORS);
            conflict.setResolutionDate(ZonedDateTime.now());
            submitCausingConflictingResult(conflict);
            break;
        default:
            log.error("Failed to resolve conflict {}. Illegal escalation state", conflict);
            break;
        }
    }

    /**
     * Updates the state of the given conflict by escalating the conflict to the next authority. The assessors or instructors then responsible for handling the conflict are getting
     * notified.
     *
     * @param storedConflict conflict to escalate
     * @return the given conflict with updated state
     */
    @Transactional
    public ModelAssessmentConflict escalateConflict(ModelAssessmentConflict storedConflict) {
        verifyNotResolved(storedConflict);
        switch (storedConflict.getState()) {
        case UNHANDLED:
            Set<Result> distinctResultsInConflict = new HashSet<>();
            storedConflict.getResultsInConflict().forEach(conflictingResult -> distinctResultsInConflict.add(conflictingResult.getResult()));
            distinctResultsInConflict.forEach(result -> singleUserNotificationService.notifyTutorAboutNewConflictForResult(result,
                    storedConflict.getCausingConflictingResult().getResult().getParticipation().getExercise(),
                    storedConflict.getCausingConflictingResult().getResult().getAssessor()));
            storedConflict.setState(EscalationState.ESCALATED_TO_TUTORS_IN_CONFLICT);
            break;
        case ESCALATED_TO_TUTORS_IN_CONFLICT:
            groupNotificationService.notifyInstructorGroupAboutEscalatedConflict(storedConflict);
            storedConflict.setState(EscalationState.ESCALATED_TO_INSTRUCTOR);
            break;
        default:
            log.error("Escalating conflict {} with state {} failed .", storedConflict.getId(), storedConflict.getState());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflict: " + storedConflict.getId() + " can´t be escalated");
        }
        saveConflict(storedConflict);
        return storedConflict;
    }

    /**
     * Used to update the list of conflicts according to the decisions of the currentUser to whom the list of conflicts got escalated to.
     */
    @Transactional
    public void updateEscalatedConflicts(List<ModelAssessmentConflict> conflicts, User currentUser) {
        conflicts.forEach(conflict -> {
            ModelAssessmentConflict storedConflict = findOne(conflict.getId());
            ConflictingResult updatedConflictingResult = conflict.getResultsInConflict().stream()
                    .filter(conflictingResult -> conflictingResult.getResult().getAssessor().getId().equals(currentUser.getId())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Provided request body contains conflict " + conflict.getId() + " without conflictingResult of user"));
            updateEscalatedConflict(storedConflict, updatedConflictingResult);
        });
    }

    /**
     * Adds new conflicts to the provided existingConflicts that are currently not present in the existingConflicts list but contained in the newConflictingFeedbacks mapping
     *
     * @param causingResult           Result that caused the conflicts in newConflictingFeedbacks
     * @param existingConflicts       conflicts with causingResult as the causing Result that curently exist in the database
     * @param newConflictingFeedbacks Map which contains existing feedbacks the causingResult is currently in conflict with. The feedbacks are mapped to the corresponding
     *                                modelElementId of the feedback from causingResult that is inside the same similarity set as the List of feedbacks and therefore conflicting.
     */
    public void addMissingConflicts(Result causingResult, List<ModelAssessmentConflict> existingConflicts, Map<String, List<Feedback>> newConflictingFeedbacks) {
        newConflictingFeedbacks.keySet().forEach(modelElementId -> {
            Optional<ModelAssessmentConflict> foundExistingConflict = existingConflicts.stream()
                    .filter(existingConflict -> existingConflict.getCausingConflictingResult().getModelElementId().equals(modelElementId)).findFirst();
            if (!foundExistingConflict.isPresent()) {
                ModelAssessmentConflict newConflict = createConflict(modelElementId, causingResult, newConflictingFeedbacks.get(modelElementId));
                existingConflicts.add(newConflict);
            }
        });
    }

    /**
     * Resolves conflicts which no longer are in conflict with existing feedbacks represented by the newConflictingFeedbacks map. Updates the list resultsInConflicts of the
     * conflicts, that still have feedbacks they are in conflict with.
     *
     * @param existingConflicts       all conflicts of one causing result that curently exist in the database
     * @param newConflictingFeedbacks Map which contains existing feedbacks the causingResult is currently in conflict with. The feedbacks are mapped to the corresponding
     *                                modelElementId of the feedback from causingResult that is inside the same similarity set as the List of feedbacks and therefore conflicting.
     */
    @Transactional
    public void updateExistingConflicts(List<ModelAssessmentConflict> existingConflicts, Map<String, List<Feedback>> newConflictingFeedbacks) {
        existingConflicts.forEach(conflict -> {
            List<Feedback> newFeedbacks = newConflictingFeedbacks.get(conflict.getCausingConflictingResult().getModelElementId());
            if (newFeedbacks != null) {
                conflictingResultService.updateExistingConflictingResults(conflict, newFeedbacks);
            }
            else {
                resolveConflictByTutor(conflict);
            }
        });
    }

    public boolean userIsResponsibleForHandling(ModelAssessmentConflict conflict, Exercise exercise, User user) {
        if (authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return true;
        }
        switch (conflict.getState()) {
        case UNHANDLED:
            return conflict.getCausingConflictingResult().getResult().getAssessor().equals(user);
        case ESCALATED_TO_TUTORS_IN_CONFLICT:
            return conflict.getResultsInConflict().stream().anyMatch(conflictingResult -> conflictingResult.getResult().getAssessor().equals(user));
        default:
            return false;
        }

    }

    private ModelAssessmentConflict createConflict(String causingModelElementId, Result causingResult, List<Feedback> feedbacksInConflict) {
        ModelAssessmentConflict conflict = new ModelAssessmentConflict();
        Set<ConflictingResult> resultsInConflict = new HashSet<>();
        feedbacksInConflict.forEach(feedback -> {
            ConflictingResult conflictingResult = conflictingResultService.createConflictingResult(feedback.getReferenceElementId(), feedback.getResult());
            resultsInConflict.add(conflictingResult);
        });
        ConflictingResult causingConflictingResult = conflictingResultService.createConflictingResult(causingModelElementId, causingResult);
        conflict.setCausingConflictingResult(causingConflictingResult);
        conflict.setResultsInConflict(resultsInConflict);
        conflict.setCreationDate(ZonedDateTime.now());
        conflict.setState(EscalationState.UNHANDLED);
        return conflict;
    }

    /**
     * Updates the given storedConflict with the decision of a tutor to whom the storedConflict got escalated to. When all tutors posted their decision the conflict is either
     * escalated to an instructor or resolved in case all tutors decided the same way
     *
     * @param storedConflict
     * @param updatedConflictingResult
     */
    private void updateEscalatedConflict(ModelAssessmentConflict storedConflict, ConflictingResult updatedConflictingResult) {
        ConflictingResult storedConflictingResult = storedConflict.getResultsInConflict().stream()
                .filter(conflictingResult -> conflictingResult.getId().equals(updatedConflictingResult.getId())).findFirst().get();
        storedConflictingResult.setUpdatedFeedback(updatedConflictingResult.getUpdatedFeedback());
        if (decisionOfAllTutorsPresent(storedConflict)) {
            if (allTutorsAcceptedConflictCausingFeedback(storedConflict)) {
                resolveConflictByTutor(storedConflict);
            }
            else {
                escalateConflict(storedConflict);
            }
        }
        saveConflict(storedConflict);
    }

    /**
     * Sets the conflict attribute of each conflictingResult in the resultsInConflict list before saving the given conflict
     * to make sure conflicting results do not get duplicated in the database when saved.
     */
    private void saveConflict(ModelAssessmentConflict conflict) {
        conflict.getResultsInConflict().forEach(conflictingResult -> conflictingResult.setConflict(conflict));
        modelAssessmentConflictRepository.save(conflict);
    }

    private void applyTutorsDecisionToCompass(ModelAssessmentConflict conflict) {
        conflict.getResultsInConflict()
                .forEach(conflictingResult -> modelingAssessmentService.updateSubmittedManualAssessment(conflictingResult.getResult(), conflictingResult.getUpdatedFeedback()));
    }

    private void applyInstructorDecisionToCompass(ConflictingResult conflictingResult, Feedback decision) {
        List<Feedback> feedbacks = (List<Feedback>) Hibernate.unproxy(conflictingResult.getResult().getFeedbacks());
        conflictingResult.getResult().setFeedbacks(feedbacks);
        Feedback feedbackToUpdate = findFeedbackByReferenceId(conflictingResult.getResult(), conflictingResult.getModelElementId()).get();
        feedbackToUpdate.setCredits(decision.getCredits());
        modelingAssessmentService.updateSubmittedManualAssessment(conflictingResult.getResult(), feedbackToUpdate);
    }

    private boolean allTutorsAcceptedConflictCausingFeedback(ModelAssessmentConflict conflict) {
        ConflictingResult firstConflictingResult = conflict.getResultsInConflict().iterator().next();
        boolean tutorsDecisionUniform = conflict.getResultsInConflict().stream().allMatch(cR -> {
            if (cR.getUpdatedFeedback() != null) {
                return cR.getUpdatedFeedback().getCredits().equals(firstConflictingResult.getUpdatedFeedback().getCredits());
            }
            return false;
        });
        if (tutorsDecisionUniform) {
            Feedback causingFeedback = conflict.getCausingConflictingResult().getResult().getFeedbacks().stream()
                    .filter(feedback -> feedback.getReferenceElementId().equals(conflict.getCausingConflictingResult().getModelElementId())).findFirst().get();
            return causingFeedback.getCredits().equals(firstConflictingResult.getUpdatedFeedback().getCredits());
        }
        else {
            return false;
        }
    }

    private boolean decisionOfAllTutorsPresent(ModelAssessmentConflict conflict) {
        return conflict.getResultsInConflict().stream().allMatch(conflictingResult -> conflictingResult.getUpdatedFeedback() != null);
    }

    private void verifyNotResolved(ModelAssessmentConflict conflict) {
        if (conflict.isResolved()) {
            log.error("Escalating resolved conflict {} is not possible.", conflict.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflict with id" + conflict.getId() + "has already been resolved");
        }
    }

    private Optional<Feedback> findFeedbackByReferenceId(Result result, String referenceId) {
        return result.getFeedbacks().stream().filter(feedback -> feedback.getReferenceElementId().equals(referenceId)).findFirst();
    }

    private void submitCausingConflictingResult(ModelAssessmentConflict conflict) {
        Result result = conflict.getCausingConflictingResult().getResult();
        ModelingSubmission submission = (ModelingSubmission) Hibernate.unproxy(result.getSubmission());
        Participation participation = (Participation) Hibernate.unproxy(submission.getParticipation());
        ModelingExercise exercise = (ModelingExercise) Hibernate.unproxy(participation.getExercise());
        List<ModelAssessmentConflict> conflictsCausedByResult = getConflictsByCausingResult(result);
        if (conflictsCausedByResult.stream().allMatch(c -> c.isResolved())) {
            modelingAssessmentService.submitManualAssessment(result, exercise, submission.getSubmissionDate());
        }
    }
}
