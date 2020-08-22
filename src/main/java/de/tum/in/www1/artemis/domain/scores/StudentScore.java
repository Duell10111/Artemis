package de.tum.in.www1.artemis.domain.scores;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "student_scores")
public class StudentScore {

    @Id
    @Column(name = "id")
    private long studentScoreId;

    @Column(name = "student_id")
    private long studentId;

    @Column(name = "exercise_id")
    private long exerciseId;

    // we should think whether participation_id would be helpful here as well (or even instead of result_id)
    @Column(name = "result_id")
    private long resultId;

    @Column(name = "score")
    private long score;

    public long getStudentScoreId() {
        return studentScoreId;
    }

    public void setStudentScoreId(long studentScoreId) {
        this.studentScoreId = studentScoreId;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public long getResultId() {
        return resultId;
    }

    public void setResultId(long resultId) {
        this.resultId = resultId;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }

    public StudentScore() {
        // Empty constructor because of @Entity
    }

    public StudentScore(long studentScoreId, long studentId, long exerciseId, long resultId, long score) {
        this.studentScoreId = studentScoreId;
        this.studentId = studentId;
        this.exerciseId = exerciseId;
        this.resultId = resultId;
        this.score = score;
    }
}
