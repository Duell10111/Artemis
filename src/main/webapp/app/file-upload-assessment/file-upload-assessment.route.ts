import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { FileUploadAssessmentComponent } from 'app/file-upload-assessment/file-upload-assessment.component';

export const modelingAssessmentRoutes: Routes = [
    {
        path: 'file-upload-exercise/:exerciseId/submissions/:submissionId/assessment',
        component: FileUploadAssessmentComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
