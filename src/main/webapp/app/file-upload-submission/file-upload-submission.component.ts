import { Component, OnInit, ElementRef, ViewChild } from '@angular/core';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Result, ResultService } from 'app/entities/result';
import { ParticipationService } from 'app/entities/participation';
import { FileUploadExerciseService, FileUploadExercise } from 'app/entities/file-upload-exercise';
import * as moment from 'moment';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission';
import { FileUploadSubmissionService } from 'app/entities/file-upload-submission/file-upload-submission.service';
import { ComplaintType } from 'app/entities/complaint';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';

@Component({
    templateUrl: './file-upload-submission.component.html',
    providers: [ParticipationService],
})
export class FileUploadSubmissionComponent implements OnInit {
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;
    submission: FileUploadSubmission | null;
    fileUploadExercise: FileUploadExercise;
    participation: StudentParticipation;
    result: Result;
    isActive: boolean;
    isSaving: boolean;
    answer: string;
    erroredFile: File | null;
    submissionFile: File | null;

    ComplaintType = ComplaintType;
    showComplaintForm = false;
    showRequestMoreFeedbackForm = false;
    // indicates if there is a complaint for the result of the submission
    hasComplaint: boolean;
    // indicates if there is a more feedback request for the result of the submission
    hasRequestMoreFeedback: boolean;
    // the number of complaints that the student is still allowed to submit in the course. this is used for disabling the complain button.
    numberOfAllowedComplaints: number;
    // indicates if the result is older than one week. if it is, the complain button is disabled
    isTimeOfComplaintValid: boolean;
    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;

    private submissionConfirmationText: string;

    constructor(
        private route: ActivatedRoute,
        private fileUploadExerciseService: FileUploadExerciseService,
        private participationService: ParticipationService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private fileUploaderService: FileUploaderService,
        private complaintService: ComplaintService,
        private resultService: ResultService,
        private jhiAlertService: JhiAlertService,
        private artemisMarkdown: ArtemisMarkdown,
        private location: Location,
        translateService: TranslateService,
    ) {
        this.isSaving = false;
        translateService.get('artemisApp.fileUploadSubmission.confirmSubmission').subscribe(text => (this.submissionConfirmationText = text));
    }

    /**
     * Initializes data for file upload editor
     */
    ngOnInit() {
        const participationId = Number(this.route.snapshot.paramMap.get('participationId'));
        if (Number.isNaN(participationId)) {
            return this.jhiAlertService.error('artemisApp.fileUploadExercise.error', null, undefined);
        }
        this.fileUploadSubmissionService.getDataForFileUploadEditor(participationId).subscribe(
            (data: StudentParticipation) => {
                this.participation = data;
                this.fileUploadExercise = this.participation.exercise as FileUploadExercise;
                this.isAfterAssessmentDueDate = !this.fileUploadExercise.assessmentDueDate || moment().isAfter(this.fileUploadExercise.assessmentDueDate);

                if (this.fileUploadExercise.course) {
                    this.complaintService.getNumberOfAllowedComplaintsInCourse(this.fileUploadExercise.course.id).subscribe((allowedComplaints: number) => {
                        this.numberOfAllowedComplaints = allowedComplaints;
                    });
                }

                if (data.submissions && data.submissions.length > 0) {
                    this.submission = data.submissions[0] as FileUploadSubmission;
                }

                this.isActive =
                    this.fileUploadExercise.dueDate === undefined || this.fileUploadExercise.dueDate === null || new Date() <= moment(this.fileUploadExercise.dueDate).toDate();
            },
            (error: HttpErrorResponse) => this.onError(error),
        );
    }

    /**
     * Uploads a submission file and submits File Upload Exercise
     */
    submit() {
        const file = this.submissionFile;

        if (!this.submission || !file) {
            return;
        }

        // this.isUploadingFile = true;
        this.erroredFile = null;
        this.fileUploaderService.uploadFile(file, file['name'], { keepFileName: true }).then(
            result => {
                this.submission!.filePath = result.path;
                this.submitExercise();
            },
            error => {
                console.error('Error during file upload in uploadBackground()', error.message);
                this.erroredFile = file;
                this.fileInput.nativeElement.value = '';
                this.submission!.filePath = null;
                // this.isUploadingAttachment = false;
                this.submissionFile = null;
            },
        );
    }

    /**
     * Submits File Upload Exercise
     */
    submitExercise() {
        const confirmSubmit = window.confirm(this.submissionConfirmationText);

        if (confirmSubmit) {
            this.submission!.submitted = true;
            this.fileUploadSubmissionService.update(this.submission!, this.fileUploadExercise.id).subscribe(
                response => {
                    this.submission = response.body!;
                    this.result = this.submission.result;

                    if (this.isActive) {
                        this.jhiAlertService.success('artemisApp.fileUploadExercise.submitSuccessful');
                    } else {
                        this.jhiAlertService.warning('artemisApp.fileUploadExercise.submitDeadlineMissed');
                    }
                },
                err => {
                    this.jhiAlertService.error('artemisApp.fileUploadExercise.error');
                    this.submission!.submitted = false;
                },
            );
        }
    }

    /**
     * @function setFileSubmissionForExercise
     * @param $event {object} Event object which contains the uploaded file
     */
    setFileSubmissionForExercise($event: any): void {
        if ($event.target.files.length) {
            this.erroredFile = null;
            const fileList: FileList = $event.target.files;
            const submissionFile = fileList[0];
            this.submissionFile = submissionFile;
        }
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, undefined);
    }

    /**
     * Navigates to previous location
     */
    previous() {
        this.location.back();
    }

    /**
     * Hides more feedback form and shows complaint form
     */
    toggleComplaintForm() {
        this.showRequestMoreFeedbackForm = false;
        this.showComplaintForm = !this.showComplaintForm;
    }

    /**
     * Hides complaint form and shows more feedback form
     */
    toggleRequestMoreFeedbackForm() {
        this.showComplaintForm = false;
        this.showRequestMoreFeedbackForm = !this.showRequestMoreFeedbackForm;
    }
}
