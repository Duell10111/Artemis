import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiEventManager } from 'ng-jhipster';
import { AlertService } from 'app/core/alert/alert.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from './services/programming-exercise.service';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { TranslateService } from '@ngx-translate/core';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseImportComponent } from 'app/exercises/programming/manage/programming-exercise-import.component';
import { isOrion, OrionState } from 'app/shared/orion/orion';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CourseExerciseService, CourseManagementService } from 'app/course/manage/course-management.service';
import { ProgrammingExerciseSimulationUtils } from 'app/exercises/programming/shared/utils/programming-exercise-simulation-utils';
import { SortService } from 'app/shared/service/sort.service';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { tap } from 'rxjs/operators';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { ProgrammingExerciseGradingService } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';

@Component({
    selector: 'jhi-programming-exercise',
    templateUrl: './programming-exercise.component.html',
})
export class ProgrammingExerciseComponent extends ExerciseComponent implements OnInit, OnDestroy {
    @Input() programmingExercises: ProgrammingExercise[];
    readonly ActionType = ActionType;
    readonly isOrion = isOrion;
    FeatureToggle = FeatureToggle;
    orionState: OrionState;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private courseExerciseService: CourseExerciseService,
        private exerciseService: ExerciseService,
        private accountService: AccountService,
        private jhiAlertService: AlertService,
        private modalService: NgbModal,
        private router: Router,
        private javaBridge: OrionConnectorService,
        private programmingExerciseSimulationUtils: ProgrammingExerciseSimulationUtils,
        private sortService: SortService,
        private gradingService: ProgrammingExerciseGradingService,
        courseService: CourseManagementService,
        translateService: TranslateService,
        eventManager: JhiEventManager,
        route: ActivatedRoute,
    ) {
        super(courseService, translateService, route, eventManager);
        this.programmingExercises = [];
    }

    ngOnInit(): void {
        super.ngOnInit();
        this.javaBridge.state().subscribe((state) => (this.orionState = state));
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllProgrammingExercisesForCourse(this.courseId).subscribe(
            (res: HttpResponse<ProgrammingExercise[]>) => {
                this.programmingExercises = res.body!;
                // reconnect exercise with course
                this.programmingExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(getCourseFromExercise(exercise));
                    exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(getCourseFromExercise(exercise));
                });
                this.emitExerciseCount(this.programmingExercises.length);
            },
            (res: HttpErrorResponse) => onError(this.jhiAlertService, res),
        );
    }

    trackId(index: number, item: ProgrammingExercise) {
        return item.id;
    }

    protected getChangeEventName(): string {
        return 'programmingExerciseListModification';
    }

    sortRows() {
        this.sortService.sortByProperty(this.programmingExercises, this.predicate, this.reverse);
    }

    openImportModal() {
        const modalRef = this.modalService.open(ProgrammingExerciseImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.result.then(
            (result: ProgrammingExercise) => {
                this.router.navigate(['course-management', this.courseId, 'programming-exercises', 'import', result.id]);
            },
            () => {},
        );
    }

    editInIDE(programmingExercise: ProgrammingExercise) {
        this.javaBridge.editExercise(programmingExercise);
    }

    openOrionEditor(exercise: ProgrammingExercise) {
        try {
            this.router.navigate(['code-editor', 'ide', exercise.id, 'admin', exercise.templateParticipation!.id!]);
        } catch (e) {
            this.javaBridge.log(e);
        }
    }

    getHiddenTestCasesNumber(programmingExerciseId: number): number {
        let number = 0;
        this.gradingService
            .subscribeForTestCases(programmingExerciseId)
            .pipe(
                tap((testCases: ProgrammingExerciseTestCase[]) => {
                    testCases.forEach((testCase) => {
                        if (testCase.afterDueDate) {
                            number++;
                        }
                    });
                }),
            )
            .subscribe();
        return number;
    }

    getPublicTestCasesNumber(programmingExerciseId: number): number {
        let number = 0;
        this.gradingService
            .subscribeForTestCases(programmingExerciseId)
            .pipe(
                tap((testCases: ProgrammingExerciseTestCase[]) => {
                    testCases.forEach((testCase) => {
                        if (!testCase.afterDueDate) {
                            number++;
                        }
                    });
                }),
            )
            .subscribe();
        return number;
    }

    // ################## ONLY FOR LOCAL TESTING PURPOSE -- START ##################

    /**
     * Checks if the url includes the string "nolocalsetup', which is an indication
     * that the particular programming exercise has no local setup
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @param urlToCheck the url which will be check if it contains the substring
     */
    noVersionControlAndContinuousIntegrationAvailableCheck(urlToCheck: string): boolean {
        return this.programmingExerciseSimulationUtils.noVersionControlAndContinuousIntegrationAvailableCheck(urlToCheck);
    }

    // ################## ONLY FOR LOCAL TESTING PURPOSE -- END ##################
}
