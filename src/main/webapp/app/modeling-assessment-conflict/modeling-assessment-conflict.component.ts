import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { UMLModel } from '@ls1intum/apollon';
import { ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import * as $ from 'jquery';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { Feedback } from 'app/entities/feedback';
import { ConflictResolutionState } from 'app/modeling-assessment-editor/conflict-resolution-state.enum';

@Component({
    selector: 'jhi-modeling-assessment-conflict',
    templateUrl: './modeling-assessment-conflict.component.html',
    styleUrls: ['./modeling-assessment-conflict.component.scss', '../modeling-assessment-editor/modeling-assessment-editor.component.scss'],
})
export class ModelingAssessmentConflictComponent implements OnInit, AfterViewInit, OnChanges {
    leftHighlightedElements: Map<string, string>;
    rightHighlightedElements: Map<string, string>;

    private rightFeedbacksCopy: Feedback[];
    private highlightColor: string;
    private userInteractionWithConflict = false;

    @Input() modelingExercise: ModelingExercise;
    @Input() leftTitle: string;
    @Input() leftModel: UMLModel;
    @Input() leftFeedbacks: Feedback[];
    @Input() leftHighlightedElementIds: Set<string> = new Set<string>();
    @Input() leftCenteredElementId: string;
    @Input() leftConflictingElemenId: string;
    @Input() rightTitle: string;
    @Input() rightModel: UMLModel;
    @Input() rightFeedback: Feedback[];
    @Input() rightHighlightedElementIds: Set<string> = new Set<string>();
    @Input() rightCenteredElementId: string;
    @Input() rightConflictingElemenId: string;
    @Input() rightAssessmentReadOnly = false;
    @Input() conflictState: ConflictResolutionState = ConflictResolutionState.UNHANDLED;
    @Input() resultsInConflict: ConflictingResult[];
    @Output() leftButtonPressed = new EventEmitter();
    @Output() rightButtonPressed = new EventEmitter();
    @Output() rightFeedbacksChanged = new EventEmitter<Feedback[]>();

    constructor() {}

    ngOnInit() {
        this.updateHighlightColor();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.conflictState) {
            if (changes.conflictState.currentValue === ConflictResolutionState.UNHANDLED) {
                this.userInteractionWithConflict = false;
            }
            this.updateHighlightColor();
        }
        if (changes.rightFeedback) {
            this.rightFeedbacksCopy = JSON.parse(JSON.stringify(changes.rightFeedback.currentValue));
            // if (this.userInteractionWithConflict) {
            //     this.updateCurrentState();
            // }
        }
        if (changes.leftHighlightedElementIds) {
            if (changes.leftHighlightedElementIds.currentValue) {
                this.leftHighlightedElements = this.createHighlightedElementMapping(changes.leftHighlightedElementIds.currentValue);
            } else {
                this.leftHighlightedElements = new Map<string, string>();
            }
        }
        if (changes.rightHighlightedElementIds) {
            if (changes.rightHighlightedElementIds.currentValue) {
                this.rightHighlightedElements = this.createHighlightedElementMapping(changes.rightHighlightedElementIds.currentValue);
            } else {
                this.rightHighlightedElements = new Map<string, string>();
            }
        }
    }

    ngAfterViewInit() {
        this.setSameWidthOnModelingAssessments();
    }

    onLeftButtonPressed() {
        this.userInteractionWithConflict = true;
        this.leftButtonPressed.emit();
    }

    onRightButtonPressed() {
        this.userInteractionWithConflict = true;
        this.rightButtonPressed.emit();
    }

    onFeedbackChanged(feedbacks: Feedback[]) {
        // const elementAssessmentUpdate = feedbacks.find(feedback => feedback.referenceId === this.rightConflictingElemenId);
        // const originalElementAssessment = this.rightFeedback.find(feedback => feedback.referenceId === this.rightConflictingElemenId);
        // if (elementAssessmentUpdate && originalElementAssessment && elementAssessmentUpdate.credits !== originalElementAssessment.credits) {
        //     this.userInteractionWithConflict = true;
        //     this.updateCurrentState();
        // }
        this.rightFeedbacksChanged.emit(feedbacks);
    }

    setSameWidthOnModelingAssessments() {
        const conflictEditorWidth = $('#conflictEditor').width();
        const instructionsWidth = $('#assessmentInstructions').width();
        if (conflictEditorWidth && instructionsWidth) {
            $('.resizable').css('width', (conflictEditorWidth - instructionsWidth) / 2 + 15);
        }
    }

    private createHighlightedElementMapping(highlightedElementIds: Set<string>) {
        return new Map<string, string>([...highlightedElementIds].map(id => [id, this.highlightColor]));
    }

    private updateHighlightColor() {
        switch (this.conflictState) {
            case ConflictResolutionState.UNHANDLED:
                this.highlightColor = 'rgba(0, 123, 255, 0.6)';
                break;
            case ConflictResolutionState.ESCALATED:
                this.highlightColor = 'rgba(255, 193, 7, 0.6)';
                break;
            case ConflictResolutionState.RESOLVED:
                this.highlightColor = 'rgba(40, 167, 69, 0.6)';
                break;
        }
        this.leftHighlightedElements = this.createHighlightedElementMapping(this.leftHighlightedElementIds);
        this.rightHighlightedElements = this.createHighlightedElementMapping(this.rightHighlightedElementIds);
    }
}
