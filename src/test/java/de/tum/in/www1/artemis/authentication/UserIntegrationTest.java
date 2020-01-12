package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

public class UserIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private List<User> users;

    private User student;

    @BeforeEach
    public void setUp() {
        users = database.addUsers(1, 1, 1);
        student = users.get(0);
    }

    @AfterEach
    public void teardown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void updateUser_asAdmin_isSuccessful() throws Exception {
        final var newPassword = "bonobo42";
        final var newEmail = "bonobo42@tum.com";
        final var newFirstName = "Bruce";
        final var newGroups = Set.of("foo", "bar");
        final var newLastName = "Wayne";
        final var newImageUrl = "foobar.png";
        final var newLangKey = "DE";
        final var newAuthorities = Set.of(AuthoritiesConstants.TEACHING_ASSISTANT).stream().map(authorityRepository::findById).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toSet());
        student.setAuthorities(newAuthorities);
        student.setEmail(newEmail);
        student.setFirstName(newFirstName);
        student.setGroups(newGroups);
        student.setLastName(newLastName);
        student.setImageUrl(newImageUrl);
        student.setLangKey(newLangKey);
        final var managedUserVM = new ManagedUserVM(student);
        managedUserVM.setPassword(newPassword);

        final var response = request.putWithResponseBody("/api/users", managedUserVM, User.class, HttpStatus.OK);
        final var updatedUserIndDB = userRepository.findOneWithAuthoritiesAndGroupsByLogin(student.getLogin()).get();
        updatedUserIndDB.setPassword(userService.decryptPasswordByLogin(updatedUserIndDB.getLogin()).get());

        assertThat(response).isNotNull();
        response.setPassword(userService.decryptPasswordByLogin(response.getLogin()).get());
        assertThat(student).as("Returned user is equal to sent update").isEqualTo(response);
        assertThat(student).as("Updated user in DB is equal to sent update").isEqualTo(updatedUserIndDB);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateUser_asInstructor_forbidden() throws Exception {
        request.put("/api/users", new ManagedUserVM(student), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void updateUser_asTutor_forbidden() throws Exception {
        request.put("/api/users", new ManagedUserVM(student), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void updateUser_withExternalUserManagement_vcsManagementHasNotBeenCalled() throws Exception {
        request.put("/api/users", new ManagedUserVM(student), HttpStatus.OK);

        verifyNoInteractions(versionControlService);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void createUser_withExternalUserManagement_vcsManagementHasNotBeenCalled() throws Exception {
        final var newUser = new ManagedUserVM(student);
        newUser.setId(null);
        newUser.setLogin("batman");
        newUser.setEmail("foobar@tum.com");

        request.post("/api/users", newUser, HttpStatus.CREATED);

        verifyNoInteractions(versionControlService);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void deleteUser_withExternalUserManagement_vcsManagementHasNotBeenCalled() throws Exception {
        request.delete("/api/users/" + student.getLogin(), HttpStatus.OK);

        verifyNoInteractions(versionControlService);
    }
}
