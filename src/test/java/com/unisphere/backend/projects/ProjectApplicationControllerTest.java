package com.unisphere.backend.projects;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectApplicationControllerTest extends AbstractProjectIntegrationTest {

    @Test
    void apply_whenProjectNotRecruiting_isRejected() throws Exception {
        String ownerToken = registerStudentAndGetToken("owner.norecruit@test.com", "PA0001");
        Long projectId = createProject(ownerToken, "Not Recruiting Project");
        Long roleId = addRole(ownerToken, projectId, "Backend Developer", 1);
        String applicantToken = registerAlumniAndGetToken("applicant.norecruit@test.com");

        mockMvc.perform(post(BASE + "/{projectId}/roles/{roleId}/applications", projectId, roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + applicantToken)
                        .content("{\"message\":\"I'd like to help\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apply_asOwner_isRejected() throws Exception {
        String ownerToken = registerStudentAndGetToken("owner.selfapply@test.com", "PA0002");
        Long projectId = createProject(ownerToken, "Self Apply Project");
        Long roleId = addRole(ownerToken, projectId, "Backend Developer", 1);
        setRecruiting(ownerToken, projectId, true);

        mockMvc.perform(post(BASE + "/{projectId}/roles/{roleId}/applications", projectId, roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"message\":\"Can I join my own project?\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullPipeline_apply_thenAccept_createsMemberAndClosesRoleWhenFull() throws Exception {
        String ownerToken = registerStudentAndGetToken("owner.accept@test.com", "PA0003");
        Long projectId = createProject(ownerToken, "Accept Pipeline Project");
        Long roleId = addRole(ownerToken, projectId, "Backend Developer", 1);
        setRecruiting(ownerToken, projectId, true);
        String applicantToken = registerAlumniAndGetToken("applicant.accept@test.com");

        Long applicationId = apply(applicantToken, projectId, roleId, "I'd love to help build this.");

        var decideResult = mockMvc.perform(patch(BASE + "/applications/{applicationId}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"status\":\"ACCEPTED\",\"reason\":\"Great fit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andReturn();

        // Role should now be CLOSED (1 slot, 1 filled).
        mockMvc.perform(get(BASE + "/{projectId}/roles", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].filledCount").value(1))
                .andExpect(jsonPath("$.data[0].status").value("CLOSED"));

        // The applicant should now appear on the member roster.
        mockMvc.perform(get(BASE + "/{projectId}/members", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    void fullPipeline_apply_thenReject_doesNotCreateMember() throws Exception {
        String ownerToken = registerStudentAndGetToken("owner.reject@test.com", "PA0004");
        Long projectId = createProject(ownerToken, "Reject Pipeline Project");
        Long roleId = addRole(ownerToken, projectId, "Designer", 1);
        setRecruiting(ownerToken, projectId, true);
        String applicantToken = registerAlumniAndGetToken("applicant.reject@test.com");

        Long applicationId = apply(applicantToken, projectId, roleId, "I'd like to help design this.");

        mockMvc.perform(patch(BASE + "/applications/{applicationId}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"status\":\"REJECTED\",\"reason\":\"Not the right fit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get(BASE + "/{projectId}/members", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void withdraw_byApplicant_succeeds_butNotByOwner() throws Exception {
        String ownerToken = registerStudentAndGetToken("owner.withdraw@test.com", "PA0005");
        Long projectId = createProject(ownerToken, "Withdraw Pipeline Project");
        Long roleId = addRole(ownerToken, projectId, "Backend Developer", 1);
        setRecruiting(ownerToken, projectId, true);
        String applicantToken = registerAlumniAndGetToken("applicant.withdraw@test.com");

        Long applicationId = apply(applicantToken, projectId, roleId, "Interested!");

        mockMvc.perform(patch(BASE + "/applications/{applicationId}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(BASE + "/applications/{applicationId}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + applicantToken)
                        .content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }

    @Test
    void duplicateApplication_toSameRole_isRejected() throws Exception {
        String ownerToken = registerStudentAndGetToken("owner.dup@test.com", "PA0006");
        Long projectId = createProject(ownerToken, "Duplicate Apply Project");
        Long roleId = addRole(ownerToken, projectId, "Backend Developer", 2);
        setRecruiting(ownerToken, projectId, true);
        String applicantToken = registerAlumniAndGetToken("applicant.dup@test.com");

        apply(applicantToken, projectId, roleId, "First application");

        mockMvc.perform(post(BASE + "/{projectId}/roles/{roleId}/applications", projectId, roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + applicantToken)
                        .content("{\"message\":\"Second application\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myApplications_listsTheCallersOwnApplications() throws Exception {
        String ownerToken = registerStudentAndGetToken("owner.mine@test.com", "PA0007");
        Long projectId = createProject(ownerToken, "My Applications Project");
        Long roleId = addRole(ownerToken, projectId, "Backend Developer", 1);
        setRecruiting(ownerToken, projectId, true);
        String applicantToken = registerAlumniAndGetToken("applicant.mine@test.com");
        apply(applicantToken, projectId, roleId, "Interested!");

        mockMvc.perform(get(BASE + "/applications/me")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }
}
