import { ModuleWithProviders, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import {
    ParticipationCleanupBuildPlanDialogComponent,
    ParticipationCleanupBuildPlanPopupComponent,
    ParticipationComponent,
    participationPopupRoute,
    ParticipationPopupService,
    participationRoute,
    ParticipationService,
    ParticipationWebsocketService,
} from './';
import { SortByModule, SortByPipe } from 'app/components/pipes';
import { ArtemisExerciseScoresModule } from 'app/scores';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { ArtemisParticipationSubmissionModule } from 'app/entities/participation-submission/participation-submission.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';

const ENTITY_STATES = [...participationRoute, ...participationPopupRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        ArtemisExerciseScoresModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisParticipationSubmissionModule,
        NgxDatatableModule,
    ],

    declarations: [ParticipationComponent, ParticipationCleanupBuildPlanDialogComponent, ParticipationCleanupBuildPlanPopupComponent],
    entryComponents: [ParticipationComponent, ParticipationCleanupBuildPlanDialogComponent, ParticipationCleanupBuildPlanPopupComponent],
    providers: [ParticipationService, ParticipationPopupService, SortByPipe],
})
export class ArtemisParticipationModule {
    static forRoot(): ModuleWithProviders {
        return {
            ngModule: ArtemisParticipationModule,
            providers: [ParticipationWebsocketService],
        };
    }
}
