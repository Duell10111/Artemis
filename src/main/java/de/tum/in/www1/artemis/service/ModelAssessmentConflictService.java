package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.EscalationState;
import de.tum.in.www1.artemis.domain.modeling.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ModelAssessmentConflictService {

    private final Logger log = LoggerFactory.getLogger(ModelAssessmentConflictService.class);

    private final ModelAssessmentConflictRepository modelAssessmentConflictRepository;

    private final ConflictingResultService conflictingResultService;

    private final ConflictingResultRepository conflictingResultRepository;

    @Autowired
    private CompassService compassService;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    private final ResultRepository resultRepository;

    private final SingleUserNotificationService singleUserNotificationService;

    public ModelAssessmentConflictService(ModelAssessmentConflictRepository modelAssessmentConflictRepository, ConflictingResultService conflictingResultService,
            ConflictingResultRepository conflictingResultRepository, UserService userService, AuthorizationCheckService authCheckService, ResultRepository resultRepository,
            SingleUserNotificationService singleUserNotificationService) {
        this.modelAssessmentConflictRepository = modelAssessmentConflictRepository;
        this.conflictingResultService = conflictingResultService;
        this.conflictingResultRepository = conflictingResultRepository;
        // this.compassService = compassService;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
        this.singleUserNotificationService = singleUserNotificationService;
    }

    public ModelAssessmentConflict findOne(Long conflictId) {
        return modelAssessmentConflictRepository.findById(conflictId).orElseThrow(() -> new EntityNotFoundException("Entity with id " + conflictId + "does not exist"));
    }

    public List<ModelAssessmentConflict> getConflictsForExercise(Long exerciseId) {
        return modelAssessmentConflictRepository.findAllConflictsOfExercise(exerciseId);
    }

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

    public List<ModelAssessmentConflict> getConflictsForSubmission(Long submissionId) {
        List<ModelAssessmentConflict> existingConflicts = modelAssessmentConflictRepository.findAllConflictsByCausingSubmission(submissionId);
        loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(existingConflicts);
        return existingConflicts;
    }

    public List<ModelAssessmentConflict> getConflictsForResult(Result result) {
        List<ModelAssessmentConflict> conflicts = modelAssessmentConflictRepository.findAllConflictsByCausingResult(result);
        return conflicts;
    }

    public List<ModelAssessmentConflict> getConflictsForResultInConflict(Result result) {
        List<ModelAssessmentConflict> conflicts = modelAssessmentConflictRepository.findAllConflictsByResultInConflict(result);
        loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(conflicts);
        return conflicts;
    }

    public List<ModelAssessmentConflict> getUnresolvedConflictsForResult(Result result) {
        List<ModelAssessmentConflict> existingConflicts = getConflictsForResult(result);
        return existingConflicts.stream().filter(conflict -> !conflict.isResolved()).collect(Collectors.toList());
    }

    public List<ModelAssessmentConflict> getConflictsForResultWithState(Result result, EscalationState state) {
        List<ModelAssessmentConflict> existingConflicts = getConflictsForResult(result);
        return existingConflicts.stream().filter(conflict -> conflict.getState().equals(state)).collect(Collectors.toList());
    }

    public void updateConflictsOnResultRemoval(Result result) {
        List<ModelAssessmentConflict> existingConflicts = modelAssessmentConflictRepository.findAllConflictsByCausingResult(result);
        modelAssessmentConflictRepository.deleteAll(existingConflicts);
        existingConflicts = modelAssessmentConflictRepository.findAllConflictsByResultInConflict(result);
        existingConflicts.forEach(conflict -> conflict.getResultsInConflict().forEach(conflictingResult -> {
            if (conflictingResult.getResult().getId().equals(result.getId())) {
                conflict.getResultsInConflict().remove(conflictingResult);
                conflictingResultRepository.delete(conflictingResult);
            }
        }));
    }

    public void loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(List<ModelAssessmentConflict> conflicts) {
        conflicts.forEach(conflict -> {
            conflict.getCausingConflictingResult()
                    .setResult(resultRepository.findByIdWithEagerSubmissionAndFeedbacksAndAssessor(conflict.getCausingConflictingResult().getResult().getId()).get());
            conflict.getResultsInConflict().forEach(conflictingResult -> conflictingResult
                    .setResult(resultRepository.findByIdWithEagerSubmissionAndFeedbacksAndAssessor(conflictingResult.getResult().getId()).get()));
        });
    }

    @Transactional
    public Exercise getExerciseOfConflict(Long conflictId) {
        ModelAssessmentConflict conflict = findOne(conflictId);
        return conflict.getCausingConflictingResult().getResult().getParticipation().getExercise();
    }

    public void saveConflicts(List<ModelAssessmentConflict> conflicts) {
        modelAssessmentConflictRepository.saveAll(conflicts);
    }

    @Transactional
    public ModelAssessmentConflict escalateConflict(ModelAssessmentConflict storedConflict) {
        if (storedConflict.isResolved()) {
            log.error("Escalating resolved conflict {} is not possible.", storedConflict.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflict with id" + storedConflict.getId() + "has already been resolved");
        }
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
            // TODO Notify instructors
            storedConflict.setState(EscalationState.ESCALATED_TO_INSTRUCTOR);
            break;
        default:
            log.error("Escalating conflict {} with state {} failed .", storedConflict.getId(), storedConflict.getState());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflict: " + storedConflict.getId() + " can´t be escalated");
        }
        modelAssessmentConflictRepository.save(storedConflict);
        return storedConflict;
    }

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
     * Adds for each modelElementId mapping, that does not have a existing corresponding conflict object, a new ModelAssessmentConflict object to the existingConflicts
     *
     * @param causingResult           Result that caused the conflicts in newConflictingFeedbacks
     * @param existingConflicts       conflicts with causingResult that curently exist in the database
     * @param newConflictingFeedbacks mapping of modelElementIds from submission of causingResult to feedbacks of other results that are in conflict with the assessment of
     *                                causingResult
     */
    @Transactional
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
     * resolves conflicts which no longer have conflicting feedbacks and updates the resultsInConflicts of the conflicts, that still have feedbacks they are in conflict with
     *
     * @param existingConflicts       all conflicts of one causing result that curently exist in the database
     * @param newConflictingFeedbacks mapping of modelElementIds from submission of the causing result to feedbacks of other results that are in conflict with the assessment of the
     *                                causing result
     */
    @Transactional
    public void updateExistingConflicts(List<ModelAssessmentConflict> existingConflicts, Map<String, List<Feedback>> newConflictingFeedbacks) {
        existingConflicts.forEach(conflict -> {
            List<Feedback> newFeedbacks = newConflictingFeedbacks.get(conflict.getCausingConflictingResult().getModelElementId());
            if (newFeedbacks != null) {
                conflictingResultService.updateExistingConflictingResults(conflict, newFeedbacks);
            }
            else {
                resolveConflict(conflict);
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
            ConflictingResult conflictingResult = conflictingResultService.createConflictingResult(conflict, feedback);
            resultsInConflict.add(conflictingResult);
        });
        ConflictingResult causingConflictingResult = conflictingResultService.createConflictingResult(causingModelElementId, causingResult);
        conflict.setCausingConflictingResult(causingConflictingResult);
        conflict.setResultsInConflict(resultsInConflict);
        conflict.setCreationDate(ZonedDateTime.now());
        conflict.setState(EscalationState.UNHANDLED);
        return conflict;
    }

    private void resolveConflict(ModelAssessmentConflict conflict) {
        switch (conflict.getState()) {
        case UNHANDLED:
            conflict.setState(EscalationState.RESOLVED_BY_CAUSER);
            conflict.setResolutionDate(ZonedDateTime.now());
            break;
        case ESCALATED_TO_TUTORS_IN_CONFLICT:
            applyTutorsDecisionToCompass(conflict);
            conflict.setState(EscalationState.RESOLVED_BY_OTHER_TUTORS);
            conflict.setResolutionDate(ZonedDateTime.now());
            break;
        case ESCALATED_TO_INSTRUCTOR:
            conflict.setState(EscalationState.RESOLVED_BY_INSTRUCTOR);
            conflict.setResolutionDate(ZonedDateTime.now());
            break;
        default:
            log.error("Tried to resolve already resolved conflict {}", conflict);
            break;
        }
    }

    private void updateEscalatedConflict(ModelAssessmentConflict storedConflict, ConflictingResult updatedConflictingResult) {
        ConflictingResult storedConflictingResult = storedConflict.getResultsInConflict().stream()
                .filter(conflictingResult -> conflictingResult.getId().equals(updatedConflictingResult.getId())).findFirst().get();
        storedConflictingResult.setUpdatedFeedback(updatedConflictingResult.getUpdatedFeedback());
        if (decisionOfAllTutorsPresent(storedConflict)) {
            if (decisionOfAllTutorsUniform(storedConflict)) {
                resolveConflict(storedConflict);
            }
            else {
                escalateConflict(storedConflict);
            }
        }
        modelAssessmentConflictRepository.save(storedConflict);
    }

    private void applyTutorsDecisionToCompass(ModelAssessmentConflict conflict) {
        conflict.getResultsInConflict().forEach(conflictingResult -> {
            compassService.applyUpdateOnSubmittedAssessment(conflictingResult.getResult(), conflictingResult.getUpdatedFeedback());
        });
    }

    private boolean decisionOfAllTutorsUniform(ModelAssessmentConflict conflict) {
        ConflictingResult firstConflictingResult = conflict.getResultsInConflict().iterator().next();
        return conflict.getResultsInConflict().stream().allMatch(cR -> cR.getUpdatedFeedback().getCredits().equals(firstConflictingResult.getUpdatedFeedback().getCredits()));
    }

    private boolean decisionOfAllTutorsPresent(ModelAssessmentConflict conflict) {
        return conflict.getResultsInConflict().stream().anyMatch(conflictingResult -> conflictingResult.getUpdatedFeedback() != null);
    }
}
