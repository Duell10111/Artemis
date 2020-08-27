package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentScoreRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class StudentScoreIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    StudentScoreRepository studentScoresRepo;

    @Autowired
    ParticipationRepository participationRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    private StudentScore studentScore;

    private User user;

    private Course course;

    private Exercise exercise;

    private Participation participation;

    private Result result;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(3, 3, 3);

        // course1
        course = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "tumuser", "tutor", "instructor");
        courseRepo.save(course);
        // exercise1
        exercise = ModelFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), course);
        exerciseRepo.save(exercise);

        // score for student1 in exercise1 in course1
        user = userRepo.findAllInGroup("tumuser").get(0);
        participation = database.addParticipationForExercise(exercise, user.getLogin());
        result = ModelFactory.generateResult(true, 75).resultString("Good effort!").participation(participation);
        resultRepo.save(result);

        // score for student2 in exercise1 in course1
        user = userRepo.findAllInGroup("tumuser").get(1);
        participation = database.addParticipationForExercise(exercise, user.getLogin());
        result = ModelFactory.generateResult(true, 80).resultString("Good effort!").participation(participation);
        resultRepo.save(result);

        // course2
        course = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "tumuser", "tutor", "instructor");
        courseRepo.save(course);
        // exercise2
        exercise = ModelFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), course);
        exerciseRepo.save(exercise);

        // score for student1 in exercise2 in course2
        user = userRepo.findAllInGroup("tumuser").get(0);
        participation = database.addParticipationForExercise(exercise, user.getLogin());
        result = ModelFactory.generateResult(true, 75).resultString("Good effort!").participation(participation);
        resultRepo.save(result);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    // change back to student1 USER after releasing feature for students
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void studentScoresForExerciseTest() throws Exception {
        List responseExerciseOne = request.get("/api/student-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
        assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseOne.size()).as("response has length 2").isEqualTo(2);

        course = courseRepo.findAll().get(0);
        // exercise3
        exercise = ModelFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), course);
        exerciseRepo.save(exercise);
        // score for student2 in exercise3 in course1
        participation = database.addParticipationForExercise(exercise, user.getLogin());
        result = ModelFactory.generateResult(true, 75).resultString("Good effort!").participation(participation);
        resultRepo.save(result);

        responseExerciseOne = request.get("/api/student-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
        assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseOne.size()).as("response has length 2").isEqualTo(2);

        List responseExerciseTwo = request.get("/api/student-scores/exercise/" + exercise.getId(), HttpStatus.OK, List.class);
        assertThat(responseExerciseTwo.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseTwo.size()).as("response has length 1").isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void studentScoresForExerciseTestAccessForbidden() throws Exception {
        course = courseRepo.findAll().get(0);
        course.setStudentGroupName("tutor");
        courseRepo.save(course);

        request.get("/api/student-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.FORBIDDEN, List.class);
    }

    // change back to student1 USER after releasing feature for students
    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void studentScoresForCourseTest() throws Exception {
        List responseCourseOne = request.get("/api/student-scores/course/" + courseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
        assertThat(responseCourseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseCourseOne.size()).as("response has length 2").isEqualTo(2);
        List responseCourseTwo = request.get("/api/student-scores/course/" + courseRepo.findAll().get(1).getId(), HttpStatus.OK, List.class);
        assertThat(responseCourseTwo.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseCourseTwo.size()).as("response has length 1").isEqualTo(1);

        course = courseRepo.findAll().get(0);
        // exercise3
        exercise = ModelFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), course);
        exerciseRepo.save(exercise);
        // score for student2 in exercise3 in course1
        participation = database.addParticipationForExercise(exercise, user.getLogin());
        result = ModelFactory.generateResult(true, 75).resultString("Good effort!").participation(participation);
        resultRepo.save(result);

        responseCourseOne = request.get("/api/student-scores/course/" + courseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
        assertThat(responseCourseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseCourseOne.size()).as("response has length 3").isEqualTo(3);
        responseCourseTwo = request.get("/api/student-scores/course/" + courseRepo.findAll().get(1).getId(), HttpStatus.OK, List.class);
        assertThat(responseCourseTwo.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseCourseTwo.size()).as("response has length 1").isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void studentScoresForCourseTestAccessForbidden() throws Exception {
        course = courseRepo.findAll().get(0);
        course.setStudentGroupName("tutor");
        courseRepo.save(course);

        request.get("/api/student-scores/course/" + courseRepo.findAll().get(0).getId(), HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void studentScoreCreationAfterNewResult() throws Exception {
        user = userRepo.findAllInGroup("tumuser").get(2);
        exercise = exerciseRepo.findAll().get(0);

        List responseExerciseOne = request.get("/api/student-scores/exercise/" + exercise.getId(), HttpStatus.OK, List.class);
        assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseOne.size()).as("response has length 2").isEqualTo(2);

        // score for student3 in exercise1 in course1
        participation = database.addParticipationForExercise(exercise, user.getLogin());
        result = ModelFactory.generateResult(true, 75).resultString("Good effort!").participation(participation);
        resultRepo.save(result);

        responseExerciseOne = request.get("/api/student-scores/exercise/" + exercise.getId(), HttpStatus.OK, List.class);

        // TODO: this is not a good assertion, use a better one
        assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseOne.size()).as("response has length 3").isEqualTo(3);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void studentScoreForStudentAndExerciseTest() throws Exception {
        user = userRepo.findAllInGroup("tumuser").get(0);
        exercise = exerciseRepo.findAll().get(0);

        List<StudentScore> list = studentScoresRepo.findAll();
        StudentScore studentScore = list.get(0);

        StudentScore response = request.get("/api/student-scores/exercise/" + exercise.getId() + "/student/" + user.getLogin(), HttpStatus.OK, StudentScore.class);
        assertThat(response.getId()).as("response id is as expected").isEqualTo(studentScore.getId());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void studentScoreUpdateTest() throws Exception {
        user = userRepo.findAllInGroup("tumuser").get(0);
        exercise = exerciseRepo.findAll().get(0);
        result = resultRepo.findAll().get(0);

        StudentScore response = request.get("/api/student-scores/exercise/" + exercise.getId() + "/student/" + user.getLogin(), HttpStatus.OK, StudentScore.class);
        assertThat(response.getScore()).as("response score is old score").isEqualTo(result.getScore());

        result.setScore(100L);
        resultRepo.save(result);

        response = request.get("/api/student-scores/exercise/" + exercise.getId() + "/student/" + user.getLogin(), HttpStatus.OK, StudentScore.class);
        assertThat(response.getScore()).as("response score is new score").isEqualTo(result.getScore());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void studentScoreDeleteByResultTest() throws Exception {
        List responseExerciseOne = request.get("/api/student-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
        assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseOne.size()).as("response has length 2").isEqualTo(2);

        result = resultRepo.findAll().get(0);
        resultRepo.deleteById(result.getId());

        responseExerciseOne = request.get("/api/student-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
        assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseOne.size()).as("response has length 1").isEqualTo(1);
    }
}
