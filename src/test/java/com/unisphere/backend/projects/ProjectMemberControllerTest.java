package com.unisphere.backend.projects;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectMemberControllerTest extends AbstractProjectIntegrationTest {

    @Test
    void owner_cannotLeaveTheirOwnProject() throws Exception {
        String ownerToken = registerStudentAndGetToken("owner.cantleave@test.com", "PM0001");
        Long projectId = createProject(ownerToken, "Owner Leave Project");

        mockMvc.perform(delete(BASE + "/{projectId}/members/me", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contributor_canLeave_andRoleReopens() throws Exception {
        String ownerToken = registerStudentAndGetToken("owner.leave@test.com", "PM0002");
        Long projectId = createProject(ownerToken, "Contributor Leave Project");
        Long roleId = addRole(ownerToken, projectId, "Backend Developer", 1);
        setRecruiting(ownerToken, projectId, true);
        String contributorToken = registerAlumniAndGetToken("contributor.leave@test.com");
        Long applicationId = apply(contributorToken, projectId, roleId, "Interested!");

        mockMvc.perform(patch(BASE + "/applications/{applicationId}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/{projectId}/roles", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.data[0].status").value("CLOSED"));

        mockMvc.perform(delete(BASE + "/{projectId}/members/me", projectId)
                        .header("Authorization", "Bearer " + contributorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/{projectId}/roles", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data[0].filledCount").value(0));
    }

    @Test
    void nonOwner_cannotRemoveAnotherMember() throws Exception {
        String ownerToken = registerStudentAndGetToken("owner.removeguard@test.com", "PM0003");
        Long projectId = createProject(ownerToken, "Remove Guard Project");
        Long roleId = addRole(ownerToken, projectId, "Backend Developer", 1);
        setRecruiting(ownerToken, projectId, true);
        String contributorToken = registerAlumniAndGetToken("contributor.removeguard@test.com");
        Long contributorId = getUserId(contributorToken);
        Long applicationId = apply(contributorToken, projectId, roleId, "Interested!");
        mockMvc.perform(patch(BASE + "/applications/{applicationId}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk());

        String outsiderToken = registerStudentAndGetToken("outsider.removeguard@test.com", "PM0004");
        mockMvc.perform(delete(BASE + "/{projectId}/members/{userId}", projectId, contributorId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(BASE + "/{projectId}/members/{userId}", projectId, contributorId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }
}
