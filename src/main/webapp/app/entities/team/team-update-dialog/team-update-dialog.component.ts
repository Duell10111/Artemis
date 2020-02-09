import { Component, Input, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { Exercise } from 'app/entities/exercise';
import { TeamService } from 'app/entities/team/team.service';
import { Team } from 'app/entities/team/team.model';
import { User } from 'app/core/user/user.model';
import { cloneDeep } from 'lodash';

@Component({
    selector: 'jhi-team-update-dialog',
    templateUrl: './team-update-dialog.component.html',
    styleUrls: ['./team-update-dialog.component.scss'],
})
export class TeamUpdateDialogComponent implements OnInit {
    @Input() team: Team;
    @Input() exercise: Exercise;

    pendingTeam: Team;
    isSaving = false;

    searchingStudents = false;
    searchingStudentsFailed = false;

    studentErrors = {};

    constructor(private participationService: ParticipationService, private teamService: TeamService, private activeModal: NgbActiveModal, private datePipe: DatePipe) {}

    ngOnInit(): void {
        this.pendingTeam = cloneDeep(this.team);
    }

    hasConflictingTeam(student: User) {
        return student.login && Object.keys(this.studentErrors).includes(student.login);
    }

    getConflictingTeam(student: User) {
        if (!student.login) {
            return null;
        }
        return this.studentErrors[student.login];
    }

    onAddStudent(student: User) {
        if (!this.pendingTeam.students) {
            this.pendingTeam.students = [];
        }
        this.pendingTeam.students.push(student);
    }

    onRemoveStudent(student: User) {
        this.pendingTeam.students = this.pendingTeam.students.filter(user => user.id !== student.id);
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.team = cloneDeep(this.pendingTeam);

        if (this.team.id !== undefined) {
            this.subscribeToSaveResponse(this.teamService.update(this.team));
        } else {
            this.subscribeToSaveResponse(this.teamService.create(this.exercise, this.team));
        }
    }

    private subscribeToSaveResponse(team: Observable<HttpResponse<Team>>) {
        this.isSaving = true;
        team.subscribe(
            res => this.onSaveSuccess(res),
            error => this.onSaveError(error),
        );
    }

    onSaveSuccess(team: HttpResponse<Team>) {
        this.activeModal.close(team.body);
        this.isSaving = false;
    }

    onSaveError(httpErrorResponse: HttpErrorResponse) {
        this.isSaving = false;
        console.log('httpErrorResponse', httpErrorResponse);
        const { studentLogin, teamId } = httpErrorResponse.error.params;
        this.studentErrors[studentLogin] = teamId;
    }
}
