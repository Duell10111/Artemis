package de.tum.in.www1.artemis.service.compass.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import de.tum.in.www1.artemis.service.compass.assessment.Assessment;

public class AssessmentIndex {

    private Map<Integer, Assessment> modelElementAssessmentMapping;

    public AssessmentIndex() {
        modelElementAssessmentMapping = new HashMap<>();
    }

    public Optional<Assessment> getAssessment(int similarityID) {
        Assessment assessment = modelElementAssessmentMapping.get(similarityID);
        if (assessment == null) {
            return Optional.empty();
        }
        return Optional.of(assessment);
    }

    protected void addAssessment(int similarityID, Assessment assessment) {
        modelElementAssessmentMapping.putIfAbsent(similarityID, assessment);
    }

    /**
     * Used for statistic
     */
    public Map<Integer, Assessment> getAssessmentsMap() {
        return this.modelElementAssessmentMapping;
    }
}
