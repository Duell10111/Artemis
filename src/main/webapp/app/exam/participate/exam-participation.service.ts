import { Injectable, OnDestroy } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable, Subject, forkJoin } from 'rxjs';
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
import { TextSubmission } from 'app/entities/text-submission.model';
import { Participation } from 'app/entities/participation/participation.model';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService implements OnDestroy {
    set examId(value: number) {
        this._examId = value;
    }
    set courseId(value: number) {
        this._courseId = value;
    }

    public studentExam$: Subject<StudentExam> = new Subject<StudentExam>();

    private studentExam: StudentExam;
    private submissionSyncList: Submission[] = [];

    private _courseId: number;
    private _examId: number;

    // autoTimerInterval in seconds
    autoSaveTimer = 0;
    autoSaveInterval: number;

    constructor(
        private httpClient: HttpClient,
        private localStorageService: LocalStorageService,
        private modelingSubmissionService: ModelingSubmissionService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private textSubmissionService: TextSubmissionService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
    ) {
        this.studentExam$.subscribe((studentExam) => {
            this.studentExam = studentExam;
            this.localStorageService.store(this.getLocalStorageKeyForStudentExam(), JSON.stringify(this.studentExam));
        });
    }

    ngOnDestroy(): void {
        window.clearInterval(this.autoSaveTimer);
    }

    private getLocalStorageKeyForStudentExam(): string {
        const prefix = 'artemis_student_exam';
        return `${prefix}_${this._courseId}_${this._examId}`;
    }

    private getResourceUrl(): string {
        return `${SERVER_API_URL}api/courses/${this._courseId}/exams/${this._examId}`;
    }

    private getExamExerciseByParticipationId(participationId: number): Exercise | undefined {
        return this.studentExam.exercises.find((examExercise) => examExercise.studentParticipations.some((studentParticipation) => studentParticipation.id === participationId));
    }

    private getExamExerciseParticiaption(participationId: number): Participation | undefined {
        const exercise: Exercise | undefined = this.getExamExerciseByParticipationId(participationId);
        return exercise ? exercise.studentParticipations.find((studentParticipation) => studentParticipation.id === participationId) : undefined;
    }

    public initStudentExam() {
        // return studentExamObject in memory
        if (this.studentExam) {
            this.studentExam$.next(this.studentExam);
        } else {
            // check for localStorage
            const localStoredExam: StudentExam = JSON.parse(this.localStorageService.retrieve(this.getLocalStorageKeyForStudentExam()));
            if (localStoredExam) {
                this.studentExam$.next(localStoredExam);
            } else {
                // download student exam from server on service init
                this.getStudentExamFromServer().subscribe((studentExam: StudentExam) => {
                    this.studentExam$.next(studentExam);
                });
            }
        }
        this.startAutoSaveTimer();
    }

    /**
     * Retrieves a {@link StudentExam} from server
     */
    private getStudentExamFromServer(): Observable<StudentExam> {
        const url = this.getResourceUrl() + '/studentExams/conduction';
        // this._courseId, this._examId
        return this.httpClient.get<StudentExam>(url);
    }

    /**
     * start AutoSaveTimer
     */
    public startAutoSaveTimer(): void {
        // auto save of submission if there are changes
        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer++;
            if (this.autoSaveTimer >= 60) {
                this.synchronizeSubmissionsWithServer();
            }
        }, 1000);
    }

    /**
     * triggers Synchronization with server
     */
    public synchronizeSubmissionsWithServer() {
        this.autoSaveTimer = 0;
        forkJoin(
            this.submissionSyncList.map((submission) => {
                switch (submission.participation.exercise?.type) {
                    case ExerciseType.TEXT:
                        this.textSubmissionService.update(submission as TextSubmission, submission.participation.exercise?.id);
                        break;
                    case ExerciseType.FILE_UPLOAD:
                        // TODO: works differently than other services
                        return this.fileUploadSubmissionService;
                    case ExerciseType.MODELING:
                        this.modelingSubmissionService.update(submission as ModelingSubmission, submission.participation.exercise.id).subscribe(
                            (response) => {
                                submission = response.body!;
                                submission.participation.submissions = [submission];
                                this.onSaveSuccess();
                            },
                            () => this.onSaveError(),
                        );
                        break;
                    case ExerciseType.PROGRAMMING:
                        // TODO: works differently than other services
                        return this.programmingSubmissionService;
                    case ExerciseType.QUIZ:
                        // TODO find submissionService
                        return null;
                }
            }),
        ).subscribe(() => {
            // clear sync list
            this.submissionSyncList = [];
        });
    }

    private onSaveSuccess() {
        console.log('saved');
    }

    private onSaveError() {
        console.log('error while saving');
    }

    /**
     * Updates StudentExam locally, will get synchronized later to the server
     * @param studentExam
     */
    updateSubmission(submission: Submission, participationId: number) {
        const examExerciseParticipation = this.getExamExerciseParticiaption(participationId);
        if (examExerciseParticipation) {
            examExerciseParticipation.submissions = [submission];
            // update immediately in localStorage and online every 60seconds
            this.studentExam$.next(this.studentExam);
            // filter older submissions of this exercise and push the newest version to be updated
            this.submissionSyncList.filter((examSubmission) => examSubmission.id !== submission.id).push(submission);
        }
    }
}
