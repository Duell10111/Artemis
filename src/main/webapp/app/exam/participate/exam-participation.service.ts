import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { HttpClient } from '@angular/common/http';
import { LocalStorageService } from 'ngx-webstorage';
import { Submission } from 'app/entities/submission.model';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { tap } from 'rxjs/operators';
import * as moment from 'moment';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService {
    private resourceUrl = SERVER_API_URL + 'api/courses';
    private studentExam: StudentExam;
    private submissionSaveList: Submission[] = [];
    // TODO: add course id and exam id
    private localStorageExamKey = 'artemis_student_exam';

    // autoTimerInterval in seconds
    autoSaveTime = 60;
    autoSaveInterval: number;

    constructor(
        private httpClient: HttpClient,
        private localStorageService: LocalStorageService,
        private modelingSubmissionService: ModelingSubmissionService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private textSubmissionService: TextSubmissionService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
    ) {}

    public getStudentExam(courseId: number, examId: number): Observable<StudentExam> {
        // TODO: check local storage
        const localStoredExam: StudentExam = this.localStorageService.retrieve(this.localStorageExamKey);

        if (localStoredExam) {
            this.studentExam = localStoredExam;
            return Observable.of(this.studentExam);
        } else {
            // download student exam from server on service init
            return this.getStudentExamFromServer(courseId, examId).pipe(
                tap((studentExam: StudentExam) => {
                    this.studentExam = studentExam;
                }),
            );
        }
    }

    /**
     * Retrieves a {@link StudentExam} from server
     */
    private getStudentExamFromServer(courseId: number, examId: number): Observable<StudentExam> {
        // TODO: exchange with real call
        const mockedStudentExam: Partial<StudentExam> = {
            exam: {
                id: 1,
                title: 'Test Exam',
                visibleDate: moment('2020-06-15T18:36:48+02:00'),
                startDate: moment('2020-06-16T16:52:36+02:00'),
                endDate: moment('2020-06-17T15:52:43+02:00'),
                startText: 'Start Text',
                endText: 'End Text',
                confirmationStartText: 'Are you sure you want to start?',
                confirmationEndText: 'Are you sure you want to end?',
                maxPoints: 50,
                randomizeExerciseOrder: false,
                numberOfExercisesInExam: 3,
                registeredUsers: null,
                exerciseGroups: null,
                studentExams: null,
                course: new Course(),
            },
            exercises: [new ModelingExercise(UMLDiagramType.ClassDiagram)],
            id: 0,
            student: undefined,
        };
        return Observable.of(mockedStudentExam as StudentExam);
    }

    /**
     * start AutoSaveTimer
     */
    public startAutoSaveTimer(): void {
        // auto save of submission if there are changes
        this.autoSaveInterval = window.setInterval(() => {
            this.synchronizeSubmissionsWithServer();
        }, 1000 * this.autoSaveTime);
    }

    /**
     * creates submissions for all submissions in SubmissionSaveList
     */
    private synchronizeSubmissionsWithServer() {
        this.submissionSaveList.forEach((submission) => {
            switch (submission.participation.exercise?.type) {
                case ExerciseType.TEXT:
                    return this.textSubmissionService;
                case ExerciseType.FILE_UPLOAD:
                    return this.fileUploadSubmissionService;
                case ExerciseType.MODELING:
                    this.modelingSubmissionService.create(submission as ModelingSubmission, submission.participation.exercise.id).subscribe(
                        (response) => {
                            submission = response.body!;
                            submission.participation.submissions = [submission];
                            this.onSaveSuccess();
                        },
                        () => this.onSaveError(),
                    );
                    break;
                case ExerciseType.PROGRAMMING:
                    return this.programmingSubmissionService;
                case ExerciseType.QUIZ:
                    // TODO find submissionService
                    return null;
            }
        });
    }

    private onSaveSuccess() {
        console.log('saved');
    }

    private onSaveError() {
        console.log('error while saving');
    }

    /**
     * Updates StudentExam on server
     * @param studentExam
     */
    createSubmission(submission: Submission, exerciseId: number) {
        // Add to updates

        // Store locally
        this.localStorageService.store(this.localStorageExamKey, '');
    }

    getLatestSubmissionForParticipation(participationId: number): Observable<Submission | undefined> {
        const latestSubmission: Submission | undefined = this.submissionSaveList.find((submission) => submission.participation.id === participationId);
        if (!latestSubmission) {
            const exercise: Exercise | undefined = this.studentExam.participations.find((participation) => participation.id === participationId)?.exercise;
            switch (exercise?.type) {
                case ExerciseType.TEXT:
                    return this.textSubmissionService.getTextSubmissionForExerciseWithoutAssessment(exercise?.id);
                case ExerciseType.FILE_UPLOAD:
                    return this.fileUploadSubmissionService.getFileUploadSubmissionForExerciseWithoutAssessment(exercise?.id);
                case ExerciseType.MODELING:
                    return this.modelingSubmissionService.getLatestSubmissionForModelingEditor(participationId);
                case ExerciseType.PROGRAMMING:
                    return this.programmingSubmissionService.getProgrammingSubmissionForExerciseWithoutAssessment(exercise?.id);
                // case ExerciseType.QUIZ:
                // TODO find submissionService
                // return null;
            }
        }
        return Observable.of(latestSubmission);
    }
}
