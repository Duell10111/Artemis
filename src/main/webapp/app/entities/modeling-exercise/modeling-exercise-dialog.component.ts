import { ApplicationRef, Component, ComponentFactoryResolver, Injector, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ShowdownExtension } from 'showdown';

import { Observable } from 'rxjs/Observable';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ModelingExercise } from './modeling-exercise.model';
import { ModelingExercisePopupService } from './modeling-exercise-popup.service';
import { ModelingExerciseService } from './modeling-exercise.service';
import { Course, CourseService } from '../course';

import { Subscription } from 'rxjs/Subscription';
import { ExerciseCategory, ExerciseService } from 'app/entities/exercise';
import { ExampleSubmissionService } from 'app/entities/example-submission/example-submission.service';
import { ApollonCommand } from 'app/markdown-editor/domainCommands/apollon.command';
import { DomainCommand } from 'app/markdown-editor/domainCommands';
import { ApollonExtension } from 'app/markdown-editor/extensions/apollon.extension';
import { ApollonDiagramService } from 'app/entities/apollon-diagram';
import { KatexCommand } from 'app/markdown-editor/commands';
import { EditorMode } from 'app/markdown-editor';

@Component({
    selector: 'jhi-modeling-exercise-dialog',
    templateUrl: './modeling-exercise-dialog.component.html',
    styleUrls: ['./modeling-exercise-dialog.scss'],
})
export class ModelingExerciseDialogComponent implements OnInit {
    EditorMode = EditorMode;

    modelingExercise: ModelingExercise;
    isSaving: boolean;
    dueDateError: boolean;
    assessmentDueDateError: boolean;
    maxScorePattern = '^[1-9]{1}[0-9]{0,4}$'; // make sure max score is a positive natural integer and not too large
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText: string | null;

    courses: Course[];

    domainCommands: DomainCommand[];
    extensions: ShowdownExtension[] = [];
    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];
    domainCommandsGradingInstructions = [new KatexCommand()];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private modelingExerciseService: ModelingExerciseService,
        private courseService: CourseService,
        private exerciseService: ExerciseService,
        private eventManager: JhiEventManager,
        private exampleSubmissionService: ExampleSubmissionService,
        private apollonCommand: ApollonCommand,
        private componentFactoryResolver: ComponentFactoryResolver,
        private appRef: ApplicationRef,
        private injector: Injector,
        private apollonService: ApollonDiagramService,
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.dueDateError = false;
        this.assessmentDueDateError = false;
        this.notificationText = null;
        this.domainCommandsProblemStatement = [...this.domainCommandsProblemStatement, this.apollonCommand];
        this.extensions = [ApollonExtension(this.componentFactoryResolver, this.appRef, this.injector, this.apollonService)];
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.modelingExercise);
        this.courseService.findAllCategoriesOfCourse(this.modelingExercise.course!.id).subscribe(
            (res: HttpResponse<string[]>) => {
                this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(res.body!);
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.modelingExercise.categories = categories.map(el => JSON.stringify(el));
    }

    validateDate() {
        this.dueDateError = this.modelingExercise.releaseDate && this.modelingExercise.dueDate ? !this.modelingExercise.dueDate.isAfter(this.modelingExercise.releaseDate) : false;

        this.assessmentDueDateError =
            this.modelingExercise.assessmentDueDate && this.modelingExercise.releaseDate
                ? !this.modelingExercise.assessmentDueDate.isAfter(this.modelingExercise.releaseDate)
                : this.modelingExercise.assessmentDueDate && this.modelingExercise.dueDate
                ? !this.modelingExercise.assessmentDueDate.isAfter(this.modelingExercise.dueDate)
                : false;
    }

    save() {
        this.isSaving = true;
        if (this.modelingExercise.id !== undefined) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.subscribeToSaveResponse(this.modelingExerciseService.update(this.modelingExercise, requestOptions));
        } else {
            this.subscribeToSaveResponse(this.modelingExerciseService.create(this.modelingExercise));
        }
    }

    deleteExampleSubmission(id: number, index: number) {
        this.exampleSubmissionService.delete(id).subscribe(
            () => {
                this.modelingExercise.exampleSubmissions.splice(index, 1);
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.error(error.message);
            },
        );
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ModelingExercise>>) {
        result.subscribe((res: HttpResponse<ModelingExercise>) => this.onSaveSuccess(res.body!), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: ModelingExercise) {
        this.eventManager.broadcast({ name: 'modelingExerciseListModification', content: 'OK' });
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-modeling-exercise-popup',
    template: '',
})
export class ModelingExercisePopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    constructor(private route: ActivatedRoute, private modelingExercisePopupService: ModelingExercisePopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            if (params['id']) {
                this.modelingExercisePopupService.open(ModelingExerciseDialogComponent as Component, params['id']);
            } else if (params['courseId']) {
                this.modelingExercisePopupService.open(ModelingExerciseDialogComponent as Component, undefined, params['courseId']);
            } else {
                this.modelingExercisePopupService.open(ModelingExerciseDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
