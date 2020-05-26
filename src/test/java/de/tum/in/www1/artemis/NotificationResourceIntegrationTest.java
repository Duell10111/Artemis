package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.group.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.single.SingleUserNotification;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class NotificationResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    UserService userService;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    GroupNotificationRepository groupNotificationRepository;

    @Autowired
    SingleUserNotificationRepository singleUserNotificationRepository;

    @Autowired
    RequestUtilService request;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CourseService courseService;

    @Autowired
    SystemNotificationRepository systemNotificationRepository;

    @Autowired
    SystemNotificationService systemNotificationService;

    private Exercise exercise;

    private List<User> users;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(2, 1, 1);
        database.addCourseWithOneTextExercise();
        database.addCourseWithOneTextExercise();
        systemNotificationRepository.deleteAll();
        exercise = exerciseRepo.findAll().get(0);

        User student1 = users.get(0);
        student1.setLastNotificationRead(ZonedDateTime.now().minusDays(1));
        users.set(0, student1);
        userRepository.save(student1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        systemNotificationRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetNotifications_recipientEvaluation() throws Exception {
        User recipient = userService.getUser();
        SingleUserNotification notification1 = ModelFactory.generateSingleUserNotification(ZonedDateTime.now(), recipient);
        notificationRepository.save(notification1);
        SingleUserNotification notification2 = ModelFactory.generateSingleUserNotification(ZonedDateTime.now(), users.get(1));
        notificationRepository.save(notification2);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with recipient equal to current user is returned").contains(notification1);
        assertThat(notifications).as("Notification with recipient not equal to current user is not returned").doesNotContain(notification2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetNotifications_courseEvaluation() throws Exception {
        // student1 is member of `testgroup` and `tumuser` per default
        // the studentGroupName of course1 is `tumuser` per default
        Course course1 = courseRepository.findAll().get(0);
        GroupNotification notification1 = ModelFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.STUDENT);
        notificationRepository.save(notification1);
        Course course2 = courseRepository.findAll().get(1);
        course2.setStudentGroupName("some-group");
        courseService.save(course2);
        GroupNotification notification2 = ModelFactory.generateGroupNotification(ZonedDateTime.now(), course2, GroupNotificationType.STUDENT);
        notificationRepository.save(notification2);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with course the current user belongs to is returned").contains(notification1);
        assertThat(notifications).as("Notification with course the current user does not belong to is not returned").doesNotContain(notification2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetNotifications_groupNotificationTypeEvaluation_asStudent() throws Exception {
        GroupNotification notificationStudent = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.STUDENT);
        notificationRepository.save(notificationStudent);
        GroupNotification notificationTutor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.TA);
        notificationRepository.save(notificationTutor);
        GroupNotification notificationInstructor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.INSTRUCTOR);
        notificationRepository.save(notificationInstructor);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with type student is returned").contains(notificationStudent);
        assertThat(notifications).as("Notification with type tutor is not returned").doesNotContain(notificationTutor);
        assertThat(notifications).as("Notification with type instructor is not returned").doesNotContain(notificationInstructor);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetNotifications_groupNotificationTypeEvaluation_asTutor() throws Exception {
        GroupNotification notificationStudent = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.STUDENT);
        notificationRepository.save(notificationStudent);
        GroupNotification notificationTutor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.TA);
        notificationRepository.save(notificationTutor);
        GroupNotification notificationInstructor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.INSTRUCTOR);
        notificationRepository.save(notificationInstructor);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with type student is not returned").doesNotContain(notificationStudent);
        assertThat(notifications).as("Notification with type tutor is returned").contains(notificationTutor);
        assertThat(notifications).as("Notification with type instructor is not returned").doesNotContain(notificationInstructor);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetNotifications_groupNotificationTypeEvaluation_asInstructor() throws Exception {
        GroupNotification notificationStudent = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.STUDENT);
        notificationRepository.save(notificationStudent);
        GroupNotification notificationTutor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.TA);
        notificationRepository.save(notificationTutor);
        GroupNotification notificationInstructor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.INSTRUCTOR);
        notificationRepository.save(notificationInstructor);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with type student is not returned").doesNotContain(notificationStudent);
        assertThat(notifications).as("Notification with type tutor is not returned").doesNotContain(notificationTutor);
        assertThat(notifications).as("Notification with type instructor is returned").contains(notificationInstructor);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testDeleteNotification_asInstructor() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification("Notification Text", null, exercise.getCourse(), type);
        groupNotification.setTarget(null);
        notificationRepository.save(groupNotification);
        request.delete("/api/notifications/" + groupNotification.getId(), HttpStatus.OK);
    }
}
